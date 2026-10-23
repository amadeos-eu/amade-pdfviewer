package eu.amadeos.apps.pdfviewer.domain;

import java.awt.geom.Rectangle2D;
import java.util.Optional;

/**
 * A link on a page: to another place in the document or to an address (web, mail).
 *
 * @param area where it is on the shown page, in points from the top left
 */
public record PageLink(Rectangle2D area, Optional<Target> target, Optional<String> uri) {
    public static PageLink to(Rectangle2D area, Target target) {
        return new PageLink(area, Optional.of(target), Optional.empty());
    }

    public static PageLink to(Rectangle2D area, String uri) {
        return new PageLink(area, Optional.empty(), Optional.of(uri));
    }
}
