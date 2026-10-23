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
import java.util.List;
import java.util.Optional;

/**
 * The window as the presenter sees it. All methods run on the EDT. As the {@link DocumentSession.Listener} it
 * repaints pages, thumbnails and text marks when they are ready.
 */
public interface MainView extends DocumentSession.Listener {
    /** View mode and sidebar in the menus, toolbar and window. */
    void showSettings(Settings settings);

    /**
     * Shows a newly opened document, or none.
     *
     * @param session null when no document is open
     */
    void showDocument(DocumentSession session, String title, List<OutlineEntry> outline);

    /** Lays the pages out anew; in single page view {@code page} is the page shown. */
    void showLayout(Zoom zoom, Rotation rotation, int page);

    /** The current page in the toolbar, status bar and thumbnails; does not scroll. */
    void showPage(int page);

    /**
     * Scrolls the page to the top of the window.
     *
     * @param offset from 0 (page top at the window top) to 1, or a height in points from the page top when
     *               {@code points} is true
     */
    void scrollTo(int page, double offset, boolean points);

    /** Scrolls the area of the page (points of the page as the document shows it) into the window. */
    void reveal(int page, Rectangle2D area);

    /** The fraction of the current page above the window's top edge, to come back to it later. */
    double pageOffset();

    /** The zoom factor the pages have now, also for the fits; 1 is 100 %. */
    double zoomFactor();

    void showSearch(SearchState state);

    void showHistory(boolean back, boolean forward);

    void showRecent(List<Path> files);

    /** A short note in the status bar, e.g. "Reloaded". */
    void showStatus(String message);

    /** The selected text, empty when nothing is selected. */
    String selectedText();

    void selectAll(int page);

    void copyToClipboard(String text);

    Optional<Path> chooseFile(Optional<Path> folder);

    /** Asks for the password of a document; empty when cancelled. */
    Optional<String> askPassword(String fileName, boolean wrong);

    /** The print dialog for the job; false when cancelled. */
    boolean printDialog(PrinterJob job, PrintRequestAttributeSet attributes);

    void showProperties(DocumentProperties properties, Path file, String pageSize);

    /** Shows the pages full screen, from this page on. */
    void startPresentation(int page);

    void focusSearch();

    void focusPage();

    void beep();

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void close();
}
