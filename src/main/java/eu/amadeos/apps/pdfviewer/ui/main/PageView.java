package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.domain.DocumentSession;
import eu.amadeos.apps.pdfviewer.domain.DocumentThread.Priority;
import eu.amadeos.apps.pdfviewer.domain.PageLayout;
import eu.amadeos.apps.pdfviewer.domain.PageLink;
import eu.amadeos.apps.pdfviewer.domain.PageSize;
import eu.amadeos.apps.pdfviewer.domain.PageText;
import eu.amadeos.apps.pdfviewer.domain.Rotation;
import eu.amadeos.apps.pdfviewer.domain.SearchHit;
import eu.amadeos.apps.pdfviewer.domain.ViewMode;
import eu.amadeos.apps.pdfviewer.domain.Zoom;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pages on the grey desk, inside a scroll pane: pictures from the {@link DocumentSession} (a blurred one
 * while the sharp one is made), the search hits, the selection, and the mouse for selecting text and following
 * links. Positions on a page are points of the page as the document shows it; the view turns and scales them.
 */
final class PageView extends JComponent implements Scrollable {
    static final Color DESK = new Color(0x5a5a5a);
    private static final Color HIT = new Color(255, 225, 0, 110);
    private static final Color CURRENT_HIT = new Color(255, 140, 0, 130);
    private static final Color SELECTION = new Color(40, 110, 220, 80);

    /** What the view tells its window. */
    interface Callbacks {
        void pageShown(int page);

        void follow(PageLink link);

        /** The mouse is over a link (its address or page), or over none (null). */
        void hover(String link);

        /** Ctrl and the mouse wheel: zoom in or out, keeping the point under the mouse in place. */
        void wheelZoom(boolean in);
    }

    private record Caret(int page, int index) {
    }

    private final Callbacks callbacks;
    private DocumentSession session;
    private ViewMode mode = ViewMode.CONTINUOUS;
    private Zoom zoom = Zoom.FIT_WIDTH;
    private Rotation rotation = Rotation.NONE;
    private int current;
    private PageLayout layout;
    private SearchState search = SearchState.NONE;
    private Caret anchor;
    private Caret end;
    private PageLink pressedLink;
    private boolean dragged;
    private Point zoomAnchor;
    private String emptyTitle = "";
    private String emptyHint = "";
    private boolean following = true;

    PageView(Callbacks callbacks) {
        this.callbacks = callbacks;
        setOpaque(true);
        setFocusable(true);
        setAutoscrolls(true);
        setName("pages");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                drag(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                released(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                moved(e);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                wheel(e);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void setEmptyText(String title, String hint) {
        emptyTitle = title;
        emptyHint = hint;
    }

    // ---------------------------------------------------------------- document and layout

    void setDocument(DocumentSession newSession) {
        session = newSession;
        anchor = null;
        end = null;
        search = SearchState.NONE;
        layout = null;
        current = 0;
        relayout();
    }

    DocumentSession session() {
        return session;
    }

    void setView(ViewMode newMode, Zoom newZoom, Rotation newRotation, int page) {
        Anchor keep = anchor();
        boolean pageChanged = newMode == ViewMode.SINGLE && page != current;
        mode = newMode;
        zoom = newZoom;
        rotation = newRotation;
        current = page;
        relayout();
        if (keep != null && !pageChanged && keep.page < (session == null ? 0 : session.pageCount())) restore(keep);
    }

    ViewMode mode() {
        return mode;
    }

    Rotation rotation() {
        return rotation;
    }

    PageLayout pageLayout() {
        return layout;
    }

    /** The zoom factor the pages have now; 1 is 100 %. */
    double zoomFactor() {
        return layout == null ? zoom.factor() : layout.scale() / PageLayout.PIXELS_PER_POINT;
    }

    // --8<-- [start:relayout]
    /** Lays the pages out for the size of the window, e.g. after zooming or when the window was resized. */
    void relayout() {
        Dimension viewport = viewportSize();
        if (session == null) {
            layout = null;
            setPreferredSize(viewport);
            setSize(viewport);
        } else {
            List<PageSize> sizes = session.sizes(rotation);
            double scale = PageLayout.scale(zoom, sizes, mode, current, viewport);
            if (layout != null && layout.scale() != scale) session.cancelRendering();
            layout = PageLayout.of(sizes, mode, scale, current, viewport);
            // Sized at once, so that scrolling to a page works before the next validation.
            setPreferredSize(layout.size());
            setSize(layout.size());
        }
        revalidate();
        repaint();
    }
    // --8<-- [end:relayout]

    /** The window changed its size: lay out again, the same place staying at the top. */
    void viewportResized() {
        if (layout == null || !layout.size().equals(getSize()) || layout.size().width != viewportSize().width
                || zoom.fits()) {
            setView(mode, zoom, rotation, current);
        }
    }

    private Dimension viewportSize() {
        if (getParent() instanceof JViewport viewport && viewport.getWidth() > 0) return viewport.getExtentSize();
        return new Dimension(800, 600);
    }

    private JViewport viewport() {
        return getParent() instanceof JViewport viewport ? viewport : null;
    }

    /** A point of a page that should stay where it is in the window while the layout changes. */
    private record Anchor(int page, double x, double y, Point inWindow) {
    }

    private Anchor anchor() {
        JViewport viewport = viewport();
        if (layout == null || viewport == null) return null;
        Point view = viewport.getViewPosition();
        Point inWindow = zoomAnchor != null ? new Point(zoomAnchor.x - view.x, zoomAnchor.y - view.y)
                : new Point(viewport.getWidth() / 2, 0);
        zoomAnchor = null;
        Point at = new Point(view.x + inWindow.x, view.y + inWindow.y);
        int page = layout.pageAt(at.x, at.y);
        if (page < 0) page = layout.currentPage(viewport.getViewRect(), current);
        Rectangle r = layout.page(page);
        if (r == null) return null;
        return new Anchor(page, (at.x - r.x) / (double) r.width, (at.y - r.y) / (double) r.height, inWindow);
    }

    private void restore(Anchor keep) {
        JViewport viewport = viewport();
        Rectangle r = layout.page(keep.page);
        if (viewport == null || r == null) return;
        int x = (int) Math.round(r.x + keep.x * r.width) - keep.inWindow.x;
        int y = (int) Math.round(r.y + keep.y * r.height) - keep.inWindow.y;
        setViewPosition(x, y);
    }

    /** Ctrl+wheel zooms around this point of the view. */
    void setZoomAnchor(Point point) {
        zoomAnchor = point;
    }

    private void setViewPosition(int x, int y) {
        JViewport viewport = viewport();
        if (viewport == null) return;
        Dimension extent = viewport.getExtentSize();
        Dimension size = getSize();
        following = false;
        viewport.setViewPosition(new Point(Math.clamp(x, 0, Math.max(0, size.width - extent.width)),
                Math.clamp(y, 0, Math.max(0, size.height - extent.height))));
        following = true;
    }

    // ---------------------------------------------------------------- scrolling

    /** Called when the viewport moved: tells the window when another page became the current one. */
    void viewportMoved() {
        JViewport viewport = viewport();
        if (layout == null || viewport == null || mode == ViewMode.SINGLE) return;
        int page = layout.currentPage(viewport.getViewRect(), current);
        if (page != current) {
            current = page;
            if (following) callbacks.pageShown(page);
        }
    }

    void setCurrent(int page) {
        current = page;
    }

    /** Scrolls the page's top, or a point down the page, to the top of the window. */
    void scrollTo(int page, double offset, boolean points) {
        JViewport viewport = viewport();
        if (layout == null || viewport == null) return;
        Rectangle r = layout.page(page);
        if (r == null) return;
        int y;
        if (points) {
            PageSize shown = session.sizes().get(page);
            Rectangle2D line = rotation.apply(new Rectangle2D.Double(0, offset, shown.width(), 0), shown);
            y = r.y + (int) Math.round(line.getY() * layout.scale()) - PageLayout.MARGIN;
        } else {
            y = offset <= 0 ? r.y - PageLayout.MARGIN : r.y + (int) Math.round(offset * r.height);
        }
        int x = viewport.getViewPosition().x;
        if (getWidth() > viewport.getWidth()) x = r.x + r.width / 2 - viewport.getWidth() / 2;
        current = page;
        setViewPosition(x, y);
    }

    /** Scrolls an area of a page into the middle of the window, unless it is in the window already. */
    void reveal(int page, Rectangle2D area) {
        JViewport viewport = viewport();
        Rectangle r = toView(page, area);
        if (viewport == null || r == null) return;
        Rectangle visible = viewport.getViewRect();
        if (visible.contains(r)) return;
        setViewPosition(r.x + r.width / 2 - visible.width / 2, r.y + r.height / 2 - visible.height / 2);
    }

    /** The share of the current page above the window's top edge. */
    double pageOffset() {
        JViewport viewport = viewport();
        Rectangle r = layout == null ? null : layout.page(current);
        if (viewport == null || r == null) return 0;
        return Math.clamp((viewport.getViewPosition().y - r.y) / (double) r.height, 0, 1);
    }

    /** True when the window can scroll further down (or up): Page Down then scrolls, else turns the page. */
    boolean canScroll(boolean down) {
        JViewport viewport = viewport();
        if (viewport == null) return false;
        int y = viewport.getViewPosition().y;
        return down ? y + viewport.getHeight() < getHeight() - 1 : y > 0;
    }

    void scrollBy(int dx, int dy) {
        JViewport viewport = viewport();
        if (viewport == null) return;
        Point p = viewport.getViewPosition();
        setViewPosition(p.x + dx, p.y + dy);
        viewportMoved();
    }

    boolean wider() {
        JViewport viewport = viewport();
        return viewport != null && getWidth() > viewport.getWidth();
    }

    // ---------------------------------------------------------------- page geometry

    private Rectangle toView(int page, Rectangle2D area) {
        Rectangle r = layout == null ? null : layout.page(page);
        if (r == null) return null;
        Rectangle2D turned = rotation.apply(area, session.sizes().get(page));
        double s = layout.scale();
        return new Rectangle(r.x + (int) Math.floor(turned.getX() * s), r.y + (int) Math.floor(turned.getY() * s),
                (int) Math.ceil(turned.getWidth() * s) + 1, (int) Math.ceil(turned.getHeight() * s) + 1);
    }

    private Point2D toPage(int page, Point p) {
        Rectangle r = layout.page(page);
        double s = layout.scale();
        Point2D turned = new Point2D.Double((p.x - r.x) / s, (p.y - r.y) / s);
        return rotation.unapply(turned, session.sizes().get(page));
    }

    /** The page under the point, or the nearest one above or below it while selecting. */
    private int nearestPage(Point p) {
        int page = layout.pageAt(p.x, p.y);
        if (page >= 0) return page;
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < layout.pageCount(); i++) {
            Rectangle r = layout.page(i);
            if (r == null) continue;
            int dy = p.y < r.y ? r.y - p.y : Math.max(0, p.y - (r.y + r.height));
            int dx = p.x < r.x ? r.x - p.x : Math.max(0, p.x - (r.x + r.width));
            int distance = dy * 4 + dx;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private PageLink linkAt(int page, Point p) {
        if (page < 0) return null;
        Point2D at = toPage(page, p);
        for (PageLink link : session.links(page)) {
            if (link.area().contains(at)) return link;
        }
        return null;
    }

    // ---------------------------------------------------------------- mouse

    private void pressed(MouseEvent e) {
        requestFocusInWindow();
        if (layout == null || !SwingUtilities.isLeftMouseButton(e)) return;
        int page = layout.pageAt(e.getX(), e.getY());
        pressedLink = linkAt(page, e.getPoint());
        dragged = false;
        if (pressedLink != null) return;
        PageText text = page < 0 ? null : session.text(page);
        if (text == null) {
            anchor = null;
            end = null;
            repaint();
            return;
        }
        int caret = text.caretAt(toPage(page, e.getPoint()));
        if (e.getClickCount() == 2) {
            int[] word = text.wordAt(text.indexAt(toPage(page, e.getPoint())));
            anchor = new Caret(page, word[0]);
            end = new Caret(page, word[1]);
        } else if (e.isShiftDown() && anchor != null) {
            end = new Caret(page, caret);
        } else {
            anchor = new Caret(page, caret);
            end = anchor;
        }
        repaint();
    }

    private void drag(MouseEvent e) {
        if (layout == null || anchor == null || pressedLink != null) return;
        dragged = true;
        scrollRectToVisible(new Rectangle(e.getX(), e.getY(), 1, 1));
        int page = nearestPage(e.getPoint());
        PageText text = page < 0 ? null : session.text(page);
        if (text == null) return;
        end = new Caret(page, Math.max(0, text.caretAt(toPage(page, e.getPoint()))));
        repaint();
    }

    private void released(MouseEvent e) {
        if (pressedLink != null && !dragged && layout != null) {
            PageLink link = linkAt(layout.pageAt(e.getX(), e.getY()), e.getPoint());
            if (link == pressedLink) callbacks.follow(link);
        }
        pressedLink = null;
    }

    private void moved(MouseEvent e) {
        if (layout == null) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        int page = layout.pageAt(e.getX(), e.getY());
        PageLink link = linkAt(page, e.getPoint());
        if (link != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            callbacks.hover(link.uri().orElseGet(() -> "#" + (link.target().get().page() + 1)));
            return;
        }
        callbacks.hover(null);
        PageText text = page < 0 ? null : session.textIfLoaded(page);
        boolean onText = text != null && text.indexAt(toPage(page, e.getPoint())) >= 0;
        setCursor(onText ? Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void wheel(MouseWheelEvent e) {
        if (e.isControlDown()) {
            if (e.getWheelRotation() != 0) {
                zoomAnchor = e.getPoint();
                callbacks.wheelZoom(e.getWheelRotation() < 0);
            }
            return;
        }
        // Without Ctrl the scroll pane scrolls as usual.
        if (getParent() != null && getParent().getParent() != null) {
            getParent().getParent().dispatchEvent(SwingUtilities.convertMouseEvent(this, e, getParent().getParent()));
        }
    }

    // ---------------------------------------------------------------- selection

    void clearSelection() {
        anchor = null;
        end = null;
        repaint();
    }

    void selectAll(int page) {
        if (session == null) return;
        PageText text = session.text(page);
        if (text == null) return;
        anchor = new Caret(page, 0);
        end = new Caret(page, text.length());
        repaint();
    }

    // --8<-- [start:selected-text]
    /** The selected text of all selected pages, a line break between two pages. */
    String selectedText() {
        if (session == null || anchor == null || end == null) return "";
        Caret from = before(anchor, end) ? anchor : end;
        Caret to = from == anchor ? end : anchor;
        StringBuilder text = new StringBuilder();
        for (int page = from.page(); page <= to.page(); page++) {
            PageText pageText = session.textIfLoaded(page);
            if (pageText == null) continue;
            int start = page == from.page() ? from.index() : 0;
            int stop = page == to.page() ? to.index() : pageText.length();
            if (!text.isEmpty()) text.append('\n');
            text.append(pageText.text(start, stop));
        }
        return text.toString().strip();
    }
    // --8<-- [end:selected-text]

    private static boolean before(Caret a, Caret b) {
        return a.page() < b.page() || a.page() == b.page() && a.index() <= b.index();
    }

    private List<Rectangle2D> selectionOn(int page, PageText text) {
        if (anchor == null || end == null) return List.of();
        Caret from = before(anchor, end) ? anchor : end;
        Caret to = from == anchor ? end : anchor;
        if (page < from.page() || page > to.page()) return List.of();
        int start = page == from.page() ? from.index() : 0;
        int stop = page == to.page() ? to.index() : text.length();
        return text.areas(start, stop);
    }

    void setSearch(SearchState state) {
        search = state;
        repaint();
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setColor(DESK);
            Rectangle clip = g.getClipBounds() != null ? g.getClipBounds() : new Rectangle(getSize());
            g.fill(clip);
            if (layout == null || session == null) {
                paintEmpty(g);
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            List<Integer> shown = new ArrayList<>();
            for (int page = 0; page < layout.pageCount(); page++) {
                Rectangle r = layout.page(page);
                if (r == null) continue;
                Rectangle shadow = new Rectangle(r.x + 3, r.y + 3, r.width, r.height);
                if (!clip.intersects(r.union(shadow))) continue;
                shown.add(page);
                g.setColor(new Color(0, 0, 0, 90));
                g.fillRect(shadow.x, shadow.y, shadow.width, shadow.height);
                paintPage(g, page, r, clip);
                paintMarks(g, page);
            }
            renderAhead(shown);
        } finally {
            g.dispose();
        }
    }

    // --8<-- [start:paint-page]
    private void paintPage(Graphics2D g, int page, Rectangle r, Rectangle clip) {
        DocumentSession.Preview preview = null;
        boolean previewLoaded = false;
        for (Rectangle tile : DocumentSession.tiles(r.getSize())) {
            Rectangle onScreen = new Rectangle(r.x + tile.x, r.y + tile.y, tile.width, tile.height);
            if (!clip.intersects(onScreen)) continue;
            BufferedImage image = session.tile(page, layout.scale(), rotation, tile, Priority.VISIBLE);
            if (image != null) {
                g.drawImage(image, onScreen.x, onScreen.y, null);
                continue;
            }
            // Until the sharp picture is there: any picture of the page, stretched, or a white sheet.
            if (!previewLoaded) {
                preview = session.preview(page, rotation);
                previewLoaded = true;
            }
            Graphics2D t = (Graphics2D) g.create();
            t.clip(onScreen);
            t.setColor(Color.WHITE);
            t.fill(onScreen);
            if (preview != null) t.drawImage(preview.image(), r.x, r.y, r.width, r.height, null);
            t.dispose();
        }
        session.text(page);
    }
    // --8<-- [end:paint-page]

    private void paintMarks(Graphics2D g, int page) {
        PageText text = session.textIfLoaded(page);
        if (text == null) return;
        Optional<SearchHit> currentHit = search.current();
        for (SearchHit hit : search.hitsOn(page)) {
            g.setColor(currentHit.isPresent() && currentHit.get().equals(hit) ? CURRENT_HIT : HIT);
            for (Rectangle2D area : text.areas(hit.start(), hit.end())) {
                Rectangle r = toView(page, area);
                if (r != null) g.fill(r);
            }
        }
        g.setColor(SELECTION);
        for (Rectangle2D area : selectionOn(page, text)) {
            Rectangle r = toView(page, area);
            if (r != null) g.fill(r);
        }
    }

    /** The pages just above and below the window, so that scrolling finds them ready. */
    private void renderAhead(List<Integer> shown) {
        if (shown.isEmpty()) return;
        for (int page : new int[]{shown.getFirst() - 1, shown.getLast() + 1, shown.getLast() + 2}) {
            Rectangle r = page < 0 ? null : layout.page(page);
            if (r == null) continue;
            List<Rectangle> tiles = DocumentSession.tiles(r.getSize());
            if (tiles.size() == 1) session.tile(page, layout.scale(), rotation, tiles.getFirst(), Priority.AHEAD);
        }
    }

    private void paintEmpty(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xd8d8d8));
        Font font = getFont() != null ? getFont() : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        Font title = font.deriveFont(Font.BOLD, AmadeUI.scaled(18));
        g.setFont(title);
        FontMetrics fm = g.getFontMetrics();
        int y = getHeight() / 2 - fm.getHeight();
        g.drawString(emptyTitle, (getWidth() - fm.stringWidth(emptyTitle)) / 2, y);
        g.setFont(font);
        fm = g.getFontMetrics();
        y += fm.getHeight() * 2;
        g.drawString(emptyHint, (getWidth() - fm.stringWidth(emptyHint)) / 2, y);
        g.setComposite(AlphaComposite.SrcOver);
    }

    /** Repaints the page if it is in the window. */
    void repaintPage(int page) {
        Rectangle r = layout == null ? null : layout.page(page);
        if (r != null) repaint(r);
    }

    // ---------------------------------------------------------------- Scrollable

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(800, 600);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 48;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(48, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 48);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
