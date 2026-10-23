package eu.amadeos.apps.pdfviewer.domain;

import java.util.Optional;

/** How the pages are laid out in the window. */
public enum ViewMode {
    /** All pages one below the other, scrolled through. */
    CONTINUOUS("continuous"),
    /** One page at a time. */
    SINGLE("single"),
    /** Two pages side by side, the first page alone on the right like the cover of a book. */
    TWO_PAGES("two-pages");

    private final String key;

    ViewMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<ViewMode> forKey(String key) {
        for (ViewMode mode : values()) {
            if (mode.key.equals(key)) return Optional.of(mode);
        }
        return Optional.empty();
    }
}
