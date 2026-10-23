package eu.amadeos.apps.pdfviewer.domain;

/** Where a search text was found: characters {@code start} to {@code end} (exclusive) of a page's text. */
public record SearchHit(int page, int start, int end) {
}
