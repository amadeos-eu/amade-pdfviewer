package eu.amadeos.apps.pdfviewer.domain;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An open PDF file, read with Apache PDFBox: page sizes, pictures of the pages, their text with the place of
 * every character, links, the table of contents and the document information.
 *
 * <p>PDFBox documents must not be used by two threads at once: everything but {@link #pageSizes()} and
 * {@link #properties()} runs on the {@link DocumentThread}.
 */
public final class PdfDocument implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(PdfDocument.class);
    /** Larger files are read from the disk as needed instead of being loaded into memory. */
    static final long IN_MEMORY_LIMIT = 100L * 1024 * 1024;

    private final Path file;
    private final PDDocument document;
    private final PDFRenderer renderer;
    private final List<PageSize> sizes;
    private final DocumentProperties properties;

    private PdfDocument(Path file, PDDocument document, long fileSize) {
        this.file = file;
        this.document = document;
        this.renderer = new PDFRenderer(document);
        List<PageSize> list = new ArrayList<>();
        for (PDPage page : document.getPages()) list.add(shownSize(page));
        this.sizes = List.copyOf(list);
        this.properties = readProperties(document, fileSize);
    }

    // --8<-- [start:open]
    /**
     * Opens a PDF file. A file of normal size is read into memory first, so that it can be rewritten on the
     * disk (by LaTeX, say) while it is shown.
     *
     * @param password null or empty for none
     * @throws PasswordException the file needs a password, or this one is wrong
     * @throws IOException       the file cannot be read or is no PDF
     */
    public static PdfDocument open(Path file, String password) throws IOException, PasswordException {
        long size = Files.size(file);
        String pw = password == null ? "" : password;
        try {
            PDDocument document = size <= IN_MEMORY_LIMIT
                    ? Loader.loadPDF(Files.readAllBytes(file), pw)
                    : Loader.loadPDF(new RandomAccessReadBufferedFile(file), pw);
            if (document.getNumberOfPages() == 0) {
                document.close();
                throw new IOException("The document has no pages");
            }
            return new PdfDocument(file, document, size);
        } catch (InvalidPasswordException e) {
            throw new PasswordException(!pw.isEmpty());
        }
    }
    // --8<-- [end:open]

    public Path file() {
        return file;
    }

    public int pageCount() {
        return sizes.size();
    }

    /** The size of every page as the document shows it, turned by the page's own rotation. */
    public List<PageSize> pageSizes() {
        return sizes;
    }

    public DocumentProperties properties() {
        return properties;
    }

    // --8<-- [start:render]
    /** A picture of the whole page as the document shows it. */
    public BufferedImage render(int page, double pixelsPerPoint) throws IOException {
        PageSize size = sizes.get(page);
        return render(page, pixelsPerPoint, Rotation.NONE, new Rectangle(0, 0,
                (int) Math.round(size.width() * pixelsPerPoint), (int) Math.round(size.height() * pixelsPerPoint)));
    }

    /**
     * A picture of part of the page, turned further by the user: {@code region} is in pixels of the turned page
     * at this scale. Large zooms render only the tiles in the window this way, not pictures of whole pages.
     */
    public BufferedImage render(int page, double pixelsPerPoint, Rotation turn, Rectangle region) throws IOException {
        BufferedImage image = new BufferedImage(Math.max(1, region.width), Math.max(1, region.height),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            // PDFBox clears the page with the background colour first, which is black otherwise.
            g.setBackground(Color.WHITE);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.translate(-region.x, -region.y);
            PageSize size = sizes.get(page);
            double w = size.width() * pixelsPerPoint;
            double h = size.height() * pixelsPerPoint;
            switch (turn) {
                case NONE -> {
                }
                case RIGHT -> {
                    g.translate(h, 0);
                    g.rotate(Math.PI / 2);
                }
                case HALF -> {
                    g.translate(w, h);
                    g.rotate(Math.PI);
                }
                case LEFT -> {
                    g.translate(0, w);
                    g.rotate(-Math.PI / 2);
                }
            }
            // PDFBox's scale 1 is 72 dpi, one pixel per point; it turns the page by its own /Rotate.
            renderer.renderPageToGraphics(page, g, (float) pixelsPerPoint);
        } finally {
            g.dispose();
        }
        return image;
    }
    // --8<-- [end:render]

    // --8<-- [start:text]
    /** The text of the page with the box of every character. */
    public PageText text(int page) throws IOException {
        PageText.Builder builder = new PageText.Builder();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                for (TextPosition p : positions) {
                    String unicode = p.getUnicode();
                    if (unicode == null || unicode.isEmpty()) continue;
                    // The font size gives a box from above the capitals to below the descenders; the glyph height
                    // that PDFBox estimates is often too small to point at.
                    double size = Math.abs(p.getFontSizeInPt());
                    double top;
                    double height;
                    if (size > 0 && size < 500) {
                        top = p.getY() - size * 0.8;
                        height = size * 1.05;
                    } else {
                        top = p.getY() - p.getHeight();
                        height = p.getHeight();
                    }
                    // A ligature like "ﬁ" is one glyph for several characters: share its width.
                    double width = p.getWidth() / unicode.length();
                    for (int i = 0; i < unicode.length(); i++) {
                        char c = unicode.charAt(i);
                        if (Character.isWhitespace(c)) builder.separator(' ');
                        else builder.add(c, p.getX() + i * width, top, width, height);
                    }
                }
            }

            @Override
            protected void writeWordSeparator() {
                builder.separator(' ');
            }

            @Override
            protected void writeLineSeparator() {
                builder.separator('\n');
            }
        };
        stripper.setStartPage(page + 1);
        stripper.setEndPage(page + 1);
        stripper.getText(document);
        return builder.build();
    }
    // --8<-- [end:text]

    // --8<-- [start:links]
    /** The links of the page that lead somewhere this viewer can go. */
    public List<PageLink> links(int page) throws IOException {
        PDPage p = document.getPage(page);
        PDRectangle crop = p.getCropBox();
        PageSize unturned = new PageSize(crop.getWidth(), crop.getHeight());
        Rotation rotation = Rotation.of(p.getRotation());
        List<PageLink> links = new ArrayList<>();
        for (PDAnnotation annotation : p.getAnnotations()) {
            if (!(annotation instanceof PDAnnotationLink link) || link.getRectangle() == null) continue;
            PDRectangle r = link.getRectangle();
            // PDF coordinates start at the bottom left; ours at the top left of the crop box.
            Rectangle2D area = rotation.apply(new Rectangle2D.Double(r.getLowerLeftX() - crop.getLowerLeftX(),
                    crop.getUpperRightY() - r.getUpperRightY(), r.getWidth(), r.getHeight()), unturned);
            PDAction action = link.getAction();
            if (action instanceof PDActionURI uri && uri.getURI() != null && !uri.getURI().isBlank()) {
                links.add(PageLink.to(area, uri.getURI().strip()));
            } else {
                PDDestination destination = action instanceof PDActionGoTo goTo ? goTo.getDestination()
                        : action == null ? link.getDestination() : null;
                target(destination).ifPresent(t -> links.add(PageLink.to(area, t)));
            }
        }
        return links;
    }
    // --8<-- [end:links]

    /** The table of contents; empty when the document has none. */
    public List<OutlineEntry> outline() {
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        return outline == null ? List.of() : children(outline, new HashSet<>(), 0);
    }

    private List<OutlineEntry> children(PDOutlineNode node, Set<PDOutlineItem> seen, int depth) {
        List<OutlineEntry> entries = new ArrayList<>();
        // Broken files can link an entry to itself.
        if (depth > 32) return entries;
        for (PDOutlineItem item : node.children()) {
            if (!seen.add(item)) break;
            Optional<Target> target = Optional.empty();
            try {
                PDDestination destination = item.getDestination();
                if (destination == null && item.getAction() instanceof PDActionGoTo goTo) {
                    destination = goTo.getDestination();
                }
                target = target(destination);
            } catch (IOException e) {
                LOG.debug("Outline entry {} has a broken destination", item.getTitle(), e);
            }
            String title = item.getTitle() == null ? "" : item.getTitle().strip();
            entries.add(new OutlineEntry(title, target, children(item, seen, depth + 1)));
        }
        return entries;
    }

    // --8<-- [start:target]
    private Optional<Target> target(PDDestination destination) throws IOException {
        if (destination instanceof PDNamedDestination named) {
            destination = document.getDocumentCatalog().findNamedDestinationPage(named);
        }
        if (!(destination instanceof PDPageDestination pageDestination)) return Optional.empty();
        int page = pageDestination.retrievePageNumber();
        if (page < 0 || page >= sizes.size()) return Optional.empty();
        int top = switch (pageDestination) {
            case PDPageXYZDestination xyz -> xyz.getTop();
            case PDPageFitWidthDestination fitH -> fitH.getTop();
            default -> -1;
        };
        PDPage p = document.getPage(page);
        if (top < 0 || p.getRotation() % 360 != 0) return Optional.of(Target.page(page));
        double fromTop = p.getCropBox().getUpperRightY() - top;
        return Optional.of(new Target(page, Math.clamp(fromTop, 0, sizes.get(page).height())));
    }
    // --8<-- [end:target]

    // --8<-- [start:pageable]
    /**
     * The pages for printing, each turned and shrunk to fit the paper. The print dialog may ask for the number
     * of pages on the UI thread, so that comes from the page sizes read at opening; PDFBox itself is only asked
     * while printing, on the document thread.
     */
    public Pageable pageable() {
        return new Pageable() {
            private PDFPageable pages;

            private synchronized PDFPageable pages() {
                if (pages == null) pages = new PDFPageable(document);
                return pages;
            }

            @Override
            public int getNumberOfPages() {
                return sizes.size();
            }

            @Override
            public PageFormat getPageFormat(int pageIndex) {
                return pages().getPageFormat(pageIndex);
            }

            @Override
            public Printable getPrintable(int pageIndex) {
                return pages().getPrintable(pageIndex);
            }
        };
    }
    // --8<-- [end:pageable]

    @Override
    public void close() throws IOException {
        document.close();
    }

    private static PageSize shownSize(PDPage page) {
        PDRectangle crop = page.getCropBox();
        PageSize size = new PageSize(Math.max(1, crop.getWidth()), Math.max(1, crop.getHeight()));
        return Rotation.of(page.getRotation()).apply(size);
    }

    private static DocumentProperties readProperties(PDDocument document, long fileSize) {
        PDDocumentInformation info = document.getDocumentInformation();
        return new DocumentProperties(text(info.getTitle()), text(info.getAuthor()), text(info.getSubject()),
                text(info.getKeywords()), text(info.getCreator()), text(info.getProducer()),
                date(info.getCreationDate()), date(info.getModificationDate()), String.valueOf(document.getVersion()),
                document.getNumberOfPages(), document.isEncrypted(), fileSize);
    }

    /** A text of the document information; the placeholders some programs write count as none. */
    private static String text(String s) {
        String t = s == null ? "" : s.strip();
        return t.equalsIgnoreCase("(unspecified)") || t.equalsIgnoreCase("unknown") ? "" : t;
    }

    private static Optional<ZonedDateTime> date(Calendar calendar) {
        return calendar == null ? Optional.empty()
                : Optional.of(calendar.toInstant().atZone(calendar.getTimeZone().toZoneId()));
    }
}
