package eu.amadeos.apps.pdfviewer.domain;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Where each page lies in the scrolled view, in pixels: the pages of the view mode, centred, with a margin
 * around them and a gap between them. Pages that the view mode does not show (all but the current one in
 * {@link ViewMode#SINGLE}) have no place.
 */
public final class PageLayout {
    /** Pixels per point at a zoom of 100 %: a 96 dpi screen. */
    public static final double PIXELS_PER_POINT = 96.0 / 72.0;
    public static final int MARGIN = 12;
    public static final int GAP = 12;

    private final Rectangle[] pages;
    private final List<int[]> rows;
    private final Dimension size;
    private final double scale;

    private PageLayout(Rectangle[] pages, List<int[]> rows, Dimension size, double scale) {
        this.pages = pages;
        this.rows = rows;
        this.size = size;
        this.scale = scale;
    }

    // --8<-- [start:scale]
    /**
     * Pixels per point for the zoom: fixed zooms multiply the 96 dpi size; the fits use the row of the current
     * page and the window's visible area.
     */
    public static double scale(Zoom zoom, List<PageSize> sizes, ViewMode mode, int current, Dimension viewport) {
        if (!zoom.fits() || sizes.isEmpty()) return zoom.factor() * PIXELS_PER_POINT;
        int[] row = rowOf(rows(sizes.size(), mode), Math.clamp(current, 0, sizes.size() - 1));
        double width = 0;
        double height = 0;
        for (int page : row) {
            width += sizes.get(page).width();
            height = Math.max(height, sizes.get(page).height());
        }
        // A page alone in two pages (the first, or the last) keeps the place of two, so the zoom does not jump.
        int places = mode == ViewMode.TWO_PAGES ? 2 : row.length;
        if (places > row.length) width *= places;
        double gaps = (places - 1) * GAP;
        // One pixel less per page, because the page sizes are rounded: a fitted row never needs a scroll bar.
        double byWidth = (viewport.width - 2 * MARGIN - gaps - places) / width;
        double byHeight = (viewport.height - 2 * MARGIN - 1) / height;
        double fit = zoom.kind() == Zoom.Kind.FIT_WIDTH ? byWidth : Math.min(byWidth, byHeight);
        return Math.clamp(fit, Zoom.MIN * PIXELS_PER_POINT, Zoom.MAX * PIXELS_PER_POINT);
    }
    // --8<-- [end:scale]

    /**
     * @param sizes    the pages as shown, already turned
     * @param scale    pixels per point
     * @param current  the current page, the one shown in {@link ViewMode#SINGLE}
     * @param viewport the visible area; the pages are centred in its width
     */
    public static PageLayout of(List<PageSize> sizes, ViewMode mode, double scale, int current, Dimension viewport) {
        Rectangle[] pages = new Rectangle[sizes.size()];
        List<int[]> rows = rows(sizes.size(), mode);
        if (mode == ViewMode.SINGLE && !sizes.isEmpty()) {
            rows = List.<int[]>of(new int[]{Math.clamp(current, 0, sizes.size() - 1)});
        }
        int contentWidth = 0;
        for (int[] row : rows) contentWidth = Math.max(contentWidth, rowWidth(row, sizes, scale, mode));
        int width = Math.max(viewport.width, contentWidth + 2 * MARGIN);
        int y = MARGIN;
        for (int[] row : rows) {
            int height = 0;
            for (int page : row) height = Math.max(height, px(sizes.get(page).height(), scale));
            int x = (width - rowWidth(row, sizes, scale, mode)) / 2;
            // In two pages, the first page sits on the right, where page 1 of a book is.
            if (mode == ViewMode.TWO_PAGES && row.length == 1 && row[0] == 0 && sizes.size() > 1) {
                x = (width + GAP) / 2;
            }
            for (int page : row) {
                int w = px(sizes.get(page).width(), scale);
                int h = px(sizes.get(page).height(), scale);
                pages[page] = new Rectangle(x, y + (height - h) / 2, w, h);
                x += w + GAP;
            }
            y += height + GAP;
        }
        int height = Math.max(viewport.height, y - GAP + MARGIN);
        return new PageLayout(pages, rows, new Dimension(width, height), scale);
    }

    private static int rowWidth(int[] row, List<PageSize> sizes, double scale, ViewMode mode) {
        int width = (row.length - 1) * GAP;
        for (int page : row) width += px(sizes.get(page).width(), scale);
        // The lonely first page of two pages still takes the room of two.
        if (mode == ViewMode.TWO_PAGES && row.length == 1 && row[0] == 0 && sizes.size() > 1) width = width * 2 + GAP;
        return width;
    }

    private static int px(double points, double scale) {
        return Math.max(1, (int) Math.round(points * scale));
    }

    /** The first page of the row after the page's row, or the page itself at the end. */
    public static int next(int count, ViewMode mode, int page) {
        if (count == 0) return 0;
        List<int[]> rows = rows(count, mode);
        int[] row = rowOf(rows, Math.clamp(page, 0, Math.max(0, count - 1)));
        int index = rows.indexOf(row);
        return index + 1 < rows.size() ? rows.get(index + 1)[0] : row[0];
    }

    /** The first page of the row before the page's row, or the first page. */
    public static int previous(int count, ViewMode mode, int page) {
        if (count == 0) return 0;
        List<int[]> rows = rows(count, mode);
        int[] row = rowOf(rows, Math.clamp(page, 0, Math.max(0, count - 1)));
        int index = rows.indexOf(row);
        return index > 0 ? rows.get(index - 1)[0] : rows.getFirst()[0];
    }

    static List<int[]> rows(int count, ViewMode mode) {
        List<int[]> rows = new ArrayList<>();
        if (mode == ViewMode.TWO_PAGES) {
            if (count > 0) rows.add(new int[]{0});
            for (int i = 1; i < count; i += 2) rows.add(i + 1 < count ? new int[]{i, i + 1} : new int[]{i});
        } else {
            for (int i = 0; i < count; i++) rows.add(new int[]{i});
        }
        return rows;
    }

    private static int[] rowOf(List<int[]> rows, int page) {
        for (int[] row : rows) {
            for (int p : row) {
                if (p == page) return row;
            }
        }
        return rows.getFirst();
    }

    public Dimension size() {
        return new Dimension(size);
    }

    /** Pixels per point. */
    public double scale() {
        return scale;
    }

    public int pageCount() {
        return pages.length;
    }

    /** Where the page lies, or null when this view does not show it. */
    public Rectangle page(int index) {
        return index >= 0 && index < pages.length && pages[index] != null ? new Rectangle(pages[index]) : null;
    }

    /** The page at the point, or -1 between the pages. */
    public int pageAt(int x, int y) {
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null && pages[i].contains(x, y)) return i;
        }
        return -1;
    }

    // --8<-- [start:current]
    /**
     * The page the reader is at when the view shows {@code visible}: the first page that fills at least half
     * of the visible height, else the one that takes most of it.
     */
    public int currentPage(Rectangle visible, int fallback) {
        int best = -1;
        int bestHeight = 0;
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] == null) continue;
            Rectangle shown = pages[i].intersection(visible);
            if (shown.isEmpty()) continue;
            if (shown.height * 2 >= visible.height) return firstOfRow(i);
            if (shown.height > bestHeight) {
                best = i;
                bestHeight = shown.height;
            }
        }
        return best < 0 ? fallback : firstOfRow(best);
    }
    // --8<-- [end:current]

    private int firstOfRow(int page) {
        return rowOf(rows, page)[0];
    }

    /** The pages of the row that holds the page; in two pages these are one or two pages. */
    public int[] row(int page) {
        return rowOf(rows, page).clone();
    }

    /** The first page of the next row, or -1 after the last row. */
    public int nextRow(int page) {
        for (int i = 0; i < rows.size() - 1; i++) {
            for (int p : rows.get(i)) {
                if (p == page) return rows.get(i + 1)[0];
            }
        }
        return -1;
    }
}
