package eu.amadeos.apps.pdfviewer.domain;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/** A quarter-turn clockwise, as a PDF page's {@code /Rotate} or as the user turns the view. */
public enum Rotation {
    NONE(0), RIGHT(90), HALF(180), LEFT(270);

    private final int degrees;

    Rotation(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }

    /** The rotation for any multiple of 90 degrees, negative ones too; other values count as none. */
    public static Rotation of(int degrees) {
        int normal = Math.floorMod(degrees, 360);
        for (Rotation r : values()) {
            if (r.degrees == normal) return r;
        }
        return NONE;
    }

    /** A quarter-turn more, clockwise. */
    public Rotation clockwise() {
        return of(degrees + 90);
    }

    public Rotation counterClockwise() {
        return of(degrees - 90);
    }

    public Rotation plus(Rotation other) {
        return of(degrees + other.degrees);
    }

    public boolean quarter() {
        return this == RIGHT || this == LEFT;
    }

    /** The size after turning a page of the given size. */
    public PageSize apply(PageSize size) {
        return quarter() ? new PageSize(size.height(), size.width()) : size;
    }

    // --8<-- [start:apply-rect]
    /**
     * A rectangle on a page of the given (unturned) size, origin top left, as it lies after the page is turned.
     */
    public Rectangle2D apply(Rectangle2D r, PageSize size) {
        double w = size.width();
        double h = size.height();
        return switch (this) {
            case NONE -> new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight());
            case RIGHT -> new Rectangle2D.Double(h - r.getMaxY(), r.getX(), r.getHeight(), r.getWidth());
            case HALF -> new Rectangle2D.Double(w - r.getMaxX(), h - r.getMaxY(), r.getWidth(), r.getHeight());
            case LEFT -> new Rectangle2D.Double(r.getY(), w - r.getMaxX(), r.getHeight(), r.getWidth());
        };
    }
    // --8<-- [end:apply-rect]

    /** The point on the unturned page (of the given unturned size) that lies at {@code p} on the turned page. */
    public Point2D unapply(Point2D p, PageSize size) {
        double w = size.width();
        double h = size.height();
        return switch (this) {
            case NONE -> new Point2D.Double(p.getX(), p.getY());
            case RIGHT -> new Point2D.Double(p.getY(), h - p.getX());
            case HALF -> new Point2D.Double(w - p.getX(), h - p.getY());
            case LEFT -> new Point2D.Double(w - p.getY(), p.getX());
        };
    }
}
