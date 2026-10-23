package eu.amadeos.apps.pdfviewer.domain;

/** The size of a page as it is shown, in points (1/72 inch). */
public record PageSize(double width, double height) {
    public PageSize {
        if (!(width > 0) || !(height > 0)) throw new IllegalArgumentException("Page size " + width + " × " + height);
    }

    public boolean landscape() {
        return width > height;
    }

    public double widthMm() {
        return width / 72 * 25.4;
    }

    public double heightMm() {
        return height / 72 * 25.4;
    }
}
