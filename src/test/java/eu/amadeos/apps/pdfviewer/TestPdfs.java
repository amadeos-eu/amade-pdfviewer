package eu.amadeos.apps.pdfviewer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/** Small PDF files made with PDFBox for the tests. */
public final class TestPdfs {
    public static final String TITLE = "A Test Document";

    private TestPdfs() {
    }

    /**
     * Three A4 pages: page 1 with a heading at (72, 750), two lines of text, a link to page 3 and one to a web
     * address; page 2 with the word "Needle"; page 3 with "needle" twice. An outline with two chapters.
     */
    public static Path sample(Path dir) {
        Path file = dir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage one = page(doc, 0);
            PDPage two = page(doc, 0);
            PDPage three = page(doc, 0);
            text(doc, one, 72, 750, 24, "Chapter One");
            text(doc, one, 72, 700, 12, "The quick brown fox");
            text(doc, one, 72, 685, 12, "jumps over the lazy dog.");
            text(doc, one, 72, 600, 12, "Go to page three");
            text(doc, one, 72, 580, 12, "Visit the web site");
            text(doc, two, 72, 750, 24, "Chapter Two");
            text(doc, two, 72, 700, 12, "A Needle in a haystack.");
            text(doc, three, 72, 700, 12, "needle and needle");

            PDAnnotationLink toThree = new PDAnnotationLink();
            toThree.setRectangle(new PDRectangle(70, 595, 100, 16));
            PDActionGoTo goTo = new PDActionGoTo();
            PDPageFitDestination fit = new PDPageFitDestination();
            fit.setPage(three);
            goTo.setDestination(fit);
            toThree.setAction(goTo);
            one.getAnnotations().add(toThree);
            PDAnnotationLink web = new PDAnnotationLink();
            web.setRectangle(new PDRectangle(70, 575, 100, 16));
            PDActionURI uri = new PDActionURI();
            uri.setURI("https://example.org/");
            web.setAction(uri);
            one.getAnnotations().add(web);

            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);
            outline.addLast(chapter("Chapter One", one, -1));
            PDOutlineItem second = chapter("Chapter Two", two, 760);
            second.addLast(chapter("Haystack", two, 710));
            outline.addLast(second);

            doc.getDocumentInformation().setTitle(TITLE);
            doc.getDocumentInformation().setAuthor("Anna Muster");
            doc.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    /**
     * One A4 page turned by {@code /Rotate}, with "HELLO" in 40 pt written so that it reads from left to right
     * on the turned page, as in scans and landscape pages.
     */
    public static Path rotated(Path dir, int rotate) {
        Path file = dir.resolve("rotated-" + rotate + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = page(doc, rotate);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 40);
                cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(rotate), 300, 400));
                cs.showText("HELLO");
                cs.endText();
            }
            doc.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    /** A page with text, protected with the user password "secret". */
    public static Path encrypted(Path dir) {
        Path file = dir.resolve("encrypted.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = page(doc, 0);
            text(doc, page, 72, 700, 12, "Top secret");
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    /** {@code count} pages of A4, each saying "Page n". */
    public static Path pages(Path dir, String name, int count) {
        Path file = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= count; i++) text(doc, page(doc, 0), 72, 700, 18, "Page " + i);
            doc.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static PDPage page(PDDocument doc, int rotate) {
        PDPage page = new PDPage(PDRectangle.A4);
        page.setRotation(rotate);
        doc.addPage(page);
        return page;
    }

    private static void text(PDDocument doc, PDPage page, float x, float y, float size, String text) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), size);
            cs.newLineAtOffset(x, y);
            cs.showText(text);
            cs.endText();
        }
    }

    private static PDOutlineItem chapter(String title, PDPage page, int top) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        if (top >= 0) {
            PDPageXYZDestination destination = new PDPageXYZDestination();
            destination.setPage(page);
            destination.setTop(top);
            item.setDestination(destination);
        } else {
            PDPageFitDestination destination = new PDPageFitDestination();
            destination.setPage(page);
            item.setDestination(destination);
        }
        return item;
    }

    public static List<String> lines(String text) {
        return List.of(text.split("\n"));
    }
}
