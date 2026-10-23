package eu.amadeos.apps.pdfviewer.domain;

/** The name of a usual paper size, for the status bar and the properties: "A4", "Letter". */
public final class PaperNames {
    private static final Object[][] PAPERS = {
            {"A0", 841.0, 1189.0}, {"A1", 594.0, 841.0}, {"A2", 420.0, 594.0}, {"A3", 297.0, 420.0},
            {"A4", 210.0, 297.0}, {"A5", 148.0, 210.0}, {"A6", 105.0, 148.0}, {"B4", 250.0, 353.0},
            {"B5", 176.0, 250.0}, {"Letter", 215.9, 279.4}, {"Legal", 215.9, 355.6}, {"Tabloid", 279.4, 431.8}};

    private PaperNames() {
    }

    /** The paper's name if the page has a usual size within 2 mm, in either orientation; else empty. */
    public static String of(PageSize size) {
        double w = Math.min(size.widthMm(), size.heightMm());
        double h = Math.max(size.widthMm(), size.heightMm());
        for (Object[] paper : PAPERS) {
            if (Math.abs(w - (double) paper[1]) <= 2 && Math.abs(h - (double) paper[2]) <= 2) return (String) paper[0];
        }
        return "";
    }
}
