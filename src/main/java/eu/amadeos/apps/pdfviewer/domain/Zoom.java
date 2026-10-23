package eu.amadeos.apps.pdfviewer.domain;

import java.util.Optional;

/**
 * How large the pages are shown: fitted to the window, or a fixed factor where 1 is the real size on a
 * 96 dpi screen.
 *
 * @param factor for {@link Kind#FIXED}; ignored for the others
 */
public record Zoom(Kind kind, double factor) {
    public enum Kind { FIT_PAGE, FIT_WIDTH, FIXED }

    public static final Zoom FIT_PAGE = new Zoom(Kind.FIT_PAGE, 1);
    public static final Zoom FIT_WIDTH = new Zoom(Kind.FIT_WIDTH, 1);
    public static final double MIN = 0.1;
    public static final double MAX = 8;
    /** The steps of Zoom In and Zoom Out, and the choices of the zoom box. */
    public static final double[] STEPS = {0.1, 0.25, 0.33, 0.5, 0.67, 0.75, 1, 1.25, 1.5, 2, 3, 4, 6, 8};

    public Zoom {
        if (kind != Kind.FIXED) {
            factor = 1;
        } else if (!(factor > 0)) {
            throw new IllegalArgumentException("Zoom factor " + factor);
        } else {
            factor = Math.clamp(factor, MIN, MAX);
        }
    }

    public static Zoom of(double factor) {
        return new Zoom(Kind.FIXED, factor);
    }

    /** The next step above the factor the pages have now. */
    public static Zoom in(double current) {
        for (double step : STEPS) {
            if (step > current * 1.01) return of(step);
        }
        return of(MAX);
    }

    /** The next step below the factor the pages have now. */
    public static Zoom out(double current) {
        for (int i = STEPS.length - 1; i >= 0; i--) {
            if (STEPS[i] < current * 0.99) return of(STEPS[i]);
        }
        return of(MIN);
    }

    public boolean fits() {
        return kind != Kind.FIXED;
    }

    /** As stored in the settings: {@code fit-page}, {@code fit-width} or the percentage. */
    public String key() {
        return switch (kind) {
            case FIT_PAGE -> "fit-page";
            case FIT_WIDTH -> "fit-width";
            case FIXED -> String.valueOf(Math.round(factor * 100));
        };
    }

    /** A stored zoom, or typed into the zoom box: "fit-page", "fit-width", "125", "125 %", "125%". */
    public static Optional<Zoom> parse(String text) {
        if (text == null) return Optional.empty();
        String s = text.strip().toLowerCase(java.util.Locale.ROOT);
        if (s.equals("fit-page")) return Optional.of(FIT_PAGE);
        if (s.equals("fit-width")) return Optional.of(FIT_WIDTH);
        s = s.replace("%", "").strip().replace(',', '.');
        try {
            double percent = Double.parseDouble(s);
            return percent > 0 && Double.isFinite(percent) ? Optional.of(of(percent / 100)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
