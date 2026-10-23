package eu.amadeos.apps.pdfviewer.domain;

import java.util.List;
import java.util.Optional;

/** An entry of the table of contents (the PDF's outline or bookmarks) with its sub-entries. */
public record OutlineEntry(String title, Optional<Target> target, List<OutlineEntry> children) {
    public OutlineEntry {
        children = List.copyOf(children);
    }

    @Override
    public String toString() {
        return title;
    }
}
