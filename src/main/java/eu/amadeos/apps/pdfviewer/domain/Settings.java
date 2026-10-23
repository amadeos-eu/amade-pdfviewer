package eu.amadeos.apps.pdfviewer.domain;

/**
 * The choices that stay from one start to the next: how new documents are shown and whether the sidebar is open.
 *
 * @param zoom     for documents opened for the first time
 * @param contents true when the sidebar shows the table of contents, false for the page thumbnails
 */
public record Settings(ViewMode view, Zoom zoom, boolean sidebar, boolean contents) {
    public static final Settings DEFAULT = new Settings(ViewMode.CONTINUOUS, Zoom.FIT_WIDTH, true, false);

    public Settings withView(ViewMode newView) {
        return new Settings(newView, zoom, sidebar, contents);
    }

    public Settings withZoom(Zoom newZoom) {
        return new Settings(view, newZoom, sidebar, contents);
    }

    public Settings withSidebar(boolean shown) {
        return new Settings(view, zoom, shown, contents);
    }

    public Settings withContents(boolean shown) {
        return new Settings(view, zoom, sidebar, shown);
    }
}
