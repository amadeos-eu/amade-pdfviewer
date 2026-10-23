package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.domain.DocumentProperties;
import eu.amadeos.apps.pdfviewer.domain.DocumentSession;
import eu.amadeos.apps.pdfviewer.domain.OutlineEntry;
import eu.amadeos.apps.pdfviewer.domain.Rotation;
import eu.amadeos.apps.pdfviewer.domain.Settings;
import eu.amadeos.apps.pdfviewer.domain.Zoom;

import javax.print.attribute.PrintRequestAttributeSet;
import java.awt.geom.Rectangle2D;
import java.awt.print.PrinterJob;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** A window that remembers what it was asked to show. */
class FakeView implements MainView {
    Settings settings;
    DocumentSession session;
    String title;
    List<OutlineEntry> outline = List.of();
    Zoom zoom;
    Rotation rotation;
    int page = -1;
    String scrolled;
    String revealed;
    double offset;
    double factor = 1;
    SearchState search = SearchState.NONE;
    boolean back;
    boolean forward;
    List<Path> recent = List.of();
    String status = "";
    String selected = "";
    String clipboard;
    final Deque<String> passwords = new ArrayDeque<>();
    final List<String> passwordQuestions = new ArrayList<>();
    Path chosen;
    String properties;
    int presentation = -1;
    int beeps;
    boolean searchFocused;
    boolean pageFocused;
    final List<String> messages = new ArrayList<>();
    boolean closed;

    @Override
    public void showSettings(Settings newSettings) {
        settings = newSettings;
    }

    @Override
    public void showDocument(DocumentSession newSession, String newTitle, List<OutlineEntry> newOutline) {
        session = newSession;
        title = newTitle;
        outline = newOutline;
    }

    @Override
    public void showLayout(Zoom newZoom, Rotation newRotation, int newPage) {
        zoom = newZoom;
        rotation = newRotation;
    }

    @Override
    public void showPage(int newPage) {
        page = newPage;
    }

    @Override
    public void scrollTo(int toPage, double toOffset, boolean points) {
        scrolled = toPage + (points ? " at " + Math.round(toOffset) + " pt" : " at " + toOffset);
    }

    @Override
    public void reveal(int toPage, Rectangle2D area) {
        revealed = toPage + " " + Math.round(area.getX()) + "," + Math.round(area.getY());
    }

    @Override
    public double pageOffset() {
        return offset;
    }

    @Override
    public double zoomFactor() {
        return factor;
    }

    @Override
    public void showSearch(SearchState state) {
        search = state;
    }

    @Override
    public void showHistory(boolean canGoBack, boolean canGoForward) {
        back = canGoBack;
        forward = canGoForward;
    }

    @Override
    public void showRecent(List<Path> files) {
        recent = files;
    }

    @Override
    public void showStatus(String message) {
        status = message;
    }

    @Override
    public String selectedText() {
        return selected;
    }

    @Override
    public void selectAll(int toPage) {
        selected = "all of " + toPage;
    }

    @Override
    public void copyToClipboard(String text) {
        clipboard = text;
    }

    @Override
    public Optional<Path> chooseFile(Optional<Path> folder) {
        return Optional.ofNullable(chosen);
    }

    @Override
    public Optional<String> askPassword(String fileName, boolean wrong) {
        passwordQuestions.add(fileName + (wrong ? " wrong" : ""));
        return Optional.ofNullable(passwords.poll());
    }

    @Override
    public boolean printDialog(PrinterJob job, PrintRequestAttributeSet attributes) {
        return false;
    }

    @Override
    public void showProperties(DocumentProperties p, Path file, String pageSize) {
        properties = p.title() + " | " + p.pages() + " | " + pageSize;
    }

    @Override
    public void startPresentation(int fromPage) {
        presentation = fromPage;
    }

    @Override
    public void focusSearch() {
        searchFocused = true;
    }

    @Override
    public void focusPage() {
        pageFocused = true;
    }

    @Override
    public void beep() {
        beeps++;
    }

    @Override
    public void showError(String message) {
        messages.add(message);
    }

    @Override
    public void showInfo(String message) {
        messages.add(message);
    }

    @Override
    public void showAbout() {
        messages.add("about");
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void pageReady(int readyPage) {
    }

    @Override
    public void thumbnailReady(int readyPage) {
    }

    @Override
    public void textReady(int readyPage) {
    }
}
