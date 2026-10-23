package eu.amadeos.apps.pdfviewer.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoomTest {
    @Test
    void steps() {
        assertEquals(1.25, Zoom.in(1).factor());
        assertEquals(0.75, Zoom.out(1).factor());
        assertEquals(1.25, Zoom.in(1.1).factor());
        assertEquals(1, Zoom.out(1.1).factor());
        assertEquals(Zoom.MAX, Zoom.in(8).factor());
        assertEquals(Zoom.MIN, Zoom.out(0.1).factor());
    }

    @Test
    void keysAndTyping() {
        assertEquals(Optional.of(Zoom.FIT_PAGE), Zoom.parse("fit-page"));
        assertEquals(Optional.of(Zoom.of(1.5)), Zoom.parse(" 150 % "));
        assertEquals(Optional.of(Zoom.of(1.255)), Zoom.parse("125,5"));
        assertEquals(Optional.empty(), Zoom.parse("big"));
        assertEquals(Optional.empty(), Zoom.parse("-5"));
        assertEquals(Zoom.MAX, Zoom.parse("5000").orElseThrow().factor());
        assertEquals("125", Zoom.of(1.25).key());
        assertEquals("fit-width", Zoom.FIT_WIDTH.key());
    }
}
