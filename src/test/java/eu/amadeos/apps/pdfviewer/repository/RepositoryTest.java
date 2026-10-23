package eu.amadeos.apps.pdfviewer.repository;

import eu.amadeos.apps.pdfviewer.domain.ReadingPosition;
import eu.amadeos.apps.pdfviewer.domain.Rotation;
import eu.amadeos.apps.pdfviewer.domain.Settings;
import eu.amadeos.apps.pdfviewer.domain.ViewMode;
import eu.amadeos.apps.pdfviewer.domain.Zoom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryTest {
    @TempDir
    Path dir;

    @Test
    void settingsSurviveAndBrokenValuesFallBack() throws Exception {
        Path file = dir.resolve("pdfviewer.properties");
        new PdfViewerPreferences(file).setSettings(new Settings(ViewMode.TWO_PAGES, Zoom.of(1.25), false, true));
        assertEquals(new Settings(ViewMode.TWO_PAGES, Zoom.of(1.25), false, true),
                new PdfViewerPreferences(file).settings());
        Files.writeString(file, "view=sideways\nzoom=big\n");
        assertEquals(Settings.DEFAULT, new PdfViewerPreferences(file).settings());
    }

    @Test
    void recentFilesNewestFirstWithoutDoubles() {
        PdfViewerPreferences preferences = new PdfViewerPreferences(dir.resolve("p.properties"));
        for (int i = 0; i < 12; i++) preferences.addRecent(dir.resolve(i + ".pdf"));
        preferences.addRecent(dir.resolve("5.pdf"));
        List<Path> recent = new PdfViewerPreferences(dir.resolve("p.properties")).recentFiles();
        assertEquals(PdfViewerPreferences.RECENT, recent.size());
        assertEquals(dir.resolve("5.pdf"), recent.getFirst());
        assertEquals(dir.resolve("11.pdf"), recent.get(1));
        assertEquals(1, recent.stream().filter(p -> p.endsWith("5.pdf")).count());
        preferences.clearRecent();
        assertEquals(List.of(), preferences.recentFiles());
    }

    @Test
    void readingPositionsKeepOnlyTheNewest() {
        AtomicLong clock = new AtomicLong();
        Path file = dir.resolve("positions.properties");
        ReadingPositions positions = new ReadingPositions(file, clock::incrementAndGet);
        ReadingPosition position = new ReadingPosition(4, 0.25, Zoom.FIT_PAGE, Rotation.LEFT);
        positions.put(dir.resolve("first.pdf"), position);
        for (int i = 0; i < ReadingPositions.LIMIT; i++) {
            positions.put(dir.resolve(i + ".pdf"), new ReadingPosition(i, 0, Zoom.of(1), Rotation.NONE));
        }
        ReadingPositions again = new ReadingPositions(file, clock::incrementAndGet);
        assertEquals(Optional.empty(), again.get(dir.resolve("first.pdf")));
        assertEquals(7, again.get(dir.resolve("7.pdf")).orElseThrow().page());
        again.put(dir.resolve("first.pdf"), position);
        assertEquals(Optional.of(position), new ReadingPositions(file, clock::incrementAndGet).get(dir.resolve("first.pdf")));
    }
}
