package eu.amadeos.apps.pdfviewer.domain;

import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * What the document says about itself, for File → Properties and the window title. Texts are empty when the
 * document leaves them out.
 */
public record DocumentProperties(String title, String author, String subject, String keywords, String creator,
                                 String producer, Optional<ZonedDateTime> created, Optional<ZonedDateTime> modified,
                                 String version, int pages, boolean encrypted, long fileSize) {

    // --8<-- [start:shown-title]
    /**
     * The title worth showing in the window title, or empty: many programs write their own name, a file name
     * or "Untitled" into the title field.
     */
    public Optional<String> shownTitle() {
        String t = title == null ? "" : title.strip();
        String lower = t.toLowerCase(Locale.ROOT);
        if (t.isEmpty() || lower.startsWith("untitled") || lower.startsWith("microsoft word - ")
                || lower.matches(".*\\.(pdf|docx?|odt|tex|dvi|ps|indd|qxd|rtf|txt)$")) {
            return Optional.empty();
        }
        return Optional.of(t);
    }
    // --8<-- [end:shown-title]
}
