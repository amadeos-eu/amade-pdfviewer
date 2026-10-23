package eu.amadeos.apps.pdfviewer.domain;

/**
 * A place in the document that the contents or a link leads to.
 *
 * @param page index from 0
 * @param top  the height on the page to show at the top of the window, in points from the top of the shown
 *             page; {@code NaN} for the top of the page
 */
public record Target(int page, double top) {
    public static Target page(int page) {
        return new Target(page, Double.NaN);
    }

    public boolean hasTop() {
        return !Double.isNaN(top);
    }
}
