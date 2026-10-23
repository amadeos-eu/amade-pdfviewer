package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.OnEdt;
import eu.amadeos.apps.pdfviewer.TestPdfs;
import eu.amadeos.apps.pdfviewer.domain.DocumentThread;
import eu.amadeos.apps.pdfviewer.domain.PageLayout;
import eu.amadeos.apps.pdfviewer.domain.PageText;
import eu.amadeos.apps.pdfviewer.domain.ViewMode;
import eu.amadeos.apps.pdfviewer.repository.PdfViewerPreferences;
import eu.amadeos.apps.pdfviewer.repository.ReadingPositions;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.swing.EdtExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window with a real document: toolbar, sidebar, mouse and keys. Needs a display. */
class MainWindowTest {
    @TempDir
    Path dir;

    private DocumentThread thread;
    private MainWindow window;
    private MainPresenter presenter;

    @BeforeEach
    void open() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        thread = new DocumentThread(new EdtExecutor());
        PdfViewerPreferences preferences = new PdfViewerPreferences(dir.resolve("p.properties"));
        ReadingPositions positions = new ReadingPositions(dir.resolve("positions.properties"), () -> 1);
        OnEdt.run(() -> {
            window = new MainWindow(MainPresenterTest.MSG, null);
            presenter = new MainPresenter(window, MainPresenterTest.MSG, null, preferences, positions, thread,
                    new DesktopLauncher(), false, () -> { });
            window.bind(presenter);
            window.setSize(1000, 800);
            window.setVisible(true);
            presenter.start(List.of());
        });
    }

    @AfterEach
    void close() throws Exception {
        if (window != null) OnEdt.run(() -> window.close());
        if (thread != null) thread.close();
    }

    private void settle() throws Exception {
        for (int i = 0; i < 8; i++) {
            thread.await();
            OnEdt.run(() -> { });
        }
    }

    private void load() throws Exception {
        OnEdt.run(() -> presenter.open(TestPdfs.sample(dir)));
        settle();
        OnEdt.run(() -> {
            for (int page = 0; page < 3; page++) presenter.session().text(page);
        });
        settle();
    }

    /** The point in the page view of a point on page 1, in points from its top left. */
    private Point onPage(int page, double x, double y) {
        PageLayout layout = window.pageView().pageLayout();
        Rectangle r = layout.page(page);
        return new Point(r.x + (int) Math.round(x * layout.scale()), r.y + (int) Math.round(y * layout.scale()));
    }

    private void mouse(int id, Point p) {
        PageView view = window.pageView();
        int buttons = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        view.dispatchEvent(new MouseEvent(view, id, System.currentTimeMillis(), buttons, p.x, p.y, 1, false,
                MouseEvent.BUTTON1));
    }

    private void key(int code) {
        PageView view = window.pageView();
        view.dispatchEvent(new KeyEvent(view, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code,
                KeyEvent.CHAR_UNDEFINED));
    }

    @Test
    void emptyWindowOffersOnlyOpening() throws Exception {
        OnEdt.run(() -> {
            assertFalse(window.toolButton("tool.print").isEnabled());
            assertTrue(window.toolButton("tool.open").isEnabled());
            assertFalse(window.searchField().isEnabled());
            assertEquals("PDF Viewer", window.getTitle());
        });
    }

    @Test
    void showsTheDocument() throws Exception {
        load();
        OnEdt.run(() -> {
            assertEquals(TestPdfs.TITLE + " - PDF Viewer", window.getTitle());
            assertEquals("1", window.pageField().getText());
            assertEquals(" of 3 ", window.pageCountLabel().getText());
            assertTrue(window.statusLabel().getText().startsWith("Page 1 of 3     A4, 210 × 297 mm"),
                    window.statusLabel().getText());
            assertTrue(window.toolButton("tool.print").isEnabled());
            assertEquals("Fit Width", window.zoomBox().getSelectedItem());
            assertEquals(3, window.sidebar().thumbnailList().getModel().getSize());
            assertEquals(3, window.sidebar().contentsTree().getRowCount());
        });
    }

    @Test
    void typedPageNumberAndThumbnailsMove() throws Exception {
        load();
        OnEdt.run(() -> {
            window.pageField().setText("3");
            window.pageField().postActionEvent();
            assertEquals(2, presenter.page());
            int top = window.pageView().pageLayout().page(2).y - PageLayout.MARGIN;
            assertEquals(top, window.scrollPane().getViewport().getViewPosition().y);
            assertEquals(2, window.sidebar().thumbnailList().getSelectedIndex());
            window.sidebar().thumbnailList().setSelectedIndex(1);
            assertEquals(1, presenter.page());
            assertEquals("2", window.pageField().getText());
        });
    }

    @Test
    void selectingWithTheMouseAndCopying() throws Exception {
        load();
        OnEdt.run(() -> {
            PageText text = presenter.session().textIfLoaded(0);
            int quick = text.text().indexOf("quick");
            int fox = text.text().indexOf("fox") + 2;
            Rectangle2D from = text.box(quick);
            Rectangle2D to = text.box(fox);
            mouse(MouseEvent.MOUSE_PRESSED, onPage(0, from.getX() + 1, from.getCenterY()));
            mouse(MouseEvent.MOUSE_DRAGGED, onPage(0, to.getMaxX() - 1, to.getCenterY()));
            mouse(MouseEvent.MOUSE_RELEASED, onPage(0, to.getMaxX() - 1, to.getCenterY()));
            assertEquals("quick brown fox", window.selectedText());
            presenter.selectAll();
            assertTrue(window.selectedText().startsWith("Chapter One\nThe quick"));
        });
    }

    @Test
    void clickingALinkJumpsAndBackReturns() throws Exception {
        load();
        OnEdt.run(() -> {
            // The link "Go to page three" lies at x 70..170, 231..247 points from the top.
            Point link = onPage(0, 100, 239);
            mouse(MouseEvent.MOUSE_PRESSED, link);
            mouse(MouseEvent.MOUSE_RELEASED, link);
            assertEquals(2, presenter.page());
            presenter.back();
            assertEquals(0, presenter.page());
        });
    }

    @Test
    void searchFieldCountsHits() throws Exception {
        load();
        OnEdt.run(() -> {
            window.searchField().setText("needle");
            window.searchField().postActionEvent();
        });
        settle();
        OnEdt.run(() -> {
            assertEquals("1 of 3", window.searchLabel().getText());
            assertEquals(1, presenter.page());
            window.searchField().setText("nothing like this");
            window.searchField().postActionEvent();
        });
        settle();
        OnEdt.run(() -> assertEquals("Not found", window.searchLabel().getText()));
    }

    @Test
    void keysScrollAndTurnPages() throws Exception {
        load();
        OnEdt.run(() -> {
            int y = window.scrollPane().getViewport().getViewPosition().y;
            key(KeyEvent.VK_PAGE_DOWN);
            assertTrue(window.scrollPane().getViewport().getViewPosition().y > y);
            key(KeyEvent.VK_END);
            assertEquals(2, presenter.page());
            key(KeyEvent.VK_HOME);
            assertEquals(0, presenter.page());
            presenter.setViewMode(ViewMode.SINGLE);
            key(KeyEvent.VK_RIGHT);
            assertEquals(1, presenter.page());
            assertEquals(null, window.pageView().pageLayout().page(0));
        });
    }

    @Test
    void zoomBoxAndMenus() throws Exception {
        load();
        OnEdt.run(() -> {
            window.zoomBox().setSelectedItem("200 %");
            assertEquals(2, window.pageView().zoomFactor(), 0.001);
            assertTrue(window.statusLabel().getText().endsWith("200 %"));
            window.menu().modeItem(ViewMode.TWO_PAGES).doClick(0);
            assertEquals(ViewMode.TWO_PAGES, presenter.settings().view());
            window.menu().sidebarItem().doClick(0);
            assertFalse(window.sidebar().isVisible());
            assertEquals(1, window.menu().recentMenu().getItemCount() - 2);
        });
    }
}
