package eu.amadeos.apps.pdfviewer.domain;

import eu.amadeos.apps.pdfviewer.domain.DocumentThread.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An open document as the window uses it: pictures of the pages, thumbnails, the text and the links of each
 * page, made on the {@link DocumentThread} when first asked for and kept in memory. Every method but
 * {@link #loadText(int)} is called on the UI thread; a method that has nothing yet returns null and tells the
 * {@link Listener} when it has.
 */
public final class DocumentSession implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentSession.class);
    /** Pages up to this many pixels are rendered as one picture; larger ones in tiles of the window. */
    public static final long WHOLE_PAGE_PIXELS = 12_000_000;
    public static final int TILE = 1024;
    /** How many pixels of page pictures are kept: about 160 MB. */
    static final long CACHE_PIXELS = 40_000_000;
    public static final int THUMBNAIL_WIDTH = 100;
    static final int THUMBNAILS_KEPT = 400;

    /** Told on the UI thread when something asked for is ready. */
    public interface Listener {
        void pageReady(int page);

        void thumbnailReady(int page);

        void textReady(int page);
    }

    private record TileKey(int page, double scale, Rotation rotation, Rectangle region) {
    }

    /** A picture of a whole page at some scale, to draw enlarged or reduced while the right one is made. */
    public record Preview(BufferedImage image, Rotation rotation) {
    }

    private final PdfDocument document;
    private final DocumentThread thread;
    private final Listener listener;
    private final LinkedHashMap<TileKey, BufferedImage> tiles = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<TileKey> pendingTiles = new HashSet<>();
    private final Map<Integer, Preview> previews = new LinkedHashMap<>(64, 0.75f, true);
    private final LinkedHashMap<Integer, BufferedImage> thumbnails = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<Integer> pendingThumbnails = new HashSet<>();
    private final Map<Integer, PageText> texts = new ConcurrentHashMap<>();
    private final Set<Integer> pendingTexts = new HashSet<>();
    private final Map<Integer, List<PageLink>> links = new ConcurrentHashMap<>();
    private Rotation thumbnailRotation = Rotation.NONE;
    private long pixels;
    private volatile boolean closed;

    public DocumentSession(PdfDocument document, DocumentThread thread, Listener listener) {
        this.document = document;
        this.thread = thread;
        this.listener = listener;
    }

    public PdfDocument document() {
        return document;
    }

    public DocumentThread thread() {
        return thread;
    }

    public int pageCount() {
        return document.pageCount();
    }

    /** The page sizes as the document shows them, before the user turns the view. */
    public List<PageSize> sizes() {
        return document.pageSizes();
    }

    /** The page sizes as shown in the window, turned by the user. */
    public List<PageSize> sizes(Rotation rotation) {
        List<PageSize> turned = new ArrayList<>();
        for (PageSize size : document.pageSizes()) turned.add(rotation.apply(size));
        return turned;
    }

    // --8<-- [start:tiles]
    /**
     * The parts to render a page of this size in pixels: the whole page while it is small enough, else a grid of
     * square tiles, of which the window only asks for those it shows.
     */
    public static List<Rectangle> tiles(Dimension page) {
        if ((long) page.width * page.height <= WHOLE_PAGE_PIXELS) {
            return List.of(new Rectangle(0, 0, page.width, page.height));
        }
        List<Rectangle> tiles = new ArrayList<>();
        for (int y = 0; y < page.height; y += TILE) {
            for (int x = 0; x < page.width; x += TILE) {
                tiles.add(new Rectangle(x, y, Math.min(TILE, page.width - x), Math.min(TILE, page.height - y)));
            }
        }
        return tiles;
    }
    // --8<-- [end:tiles]

    /**
     * The picture of a tile (or of the whole page), or null while it is being made.
     *
     * @param region in pixels of the turned page at this scale, one of {@link #tiles(Dimension)}
     */
    public BufferedImage tile(int page, double scale, Rotation rotation, Rectangle region, Priority priority) {
        TileKey key = new TileKey(page, scale, rotation, new Rectangle(region));
        BufferedImage image = tiles.get(key);
        if (image != null || closed) return image;
        if (pendingTiles.add(key)) {
            thread.submit(priority, () -> {
                checkOpen();
                return document.render(page, scale, rotation, key.region());
            }, rendered -> store(key, rendered), failure -> {
                pendingTiles.remove(key);
                LOG.warn("Page {} could not be rendered: {}", page + 1, failure.toString());
            });
        }
        return null;
    }

    private void store(TileKey key, BufferedImage image) {
        pendingTiles.remove(key);
        if (closed) return;
        tiles.put(key, image);
        pixels += (long) image.getWidth() * image.getHeight();
        if (key.region().x == 0 && key.region().y == 0 && tiles(new Dimension(image.getWidth(), image.getHeight()))
                .size() == 1) {
            previews.put(key.page(), new Preview(image, key.rotation()));
        }
        Iterator<Map.Entry<TileKey, BufferedImage>> oldest = tiles.entrySet().iterator();
        while (pixels > CACHE_PIXELS && oldest.hasNext()) {
            Map.Entry<TileKey, BufferedImage> entry = oldest.next();
            if (entry.getKey().equals(key)) continue;
            pixels -= (long) entry.getValue().getWidth() * entry.getValue().getHeight();
            oldest.remove();
        }
        while (previews.size() > 64) previews.remove(previews.keySet().iterator().next());
        listener.pageReady(key.page());
    }

    /** The best picture of the page there is at any scale, or null; drawn while the right one is made. */
    public Preview preview(int page, Rotation rotation) {
        Preview preview = previews.get(page);
        if (preview != null && preview.rotation() == rotation) return preview;
        BufferedImage thumbnail = rotation == thumbnailRotation ? thumbnails.get(page) : null;
        return thumbnail == null ? null : new Preview(thumbnail, rotation);
    }

    /** Drops the waiting page pictures, when the zoom changed and they would come too late. */
    public void cancelRendering() {
        thread.cancel(Priority.VISIBLE);
        thread.cancel(Priority.AHEAD);
        pendingTiles.clear();
    }

    /** A small picture of the page {@value #THUMBNAIL_WIDTH} pixels wide (or high, when turned), or null. */
    public BufferedImage thumbnail(int page, Rotation rotation) {
        if (rotation != thumbnailRotation) {
            thread.cancel(Priority.THUMBNAIL);
            thumbnails.clear();
            pendingThumbnails.clear();
            thumbnailRotation = rotation;
        }
        BufferedImage image = thumbnails.get(page);
        if (image != null || closed) return image;
        if (pendingThumbnails.add(page)) {
            PageSize size = rotation.apply(sizes().get(page));
            double scale = THUMBNAIL_WIDTH / Math.max(size.width(), size.height() * 0.75);
            Rectangle region = new Rectangle(0, 0, (int) Math.round(size.width() * scale),
                    (int) Math.round(size.height() * scale));
            thread.submit(Priority.THUMBNAIL, () -> {
                checkOpen();
                return document.render(page, scale, rotation, region);
            }, rendered -> {
                pendingThumbnails.remove(page);
                if (closed || rotation != thumbnailRotation) return;
                thumbnails.put(page, rendered);
                while (thumbnails.size() > THUMBNAILS_KEPT) thumbnails.remove(thumbnails.keySet().iterator().next());
                listener.thumbnailReady(page);
            }, failure -> pendingThumbnails.remove(page));
        }
        return null;
    }

    /** The text of the page, or null while it is read; also reads the links of the page. */
    public PageText text(int page) {
        PageText text = texts.get(page);
        if (text != null || closed) return text;
        if (pendingTexts.add(page)) {
            thread.submit(Priority.TEXT, () -> {
                checkOpen();
                loadLinks(page);
                return loadText(page);
            }, loaded -> {
                pendingTexts.remove(page);
                if (!closed) listener.textReady(page);
            }, failure -> {
                pendingTexts.remove(page);
                texts.put(page, PageText.EMPTY);
                LOG.warn("Text of page {} could not be read: {}", page + 1, failure.toString());
            });
        }
        return null;
    }

    /** The text that is already read, without asking for more. */
    public PageText textIfLoaded(int page) {
        return texts.get(page);
    }

    // --8<-- [start:load-text]
    /** Reads the text of the page; only on the document thread, e.g. by the search. */
    public PageText loadText(int page) throws IOException {
        PageText text = texts.get(page);
        if (text != null) return text;
        try {
            text = document.text(page);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Text of page {} could not be read: {}", page + 1, e.toString());
            text = PageText.EMPTY;
        }
        texts.put(page, text);
        return text;
    }
    // --8<-- [end:load-text]

    /** The links of the page, read with its text; empty until then. */
    public List<PageLink> links(int page) {
        return links.getOrDefault(page, List.of());
    }

    private void loadLinks(int page) {
        if (links.containsKey(page)) return;
        try {
            links.put(page, List.copyOf(document.links(page)));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Links of page {} could not be read: {}", page + 1, e.toString());
            links.put(page, List.of());
        }
    }

    private void checkOpen() {
        if (closed) throw new CancellationException("document closed");
    }

    public boolean closed() {
        return closed;
    }

    /** Closes the document on its thread after the job that runs now; waiting jobs are dropped. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        tiles.clear();
        previews.clear();
        thumbnails.clear();
        thread.submit(Priority.DOCUMENT, () -> {
            document.close();
            return true;
        }, done -> {
        }, failure -> LOG.debug("Closing {} failed", document.file(), failure));
    }
}
