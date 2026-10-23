package eu.amadeos.apps.pdfviewer.domain;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTextTest {
    /** "ab cd" on one line at y 10, "ef" below at y 30; each character 10 wide and 12 high. */
    private static PageText sample() {
        PageText.Builder b = new PageText.Builder();
        b.separator('\n');
        b.add('a', 0, 10, 10, 12).add('b', 10, 10, 10, 12).separator(' ');
        b.add('c', 30, 10, 10, 12).add('d', 40, 10, 10, 12).separator(' ').separator('\n');
        b.add('e', 0, 30, 10, 12).add('f', 10, 30, 10, 12).separator('\n');
        return b.build();
    }

    @Test
    void separatorsAreSingleAndNotAtTheEnds() {
        assertEquals("ab cd\nef", sample().text());
    }

    @Test
    void findIgnoresCaseAndLineBreaks() {
        PageText text = sample();
        assertEquals(1, text.find("CD").size());
        assertArrayEquals(new int[]{3, 8}, text.find("cd  ef").getFirst());
        assertTrue(text.find("x").isEmpty());
        assertTrue(text.find("  ").isEmpty());
        assertTrue(text.find("(").isEmpty());
    }

    @Test
    void areasPerLine() {
        List<Rectangle2D> areas = sample().areas(1, 7);
        assertEquals(2, areas.size());
        assertEquals(new Rectangle2D.Float(10, 10, 40, 12), areas.get(0));
        assertEquals(new Rectangle2D.Float(0, 30, 10, 12), areas.get(1));
    }

    @Test
    void caretAndWords() {
        PageText text = sample();
        assertEquals(0, text.caretAt(new Point2D.Double(2, 15)));
        assertEquals(1, text.caretAt(new Point2D.Double(8, 15)));
        // right of the line end: after "d"
        assertEquals(5, text.caretAt(new Point2D.Double(300, 14)));
        // between the lines, nearer the second
        assertEquals(8, text.caretAt(new Point2D.Double(16, 29)));
        assertEquals(3, text.indexAt(new Point2D.Double(35, 15)));
        assertEquals(-1, text.indexAt(new Point2D.Double(25, 15)));
        assertArrayEquals(new int[]{3, 5}, text.wordAt(4));
        assertEquals("b cd\ne", text.text(7, 1));
        assertEquals(-1, PageText.EMPTY.caretAt(new Point2D.Double(1, 1)));
    }
}
