package eu.amadeos.apps.pdfviewer.domain;

import eu.amadeos.apps.pdfviewer.TestPdfs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfDocumentTest {
    @TempDir
    Path dir;

    @Test
    void readsPagesAndProperties() throws Exception {
        try (PdfDocument doc = PdfDocument.open(TestPdfs.sample(dir), null)) {
            assertEquals(3, doc.pageCount());
            assertEquals(595.27, doc.pageSizes().getFirst().width(), 0.01);
            assertEquals(841.89, doc.pageSizes().getFirst().height(), 0.01);
            assertEquals("A4", PaperNames.of(doc.pageSizes().getFirst()));
            DocumentProperties p = doc.properties();
            assertEquals(TestPdfs.TITLE, p.title());
            assertEquals("Anna Muster", p.author());
            assertEquals(3, p.pages());
            assertFalse(p.encrypted());
            assertEquals(Files.size(doc.file()), p.fileSize());
        }
    }

    @Test
    void textInReadingOrder() throws Exception {
        try (PdfDocument doc = PdfDocument.open(TestPdfs.sample(dir), null)) {
            assertEquals(List.of("Chapter One", "The quick brown fox", "jumps over the lazy dog.", "Go to page three",
                    "Visit the web site"), TestPdfs.lines(doc.text(0).text()));
            PageText text = doc.text(0);
            assertEquals(1, text.find("brown fox jumps").size());
            assertEquals("brown fox\njumps", text.text(text.find("brown fox jumps").getFirst()[0],
                    text.find("brown fox jumps").getFirst()[1]));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 90, 180, 270})
    void characterBoxesLieOnTheGlyphs(int rotate) throws Exception {
        try (PdfDocument doc = PdfDocument.open(TestPdfs.rotated(dir, rotate), null)) {
            PageSize size = doc.pageSizes().getFirst();
            assertEquals(rotate % 180 != 0, size.landscape());
            BufferedImage image = doc.render(0, 1);
            PageText text = doc.text(0);
            assertEquals("HELLO", text.text());
            assertInk(image, text.areas(0, 5), 1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 90})
    void userRotationTurnsPictureAndBoxesAlike(int rotate) throws Exception {
        try (PdfDocument doc = PdfDocument.open(TestPdfs.rotated(dir, rotate), null)) {
            PageSize shown = doc.pageSizes().getFirst();
            for (Rotation turn : Rotation.values()) {
                PageSize turned = turn.apply(shown);
                Rectangle full = new Rectangle(0, 0, (int) Math.round(turned.width()), (int) Math.round(turned.height()));
                BufferedImage image = doc.render(0, 1, turn, full);
                List<Rectangle2D> boxes = doc.text(0).areas(0, 5).stream().map(r -> turn.apply(r, shown)).toList();
                assertInk(image, boxes, 1);
            }
        }
    }

    // --8<-- [start:tiles-test]
    @Test
    void tilesMatchTheWholePicture() throws Exception {
        try (PdfDocument doc = PdfDocument.open(TestPdfs.rotated(dir, 0), null)) {
            BufferedImage whole = doc.render(0, 2, Rotation.RIGHT, new Rectangle(0, 0, 1684, 1191));
            BufferedImage tile = doc.render(0, 2, Rotation.RIGHT, new Rectangle(1024, 0, 660, 1024));
            int differences = 0;
            for (int y = 0; y < tile.getHeight(); y += 3) {
                for (int x = 0; x < tile.getWidth(); x += 3) {
                    if (Math.abs((whole.getRGB(1024 + x, y) & 0xff) - (tile.getRGB(x, y) & 0xff)) > 60) differences++;
                }
            }
            assertTrue(differences < 20, "differences: " + differences);
        }
    }
    // --8<-- [end:tiles-test]

    /** Nearly all dark pixels of the picture lie in the boxes, and every box holds some. */
    private static void assertInk(BufferedImage image, List<Rectangle2D> boxes, double scale) {
        int dark = 0;
        int inside = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xff) > 128) continue;
                dark++;
                for (Rectangle2D box : boxes) {
                    if (box.getX() * scale - 2 <= x && x <= box.getMaxX() * scale + 2 && box.getY() * scale - 2 <= y
                            && y <= box.getMaxY() * scale + 2) {
                        inside++;
                        break;
                    }
                }
            }
        }
        assertTrue(dark > 100, "no text drawn");
        assertTrue(inside >= dark * 0.98, inside + " of " + dark + " dark pixels in " + boxes);
    }

    @Test
    void linksAndTheirTargets() throws Exception {
        try (PdfDocument doc = PdfDocument.open(TestPdfs.sample(dir), null)) {
            List<PageLink> links = doc.links(0);
            assertEquals(2, links.size());
            PageLink toThree = links.get(0);
            assertEquals(2, toThree.target().orElseThrow().page());
            assertFalse(toThree.target().get().hasTop());
            // 595 - 70 ... y from the top: 841.89 - 611
            assertEquals(70, toThree.area().getX(), 0.01);
            assertEquals(841.89 - 611, toThree.area().getY(), 0.01);
            assertEquals("https://example.org/", links.get(1).uri().orElseThrow());
            assertTrue(doc.links(1).isEmpty());
        }
    }

    @Test
    void outline() throws Exception {
        try (PdfDocument doc = PdfDocument.open(TestPdfs.sample(dir), null)) {
            List<OutlineEntry> outline = doc.outline();
            assertEquals(List.of("Chapter One", "Chapter Two"), outline.stream().map(OutlineEntry::title).toList());
            assertEquals(Target.page(0).page(), outline.get(0).target().orElseThrow().page());
            Target two = outline.get(1).target().orElseThrow();
            assertEquals(1, two.page());
            assertEquals(841.89 - 760, two.top(), 0.01);
            assertEquals("Haystack", outline.get(1).children().getFirst().title());
        }
    }

    @Test
    void passwords() throws Exception {
        Path file = TestPdfs.encrypted(dir);
        assertFalse(assertThrows(PasswordException.class, () -> PdfDocument.open(file, null)).wrong());
        assertTrue(assertThrows(PasswordException.class, () -> PdfDocument.open(file, "guess")).wrong());
        try (PdfDocument doc = PdfDocument.open(file, "secret")) {
            assertTrue(doc.properties().encrypted());
            assertEquals("Top secret", doc.text(0).text());
        }
    }

    @Test
    void notAPdf() throws Exception {
        Path file = Files.writeString(dir.resolve("letter.pdf"), "Dear Anna,");
        assertThrows(IOException.class, () -> PdfDocument.open(file, null));
    }
}
