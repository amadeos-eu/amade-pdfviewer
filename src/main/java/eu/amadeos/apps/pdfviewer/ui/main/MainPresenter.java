package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.domain.DocumentProperties;
import eu.amadeos.apps.pdfviewer.domain.DocumentSession;
import eu.amadeos.apps.pdfviewer.domain.DocumentThread;
import eu.amadeos.apps.pdfviewer.domain.DocumentThread.Priority;
import eu.amadeos.apps.pdfviewer.domain.OutlineEntry;
import eu.amadeos.apps.pdfviewer.domain.PageLayout;
import eu.amadeos.apps.pdfviewer.domain.PageLink;
import eu.amadeos.apps.pdfviewer.domain.PageSize;
import eu.amadeos.apps.pdfviewer.domain.PageText;
import eu.amadeos.apps.pdfviewer.domain.PaperNames;
import eu.amadeos.apps.pdfviewer.domain.PasswordException;
import eu.amadeos.apps.pdfviewer.domain.PdfDocument;
import eu.amadeos.apps.pdfviewer.domain.ReadingPosition;
import eu.amadeos.apps.pdfviewer.domain.Rotation;
import eu.amadeos.apps.pdfviewer.domain.SearchHit;
import eu.amadeos.apps.pdfviewer.domain.Settings;
import eu.amadeos.apps.pdfviewer.domain.Target;
import eu.amadeos.apps.pdfviewer.domain.ViewMode;
import eu.amadeos.apps.pdfviewer.domain.Zoom;
import eu.amadeos.apps.pdfviewer.repository.PdfViewerPreferences;
import eu.amadeos.apps.pdfviewer.repository.ReadingPositions;
import eu.amadeos.commons.config.ConfigWatcher;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.swing.Timer;
import java.awt.geom.Rectangle2D;
import java.awt.print.Pageable;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Opens documents and moves through them: pages, zoom, rotation, view mode, search, links, history, printing
 * and reloading a changed file. The window does the drawing; the presenter decides what it shows.
 */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    /** Wait this long after the last change of the file before reloading it, so LaTeX can finish writing. */
    static final int RELOAD_DELAY_MILLIS = 700;
    static final int RELOAD_ATTEMPTS = 4;
    static final int HISTORY = 50;

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final PdfViewerPreferences preferences;
    private final ReadingPositions positions;
    private final DocumentThread thread;
    private final DesktopLauncher launcher;
    private final boolean watchFiles;
    private final Runnable exit;
    private final Deque<ReadingPosition> back = new ArrayDeque<>();
    private final Deque<ReadingPosition> forward = new ArrayDeque<>();
    private Settings settings = Settings.DEFAULT;
    private DocumentSession session;
    private Path file;
    private String password;
    private int page;
    private Zoom zoom = Zoom.FIT_WIDTH;
    private Rotation rotation = Rotation.NONE;
    private SearchState search = SearchState.NONE;
    private boolean searchJump;
    private boolean searchBackwards;
    private int searchGeneration;
    private ConfigWatcher watcher;
    private Timer reloadTimer;
    private int reloadAttempts;

    /**
     * @param locales    null in tests: no language menu
     * @param watchFiles false in tests: no reloading when the file changes
     * @param exit       ends the program after the window closed
     */
    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, PdfViewerPreferences preferences,
                         ReadingPositions positions, DocumentThread thread, DesktopLauncher launcher,
                         boolean watchFiles, Runnable exit) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.preferences = preferences;
        this.positions = positions;
        this.thread = thread;
        this.launcher = launcher;
        this.watchFiles = watchFiles;
        this.exit = exit;
    }

    /** Shows the window's state and opens the first file of the command line, if there is one. */
    public void start(List<String> arguments) {
        EdtExecutor.requireEdt();
        settings = preferences.settings();
        zoom = settings.zoom();
        view.showSettings(settings);
        view.showDocument(null, msg.get("window.title"), List.of());
        view.showRecent(preferences.recentFiles());
        view.showHistory(false, false);
        arguments.stream().filter(a -> !a.startsWith("-")).findFirst().map(MainPresenter::path).ifPresent(this::open);
    }

    /** A command-line argument: a path or a {@code file://} URI as file managers pass them. */
    static Path path(String argument) {
        if (argument.startsWith("file://")) {
            try {
                return Path.of(java.net.URI.create(argument));
            } catch (IllegalArgumentException e) {
                return Path.of(argument.substring("file://".length()));
            }
        }
        return Path.of(argument);
    }

    // ---------------------------------------------------------------- state for the window and tests

    public Settings settings() {
        return settings;
    }

    public DocumentSession session() {
        return session;
    }

    public Path file() {
        return file;
    }

    public int page() {
        return page;
    }

    public Zoom zoom() {
        return zoom;
    }

    public Rotation rotation() {
        return rotation;
    }

    public SearchState search() {
        return search;
    }

    // ---------------------------------------------------------------- opening

    /** File → Open: the file chooser in the folder of the last document. */
    public void chooseAndOpen() {
        Optional<Path> folder = file != null ? Optional.ofNullable(file.getParent()) : preferences.lastFolder();
        view.chooseFile(folder).ifPresent(chosen -> {
            if (chosen.getParent() != null) preferences.setLastFolder(chosen.getParent());
            open(chosen);
        });
    }

    // --8<-- [start:open]
    public void open(Path newFile) {
        EdtExecutor.requireEdt();
        Path absolute = newFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            view.showError(msg.format("error.not-found", absolute.toString()));
            return;
        }
        load(absolute, null, false);
    }

    private record Loaded(PdfDocument document, List<OutlineEntry> outline) {
    }

    /** Opens the file on the document thread; asks for a password as long as one is needed and given. */
    private void load(Path target, String pw, boolean reload) {
        thread.submit(Priority.DOCUMENT, () -> {
            PdfDocument document = PdfDocument.open(target, pw);
            return new Loaded(document, document.outline());
        }, loaded -> opened(loaded, pw, reload), failure -> {
            if (failure instanceof PasswordException e) {
                view.askPassword(target.getFileName().toString(), e.wrong())
                        .ifPresent(given -> load(target, given, reload));
            } else if (reload) {
                retryReload(failure);
            } else {
                LOG.warn("{} cannot be opened", target, failure);
                view.showError(msg.format("error.open", target.getFileName().toString(), reason(failure)));
            }
        });
    }
    // --8<-- [end:open]

    private void opened(Loaded loaded, String pw, boolean reload) {
        PdfDocument document = loaded.document();
        Path newFile = document.file();
        ReadingPosition keep;
        if (reload && session != null) {
            keep = position();
        } else {
            savePosition();
            keep = positions.get(newFile).orElse(null);
            back.clear();
            forward.clear();
        }
        if (session != null) session.close();
        if (!newFile.equals(file)) stopWatching();
        session = new DocumentSession(document, thread, view);
        file = newFile;
        password = pw;
        reloadAttempts = 0;
        page = keep == null ? 0 : Math.min(keep.page(), document.pageCount() - 1);
        zoom = keep == null ? settings.zoom() : keep.zoom();
        rotation = keep == null ? Rotation.NONE : keep.rotation();
        String title = document.properties().shownTitle().orElse(newFile.getFileName().toString());
        view.showDocument(session, msg.format("window.title.document", title), loaded.outline());
        view.showLayout(zoom, rotation, page);
        view.scrollTo(page, keep == null ? 0 : keep.offset(), false);
        view.showPage(page);
        view.showHistory(!back.isEmpty(), !forward.isEmpty());
        if (reload) {
            view.showStatus(msg.get("status.reloaded"));
            if (search.active()) startSearch(search.query(), false, false);
        } else {
            clearSearch();
            preferences.addRecent(newFile);
            view.showRecent(preferences.recentFiles());
        }
        watch(newFile);
    }

    private static String reason(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public void openRecent(Path recent) {
        open(recent);
    }

    public void clearRecent() {
        preferences.clearRecent();
        view.showRecent(List.of());
    }

    // --8<-- [start:reload]
    /** File → Reload, and after the file changed on the disk: the same page, zoom and search. */
    public void reload() {
        if (file == null) return;
        if (!Files.isRegularFile(file)) {
            // Deleted and not yet written again: try again in a moment.
            retryReload(new IOException("missing"));
            return;
        }
        load(file, password, true);
    }

    private void retryReload(Throwable failure) {
        if (++reloadAttempts > RELOAD_ATTEMPTS) {
            LOG.warn("{} could not be reloaded", file, failure);
            reloadAttempts = 0;
            return;
        }
        if (watchFiles) scheduleReload(1000);
    }

    private void watch(Path watched) {
        if (!watchFiles || watcher != null || watched.getParent() == null) return;
        watcher = new ConfigWatcher(watched.getParent(), Set.of(watched.getFileName().toString()), "pdf-reload",
                () -> javax.swing.SwingUtilities.invokeLater(() -> scheduleReload(RELOAD_DELAY_MILLIS)));
        if (!watcher.start()) watcher = null;
    }

    private void scheduleReload(int delay) {
        if (reloadTimer == null) {
            reloadTimer = new Timer(delay, e -> reload());
            reloadTimer.setRepeats(false);
        }
        reloadTimer.setInitialDelay(delay);
        reloadTimer.restart();
    }
    // --8<-- [end:reload]

    private void stopWatching() {
        if (watcher != null) watcher.close();
        watcher = null;
        if (reloadTimer != null) reloadTimer.stop();
    }

    // ---------------------------------------------------------------- reading position

    private ReadingPosition position() {
        return new ReadingPosition(page, view.pageOffset(), zoom, rotation);
    }

    private void savePosition() {
        if (session != null && file != null) positions.put(file, position());
    }

    // ---------------------------------------------------------------- pages

    /** The window scrolled to another page. */
    public void pageShown(int shown) {
        if (session == null || shown == page) return;
        page = Math.clamp(shown, 0, session.pageCount() - 1);
        view.showPage(page);
    }

    public void goTo(int target) {
        if (session == null) return;
        int clamped = Math.clamp(target, 0, session.pageCount() - 1);
        boolean relayout = settings.view() == ViewMode.SINGLE && clamped != page;
        page = clamped;
        if (relayout) view.showLayout(zoom, rotation, page);
        view.scrollTo(page, 0, false);
        view.showPage(page);
    }

    /** The page number typed into the toolbar, counting from 1; false if it is no page of the document. */
    public boolean goToTyped(String text) {
        if (session == null) return false;
        try {
            int number = Integer.parseInt(text.strip());
            if (number < 1 || number > session.pageCount()) {
                view.beep();
                view.showPage(page);
                return false;
            }
            remember();
            goTo(number - 1);
            return true;
        } catch (NumberFormatException e) {
            view.beep();
            view.showPage(page);
            return false;
        }
    }

    public void next() {
        if (session != null) goTo(PageLayout.next(session.pageCount(), settings.view(), page));
    }

    public void previous() {
        if (session != null) goTo(PageLayout.previous(session.pageCount(), settings.view(), page));
    }

    public void first() {
        if (session == null) return;
        remember();
        goTo(0);
    }

    public void last() {
        if (session == null) return;
        remember();
        goTo(session.pageCount() - 1);
    }

    public void goToPage() {
        if (session != null) view.focusPage();
    }

    // ---------------------------------------------------------------- links, contents and history

    // --8<-- [start:follow]
    /** A link was clicked: inside the document it jumps there (Back returns), else the address is opened. */
    public void follow(PageLink link) {
        if (link.target().isPresent()) {
            follow(link.target().get());
        } else {
            link.uri().ifPresent(this::browse);
        }
    }

    public void follow(Target target) {
        if (session == null || target.page() < 0 || target.page() >= session.pageCount()) return;
        remember();
        goTo(target.page());
        if (target.hasTop()) view.scrollTo(target.page(), target.top(), true);
    }

    private void browse(String uri) {
        try {
            launcher.browse(uri);
        } catch (IOException | RuntimeException e) {
            view.showError(msg.format("error.link", uri, reason(e)));
        }
    }
    // --8<-- [end:follow]

    private void remember() {
        if (session == null) return;
        back.push(position());
        while (back.size() > HISTORY) back.removeLast();
        forward.clear();
        view.showHistory(true, false);
    }

    public void back() {
        if (back.isEmpty()) return;
        forward.push(position());
        restore(back.pop());
    }

    public void forward() {
        if (forward.isEmpty()) return;
        back.push(position());
        restore(forward.pop());
    }

    private void restore(ReadingPosition place) {
        goTo(place.page());
        view.scrollTo(page, place.offset(), false);
        view.showHistory(!back.isEmpty(), !forward.isEmpty());
    }

    // ---------------------------------------------------------------- zoom, rotation, view

    public void setZoom(Zoom newZoom) {
        zoom = newZoom;
        settings = settings.withZoom(newZoom);
        preferences.setSettings(settings);
        if (session != null) view.showLayout(zoom, rotation, page);
        view.showSettings(settings);
    }

    /** The zoom box: "Fit Page", "Fit Width" or a percentage typed in; false if it means nothing. */
    public boolean setZoomTyped(String text) {
        Optional<Zoom> typed = Zoom.parse(text);
        if (typed.isEmpty()) {
            view.beep();
            view.showLayout(zoom, rotation, page);
            return false;
        }
        setZoom(typed.get());
        return true;
    }

    public void zoomIn() {
        setZoom(Zoom.in(view.zoomFactor()));
    }

    public void zoomOut() {
        setZoom(Zoom.out(view.zoomFactor()));
    }

    public void rotateRight() {
        rotate(rotation.clockwise());
    }

    public void rotateLeft() {
        rotate(rotation.counterClockwise());
    }

    private void rotate(Rotation newRotation) {
        if (session == null) return;
        rotation = newRotation;
        view.showLayout(zoom, rotation, page);
        view.scrollTo(page, 0, false);
    }

    public void setViewMode(ViewMode mode) {
        settings = settings.withView(mode);
        preferences.setSettings(settings);
        view.showSettings(settings);
        if (session != null) {
            view.showLayout(zoom, rotation, page);
            view.scrollTo(page, 0, false);
        }
    }

    public void setSidebar(boolean shown) {
        settings = settings.withSidebar(shown);
        preferences.setSettings(settings);
        view.showSettings(settings);
    }

    public void toggleSidebar() {
        setSidebar(!settings.sidebar());
    }

    /** The sidebar shows the table of contents (true) or the thumbnails (false), and is opened. */
    public void showContents(boolean contents) {
        settings = settings.withContents(contents).withSidebar(true);
        preferences.setSettings(settings);
        view.showSettings(settings);
    }

    public void presentation() {
        if (session != null) view.startPresentation(page);
    }

    /** The presentation ended on this page. */
    public void presentationEnded(int last) {
        goTo(last);
    }

    // ---------------------------------------------------------------- search

    public void find() {
        if (session != null) view.focusSearch();
    }

    // --8<-- [start:search]
    /**
     * Enter in the search field: a new text starts a search from the current page on; the same text again
     * goes to its next (or previous) hit.
     */
    public void search(String text, boolean backwards) {
        if (session == null) return;
        String query = text == null ? "" : text.strip();
        if (query.isEmpty()) {
            clearSearch();
            return;
        }
        if (query.equals(search.query())) {
            if (!search.hits().isEmpty()) step(backwards);
            else if (!search.running()) view.beep();
            return;
        }
        startSearch(query, backwards, true);
    }

    private void startSearch(String query, boolean backwards, boolean jump) {
        int generation = ++searchGeneration;
        search = new SearchState(query, List.of(), Optional.empty(), true, 0);
        searchJump = jump;
        searchBackwards = backwards;
        thread.cancel(Priority.SEARCH);
        view.showSearch(search);
        int count = session.pageCount();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) order.add(backwards ? Math.floorMod(page - i, count) : (page + i) % count);
        searchPage(generation, order, 0);
    }

    /** One page at a time, so that rendering in between stays quick. */
    private void searchPage(int generation, List<Integer> order, int index) {
        if (generation != searchGeneration) return;
        if (index >= order.size()) {
            search = new SearchState(search.query(), search.hits(), search.current(), false, order.size());
            if (search.hits().isEmpty()) view.beep();
            view.showSearch(search);
            return;
        }
        int searched = order.get(index);
        DocumentSession current = session;
        String query = search.query();
        current.thread().submit(Priority.SEARCH, () -> {
            PageText text = current.loadText(searched);
            List<SearchHit> found = new ArrayList<>();
            for (int[] range : text.find(query)) found.add(new SearchHit(searched, range[0], range[1]));
            return found;
        }, found -> {
            if (generation != searchGeneration || current != session) return;
            List<SearchHit> hits = new ArrayList<>(search.hits());
            hits.addAll(found);
            hits.sort(Comparator.comparingInt(SearchHit::page).thenComparingInt(SearchHit::start));
            Optional<SearchHit> shown = search.current();
            if (searchJump && !found.isEmpty()) {
                shown = Optional.of(searchBackwards ? found.getLast() : found.getFirst());
                searchJump = false;
            }
            search = new SearchState(query, hits, shown, true, index + 1);
            view.showSearch(search);
            if (shown.isPresent() && found.contains(shown.get())) show(shown.get());
            searchPage(generation, order, index + 1);
        }, failure -> searchPage(generation, order, index + 1));
    }

    private void step(boolean backwards) {
        List<SearchHit> hits = search.hits();
        int index = search.current().map(hits::indexOf).orElse(-1);
        int next = index < 0 ? (backwards ? hits.size() - 1 : 0)
                : Math.floorMod(index + (backwards ? -1 : 1), hits.size());
        SearchHit hit = hits.get(next);
        search = new SearchState(search.query(), hits, Optional.of(hit), search.running(), search.searched());
        view.showSearch(search);
        show(hit);
    }
    // --8<-- [end:search]

    private void show(SearchHit hit) {
        if (hit.page() != page) goTo(hit.page());
        PageText text = session.textIfLoaded(hit.page());
        if (text == null) return;
        Rectangle2D area = null;
        for (Rectangle2D r : text.areas(hit.start(), hit.end())) area = area == null ? r : area.createUnion(r);
        if (area != null) view.reveal(hit.page(), area);
    }

    public void findNext() {
        if (search.active()) search(search.query(), false);
        else find();
    }

    public void findPrevious() {
        if (search.active()) search(search.query(), true);
        else find();
    }

    public void clearSearch() {
        searchGeneration++;
        if (session != null) thread.cancel(Priority.SEARCH);
        search = SearchState.NONE;
        view.showSearch(search);
    }

    // ---------------------------------------------------------------- edit

    public void copy() {
        String text = view.selectedText();
        if (text.isEmpty()) view.beep();
        else view.copyToClipboard(text);
    }

    public void selectAll() {
        if (session != null) view.selectAll(page);
    }

    // ---------------------------------------------------------------- print and properties

    // --8<-- [start:print]
    public void print() {
        if (session == null) return;
        String jobName = file.getFileName().toString();
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(jobName);
        Pageable pages = session.document().pageable();
        job.setPageable(pages);
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new JobName(jobName, null));
        if (!view.printDialog(job, attributes)) return;
        view.showStatus(msg.get("status.printing"));
        DocumentSession printed = session;
        // PDFBox renders the pages for the printer: on the document thread, never next to the window's pictures.
        thread.submit(Priority.DOCUMENT, () -> {
            if (printed.closed()) throw new IOException("closed");
            job.print(attributes);
            return true;
        }, done -> view.showStatus(msg.get("status.printed")), failure -> {
            LOG.warn("Printing {} failed", jobName, failure);
            view.showStatus("");
            view.showError(msg.format("error.print", reason(failure)));
        });
    }
    // --8<-- [end:print]

    public void properties() {
        if (session == null) return;
        DocumentProperties properties = session.document().properties();
        view.showProperties(properties, file, pageSize(session.sizes().get(page)));
    }

    /** "A4, 210 × 297 mm" or "200 × 300 mm". */
    public String pageSize(PageSize size) {
        String mm = msg.format("status.mm", Math.round(size.widthMm()), Math.round(size.heightMm()));
        String name = PaperNames.of(size);
        return name.isEmpty() ? mm : name + ", " + mm;
    }

    // ---------------------------------------------------------------- language, about, exit

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-pdfviewer"));
        }
    }

    public void about() {
        view.showAbout();
    }

    public void exit() {
        savePosition();
        stopWatching();
        if (session != null) session.close();
        view.close();
        exit.run();
    }

    Locale locale() {
        return msg.locale();
    }
}
