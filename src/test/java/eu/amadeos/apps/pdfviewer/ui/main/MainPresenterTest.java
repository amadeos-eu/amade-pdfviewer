package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.OnEdt;
import eu.amadeos.apps.pdfviewer.TestPdfs;
import eu.amadeos.apps.pdfviewer.bootstrap.PdfViewerModule;
import eu.amadeos.apps.pdfviewer.domain.DocumentThread;
import eu.amadeos.apps.pdfviewer.domain.PageLink;
import eu.amadeos.apps.pdfviewer.domain.ReadingPosition;
import eu.amadeos.apps.pdfviewer.domain.Rotation;
import eu.amadeos.apps.pdfviewer.domain.SearchHit;
import eu.amadeos.apps.pdfviewer.domain.Target;
import eu.amadeos.apps.pdfviewer.domain.ViewMode;
import eu.amadeos.apps.pdfviewer.domain.Zoom;
import eu.amadeos.apps.pdfviewer.repository.PdfViewerPreferences;
import eu.amadeos.apps.pdfviewer.repository.ReadingPositions;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    static final Messages MSG = Messages.load(PdfViewerModule.class, PdfViewerModule.MESSAGES, Locale.ENGLISH);

    @TempDir
    Path dir;

    private final FakeView view = new FakeView();
    private final List<String> browsed = new ArrayList<>();
    private DocumentThread thread;
    private PdfViewerPreferences preferences;
    private ReadingPositions positions;
    private MainPresenter presenter;
    private boolean exited;

    @BeforeEach
    void setUp() throws Exception {
        thread = new DocumentThread(new EdtExecutor());
        preferences = new PdfViewerPreferences(dir.resolve("config/pdfviewer.properties"));
        positions = new ReadingPositions(dir.resolve("config/positions.properties"), () -> 1000);
        DesktopLauncher launcher = new DesktopLauncher() {
            @Override
            public void browse(String uri) {
                browsed.add(uri);
            }
        };
        presenter = new MainPresenter(view, MSG, null, preferences, positions, thread, launcher, false,
                () -> exited = true);
        OnEdt.run(() -> presenter.start(List.of()));
    }

    @AfterEach
    void tearDown() {
        thread.close();
    }

    /** Lets the document thread and then the event thread finish what they were given, a few rounds deep. */
    private void settle() throws Exception {
        for (int i = 0; i < 8; i++) {
            thread.await();
            OnEdt.run(() -> { });
        }
    }

    private void open(Path file) throws Exception {
        OnEdt.run(() -> presenter.open(file));
        settle();
    }

    @Test
    void startsEmpty() {
        assertNull(view.session);
        assertEquals("PDF Viewer", view.title);
        assertEquals(ViewMode.CONTINUOUS, view.settings.view());
    }

    @Test
    void opensWithTitleOutlineAndRecentFiles() throws Exception {
        Path file = TestPdfs.sample(dir);
        open(file);
        assertNotNull(view.session);
        assertEquals(3, view.session.pageCount());
        assertEquals(TestPdfs.TITLE + " - PDF Viewer", view.title);
        assertEquals(2, view.outline.size());
        assertEquals(0, view.page);
        assertEquals(Zoom.FIT_WIDTH, view.zoom);
        assertEquals(List.of(file), view.recent);
        assertEquals(List.of(file), preferences.recentFiles());
    }

    @Test
    void openingFromTheCommandLineAcceptsFileUris() {
        assertEquals(Path.of("/tmp/a b.pdf"), MainPresenter.path("file:///tmp/a%20b.pdf"));
        assertEquals(Path.of("x.pdf"), MainPresenter.path("x.pdf"));
    }

    @Test
    void missingAndBrokenFiles() throws Exception {
        open(dir.resolve("gone.pdf"));
        assertTrue(view.messages.getFirst().contains("does not exist"), view.messages.toString());
        Path broken = Files.writeString(dir.resolve("broken.pdf"), "no pdf");
        open(broken);
        assertTrue(view.messages.get(1).startsWith("The document “broken.pdf” cannot be opened"));
        assertNull(view.session);
    }

    @Test
    void asksForThePasswordUntilItIsRight() throws Exception {
        view.passwords.add("guess");
        view.passwords.add("secret");
        open(TestPdfs.encrypted(dir));
        assertEquals(List.of("encrypted.pdf", "encrypted.pdf wrong"), view.passwordQuestions);
        assertNotNull(view.session);
    }

    @Test
    void cancellingThePasswordOpensNothing() throws Exception {
        open(TestPdfs.encrypted(dir));
        assertEquals(1, view.passwordQuestions.size());
        assertNull(view.session);
        assertTrue(view.messages.isEmpty());
    }

    @Test
    void pagesAndTypedPageNumbers() throws Exception {
        open(TestPdfs.pages(dir, "five.pdf", 5));
        OnEdt.run(() -> {
            presenter.next();
            presenter.next();
            assertEquals(2, view.page);
            presenter.previous();
            assertEquals(1, view.page);
            presenter.last();
            assertEquals(4, view.page);
            presenter.next();
            assertEquals(4, view.page);
            assertTrue(presenter.goToTyped("2"));
            assertEquals(1, view.page);
            assertFalse(presenter.goToTyped("9"));
            assertFalse(presenter.goToTyped("x"));
            assertEquals(2, view.beeps);
            presenter.pageShown(3);
            assertEquals(3, presenter.page());
        });
    }

    @Test
    void twoPagesTurnByRows() throws Exception {
        open(TestPdfs.pages(dir, "five.pdf", 5));
        OnEdt.run(() -> {
            presenter.setViewMode(ViewMode.TWO_PAGES);
            presenter.next();
            assertEquals(1, view.page);
            presenter.next();
            assertEquals(3, view.page);
            presenter.previous();
            assertEquals(1, view.page);
            assertEquals(ViewMode.TWO_PAGES, preferences.settings().view());
        });
    }

    @Test
    void linksJumpAndBackReturns() throws Exception {
        open(TestPdfs.sample(dir));
        OnEdt.run(() -> {
            view.offset = 0.25;
            presenter.follow(PageLink.to(new Rectangle2D.Double(), Target.page(2)));
            assertEquals(2, view.page);
            assertTrue(view.back);
            presenter.follow(new Target(1, 81.9));
            assertEquals("1 at 82 pt", view.scrolled);
            presenter.back();
            assertEquals(2, view.page);
            presenter.back();
            assertEquals(0, view.page);
            assertEquals("0 at 0.25", view.scrolled);
            assertFalse(view.back);
            assertTrue(view.forward);
            presenter.forward();
            assertEquals(2, view.page);
            presenter.follow(PageLink.to(new Rectangle2D.Double(), "https://example.org/"));
            assertEquals(List.of("https://example.org/"), browsed);
        });
    }

    @Test
    void searchJumpsToTheFirstHitAndSteps() throws Exception {
        open(TestPdfs.sample(dir));
        OnEdt.run(() -> presenter.search("needle", false));
        settle();
        SearchState search = view.search;
        assertFalse(search.running());
        assertEquals(List.of(1, 2, 2), search.hits().stream().map(SearchHit::page).toList());
        assertEquals(1, search.number());
        assertEquals(1, view.page);
        assertTrue(view.revealed.startsWith("1 "), view.revealed);
        OnEdt.run(() -> {
            presenter.search("needle", false);
            assertEquals(2, view.search.number());
            assertEquals(2, view.page);
            presenter.findNext();
            presenter.findNext();
            assertEquals(1, view.search.number());
            presenter.findPrevious();
            assertEquals(3, view.search.number());
        });
    }

    @Test
    void searchFromTheCurrentPageAndNothingFound() throws Exception {
        open(TestPdfs.sample(dir));
        OnEdt.run(() -> {
            presenter.goTo(2);
            presenter.search("NEEDLE", false);
        });
        settle();
        assertEquals(2, view.search.current().orElseThrow().page());
        OnEdt.run(() -> presenter.search("haystack needle", false));
        settle();
        assertTrue(view.search.hits().isEmpty());
        assertEquals(1, view.beeps);
        OnEdt.run(() -> presenter.search("  ", false));
        assertFalse(view.search.active());
    }

    @Test
    void readingPositionIsRememberedPerDocument() throws Exception {
        Path five = TestPdfs.pages(dir, "five.pdf", 5);
        Path sample = TestPdfs.sample(dir);
        open(five);
        OnEdt.run(() -> {
            presenter.goTo(3);
            presenter.setZoom(Zoom.of(1.5));
            presenter.rotateRight();
            view.offset = 0.5;
        });
        open(sample);
        assertEquals(0, view.page);
        assertEquals(Zoom.of(1.5), view.zoom);
        assertEquals(Rotation.NONE, view.rotation);
        assertEquals(Optional.of(new ReadingPosition(3, 0.5, Zoom.of(1.5), Rotation.RIGHT)), positions.get(five));
        open(five);
        assertEquals(3, view.page);
        assertEquals(Rotation.RIGHT, view.rotation);
        assertEquals("3 at 0.5", view.scrolled);
    }

    // --8<-- [start:reload-test]
    @Test
    void reloadKeepsPageZoomAndSearch() throws Exception {
        Path file = TestPdfs.pages(dir, "doc.pdf", 5);
        open(file);
        OnEdt.run(() -> presenter.search("Page 2", false));
        settle();
        OnEdt.run(() -> {
            presenter.goTo(4);
            presenter.setZoom(Zoom.FIT_PAGE);
        });
        TestPdfs.pages(dir, "doc.pdf", 3);
        OnEdt.run(presenter::reload);
        settle();
        assertEquals(3, view.session.pageCount());
        assertEquals(2, view.page);
        assertEquals(Zoom.FIT_PAGE, view.zoom);
        assertEquals("Reloaded: the file was changed.", view.status);
        assertEquals(1, view.search.hits().size());
    }
    // --8<-- [end:reload-test]

    @Test
    void zoomStepsFromTheShownFactorAndTypedZooms() throws Exception {
        open(TestPdfs.sample(dir));
        OnEdt.run(() -> {
            view.factor = 1.1;
            presenter.zoomIn();
            assertEquals(Zoom.of(1.25), view.zoom);
            view.factor = 1.25;
            presenter.zoomOut();
            assertEquals(Zoom.of(1), view.zoom);
            assertTrue(presenter.setZoomTyped("150 %"));
            assertEquals(Zoom.of(1.5), view.zoom);
            assertFalse(presenter.setZoomTyped("huge"));
            assertEquals(Zoom.of(1.5), preferences.settings().zoom());
        });
    }

    @Test
    void sidebarCopyPropertiesPresentationAndExit() throws Exception {
        open(TestPdfs.sample(dir));
        OnEdt.run(() -> {
            presenter.toggleSidebar();
            assertFalse(view.settings.sidebar());
            presenter.showContents(true);
            assertTrue(view.settings.sidebar());
            assertTrue(preferences.settings().contents());
            presenter.copy();
            assertEquals(1, view.beeps);
            view.selected = "brown fox";
            presenter.copy();
            assertEquals("brown fox", view.clipboard);
            presenter.properties();
            assertEquals(TestPdfs.TITLE + " | 3 | A4, 210 × 297 mm", view.properties);
            presenter.goTo(1);
            presenter.presentation();
            assertEquals(1, view.presentation);
            presenter.presentationEnded(2);
            assertEquals(2, view.page);
            presenter.exit();
        });
        assertTrue(view.closed);
        assertTrue(exited);
        assertEquals(2, positions.get(view.session.document().file()).orElseThrow().page());
    }
}
