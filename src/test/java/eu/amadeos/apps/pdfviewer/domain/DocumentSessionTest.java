package eu.amadeos.apps.pdfviewer.domain;

import eu.amadeos.apps.pdfviewer.TestPdfs;
import eu.amadeos.apps.pdfviewer.domain.DocumentThread.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentSessionTest {
    @TempDir
    Path dir;

    private final List<String> events = new CopyOnWriteArrayList<>();

    private final DocumentSession.Listener listener = new DocumentSession.Listener() {
        @Override
        public void pageReady(int page) {
            events.add("page " + page);
        }

        @Override
        public void thumbnailReady(int page) {
            events.add("thumbnail " + page);
        }

        @Override
        public void textReady(int page) {
            events.add("text " + page);
        }
    };

    @Test
    void wholePagesWhenSmallTilesWhenLarge() {
        assertEquals(List.of(new Rectangle(0, 0, 794, 1123)), DocumentSession.tiles(new Dimension(794, 1123)));
        List<Rectangle> tiles = DocumentSession.tiles(new Dimension(4000, 5000));
        assertEquals(4 * 5, tiles.size());
        assertEquals(new Rectangle(3072, 4096, 928, 904), tiles.getLast());
    }

    @Test
    void picturesTextsAndThumbnailsComeLater() throws Exception {
        // The results are handed over on the calling thread here, so the session sees them at once.
        try (DocumentThread thread = new DocumentThread(Runnable::run)) {
            DocumentSession session = new DocumentSession(PdfDocument.open(TestPdfs.sample(dir), null), thread, listener);
            Rectangle whole = new Rectangle(0, 0, 595, 842);
            assertNull(session.tile(0, 1, Rotation.NONE, whole, Priority.VISIBLE));
            assertNull(session.text(0));
            assertNull(session.thumbnail(1, Rotation.NONE));
            thread.await();
            BufferedImage page = session.tile(0, 1, Rotation.NONE, whole, Priority.VISIBLE);
            assertNotNull(page);
            assertEquals(595, page.getWidth());
            assertNotNull(session.preview(0, Rotation.NONE));
            assertNull(session.preview(0, Rotation.RIGHT));
            assertTrue(session.text(0).text().startsWith("Chapter One"));
            assertEquals(2, session.links(0).size());
            BufferedImage thumbnail = session.thumbnail(1, Rotation.NONE);
            assertTrue(thumbnail.getWidth() <= DocumentSession.THUMBNAIL_WIDTH && thumbnail.getHeight() > thumbnail.getWidth());
            assertTrue(events.containsAll(List.of("page 0", "text 0", "thumbnail 1")), events.toString());
            session.close();
            thread.await();
            assertNull(session.tile(1, 1, Rotation.NONE, whole, Priority.VISIBLE));
        }
    }
}
