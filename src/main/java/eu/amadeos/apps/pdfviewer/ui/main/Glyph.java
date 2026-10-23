package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.commons.swing.ToolIcon;

import java.awt.geom.Arc2D;
import java.awt.geom.Rectangle2D;

/** The toolbar pictures of the PDF viewer that amade-commons has no standard glyph for. */
enum Glyph implements ToolIcon.Glyph {
    SIDEBAR, PREVIOUS_PAGE, NEXT_PAGE, ROTATE_LEFT, ROTATE_RIGHT, FIND_PREVIOUS, FIND_NEXT, BACK, FORWARD;

    @Override
    public void paint(ToolIcon.Pen pen) {
        switch (this) {
            case SIDEBAR -> {
                pen.fillAndDraw(new Rectangle2D.Double(1.5, 2.5, 13, 11), pen.paper());
                pen.fill(new Rectangle2D.Double(2, 3, 4, 10), pen.light());
                pen.line(6, 2.5, 6, 13.5);
            }
            case PREVIOUS_PAGE -> pen.triangle(3, 11, 13, 11, 8, 4.5);
            case NEXT_PAGE -> pen.triangle(3, 5, 13, 5, 8, 11.5);
            case FIND_PREVIOUS -> pen.triangle(4, 10, 12, 10, 8, 5);
            case FIND_NEXT -> pen.triangle(4, 6, 12, 6, 8, 11);
            case ROTATE_LEFT -> {
                pen.mirror();
                rotate(pen);
            }
            case ROTATE_RIGHT -> rotate(pen);
            case BACK -> {
                pen.mirror();
                arrow(pen);
            }
            case FORWARD -> arrow(pen);
        }
    }

    private static void rotate(ToolIcon.Pen pen) {
        pen.bold(1.6f);
        pen.draw(new Arc2D.Double(3, 3, 10, 10, 110, -250, Arc2D.OPEN));
        pen.thin();
        pen.triangle(9.5, 1.5, 9.5, 6.5, 13.5, 3.5);
    }

    private static void arrow(ToolIcon.Pen pen) {
        pen.bold(2f);
        pen.line(2.5, 8, 9, 8);
        pen.thin();
        pen.triangle(8, 3.5, 8, 12.5, 13.5, 8);
    }
}
