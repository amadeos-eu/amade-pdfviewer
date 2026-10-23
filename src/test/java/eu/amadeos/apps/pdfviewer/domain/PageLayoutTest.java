package eu.amadeos.apps.pdfviewer.domain;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageLayoutTest {
    private static final List<PageSize> FIVE = Collections.nCopies(5, new PageSize(600, 800));
    private static final Dimension VIEW = new Dimension(1000, 700);

    @Test
    void continuousPagesOneBelowTheOther() {
        PageLayout layout = PageLayout.of(FIVE, ViewMode.CONTINUOUS, 1, 0, VIEW);
        assertEquals(new Rectangle(200, 12, 600, 800), layout.page(0));
        assertEquals(new Rectangle(200, 12 + 800 + 12, 600, 800), layout.page(1));
        assertEquals(new Dimension(1000, 5 * 800 + 4 * 12 + 24), layout.size());
        assertEquals(1, layout.pageAt(500, 900));
        assertEquals(-1, layout.pageAt(10, 900));
    }

    @Test
    void singleShowsOnlyTheCurrentPage() {
        PageLayout layout = PageLayout.of(FIVE, ViewMode.SINGLE, 0.5, 3, VIEW);
        assertNull(layout.page(0));
        assertEquals(new Rectangle(350, 12, 300, 400), layout.page(3));
        assertEquals(VIEW, layout.size());
    }

    @Test
    void twoPagesLikeABook() {
        PageLayout layout = PageLayout.of(FIVE, ViewMode.TWO_PAGES, 0.5, 0, VIEW);
        // page 1 alone on the right half, then 2|3, 4|5
        assertEquals(506, layout.page(0).x);
        assertEquals(new Rectangle(194, 424, 300, 400), layout.page(1));
        assertEquals(new Rectangle(506, 424, 300, 400), layout.page(2));
        assertArrayEquals(new int[]{3, 4}, layout.row(4));
        assertEquals(3, layout.nextRow(1));
        assertEquals(-1, layout.nextRow(4));
    }

    @Test
    void fits() {
        double width = PageLayout.scale(Zoom.FIT_WIDTH, FIVE, ViewMode.CONTINUOUS, 0, VIEW);
        assertEquals((1000 - 24 - 1) / 600.0, width, 1e-9);
        double page = PageLayout.scale(Zoom.FIT_PAGE, FIVE, ViewMode.CONTINUOUS, 0, VIEW);
        assertEquals((700 - 24 - 1) / 800.0, page, 1e-9);
        double two = PageLayout.scale(Zoom.FIT_WIDTH, FIVE, ViewMode.TWO_PAGES, 2, VIEW);
        assertEquals((1000 - 24 - 12 - 2) / 1200.0, two, 1e-9);
        // The first page stands alone, but gets the same zoom as the pairs after it.
        assertEquals(two, PageLayout.scale(Zoom.FIT_WIDTH, FIVE, ViewMode.TWO_PAGES, 0, VIEW), 1e-9);
        assertEquals(1.5 * PageLayout.PIXELS_PER_POINT, PageLayout.scale(Zoom.of(1.5), FIVE, ViewMode.SINGLE, 0, VIEW));
    }

    @Test
    void currentPageFillsMostOfTheWindow() {
        PageLayout layout = PageLayout.of(FIVE, ViewMode.CONTINUOUS, 1, 0, VIEW);
        assertEquals(0, layout.currentPage(new Rectangle(0, 0, 1000, 700), 0));
        assertEquals(1, layout.currentPage(new Rectangle(0, 500, 1000, 700), 0));
        assertEquals(2, layout.currentPage(new Rectangle(0, 1700, 1000, 700), 0));
    }
}
