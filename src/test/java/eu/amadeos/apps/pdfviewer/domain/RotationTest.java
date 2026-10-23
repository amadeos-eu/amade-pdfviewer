package eu.amadeos.apps.pdfviewer.domain;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationTest {
    private static final PageSize PAGE = new PageSize(200, 100);

    @Test
    void ofAnyMultipleOf90() {
        assertEquals(Rotation.LEFT, Rotation.of(-90));
        assertEquals(Rotation.NONE, Rotation.of(360));
        assertEquals(Rotation.RIGHT, Rotation.of(450));
        assertEquals(Rotation.NONE, Rotation.of(45));
        assertEquals(Rotation.NONE, Rotation.LEFT.clockwise());
        assertEquals(Rotation.HALF, Rotation.RIGHT.plus(Rotation.RIGHT));
    }

    @Test
    void sizes() {
        assertEquals(new PageSize(100, 200), Rotation.RIGHT.apply(PAGE));
        assertEquals(PAGE, Rotation.HALF.apply(PAGE));
    }

    /** The top left corner (a 10 × 20 box at 0, 0) moves to the top right when turned clockwise. */
    @Test
    void rectangles() {
        Rectangle2D corner = new Rectangle2D.Double(0, 0, 10, 20);
        assertEquals(new Rectangle2D.Double(80, 0, 20, 10), Rotation.RIGHT.apply(corner, PAGE));
        assertEquals(new Rectangle2D.Double(190, 80, 10, 20), Rotation.HALF.apply(corner, PAGE));
        assertEquals(new Rectangle2D.Double(0, 190, 20, 10), Rotation.LEFT.apply(corner, PAGE));
    }

    @Test
    void pointsBack() {
        Point2D p = new Point2D.Double(30, 40);
        for (Rotation r : Rotation.values()) {
            Rectangle2D dot = r.apply(new Rectangle2D.Double(30, 40, 0, 0), PAGE);
            Point2D back = r.unapply(new Point2D.Double(dot.getX(), dot.getY()), PAGE);
            assertEquals(p.getX(), back.getX(), 1e-9, r.toString());
            assertEquals(p.getY(), back.getY(), 1e-9, r.toString());
        }
    }
}
