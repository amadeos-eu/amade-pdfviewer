package eu.amadeos.apps.pdfviewer.domain;

/**
 * Where a reader stopped in a document, to open it there again.
 *
 * @param page   index from 0
 * @param offset how far the top of the window was down the page, from 0 (top) to 1 (bottom)
 */
public record ReadingPosition(int page, double offset, Zoom zoom, Rotation rotation) {
    public ReadingPosition {
        page = Math.max(0, page);
        offset = Double.isFinite(offset) ? Math.clamp(offset, 0, 1) : 0;
    }
}
