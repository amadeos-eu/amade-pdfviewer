package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.domain.SearchHit;

import java.util.List;
import java.util.Optional;

/**
 * The search as the window shows it.
 *
 * @param hits     all hits found so far, by page and position
 * @param current  the hit shown, if any
 * @param running  true while pages are still searched
 * @param searched how many pages are searched
 */
public record SearchState(String query, List<SearchHit> hits, Optional<SearchHit> current, boolean running,
                          int searched) {
    public static final SearchState NONE = new SearchState("", List.of(), Optional.empty(), false, 0);

    public SearchState {
        hits = List.copyOf(hits);
    }

    public boolean active() {
        return !query.isEmpty();
    }

    /** Position of the current hit, from 1; 0 for none. */
    public int number() {
        return current.map(hit -> hits.indexOf(hit) + 1).orElse(0);
    }

    public List<SearchHit> hitsOn(int page) {
        return hits.stream().filter(hit -> hit.page() == page).toList();
    }
}
