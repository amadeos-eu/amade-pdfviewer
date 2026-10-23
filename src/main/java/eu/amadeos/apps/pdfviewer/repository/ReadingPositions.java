package eu.amadeos.apps.pdfviewer.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.pdfviewer.domain.ReadingPosition;
import eu.amadeos.apps.pdfviewer.domain.Rotation;
import eu.amadeos.apps.pdfviewer.domain.Zoom;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.LongSupplier;

/**
 * Where the reader stopped in each document, in {@code ~/.config/amadeos/pdfviewer/positions.properties}: one
 * line per file with page, offset on the page, zoom, rotation and the time it was saved. Only the
 * {@value #LIMIT} most recent documents are kept.
 */
public class ReadingPositions {
    public static final String FILE = "positions.properties";
    public static final int LIMIT = 300;

    private final Path file;
    private final LongSupplier clock;
    private final Properties properties;

    @Inject
    public ReadingPositions(AppDirectories directories) {
        this(directories.configFile(FILE), System::currentTimeMillis);
    }

    public ReadingPositions(Path file, LongSupplier clock) {
        this.file = Objects.requireNonNull(file, "file");
        this.clock = clock;
        this.properties = PropertiesFile.load(file);
    }

    // --8<-- [start:get]
    /** The position saved for the file; page 1 of a document opened for the first time is empty. */
    public Optional<ReadingPosition> get(Path document) {
        String value = properties.getProperty(key(document));
        if (value == null) return Optional.empty();
        String[] parts = value.split(";");
        if (parts.length < 5) return Optional.empty();
        try {
            Optional<Zoom> zoom = Zoom.parse(parts[2]);
            if (zoom.isEmpty()) return Optional.empty();
            return Optional.of(new ReadingPosition(Integer.parseInt(parts[0]), Double.parseDouble(parts[1]),
                    zoom.get(), Rotation.of(Integer.parseInt(parts[3]))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
    // --8<-- [end:get]

    public void put(Path document, ReadingPosition position) {
        properties.setProperty(key(document), String.format(Locale.ROOT, "%d;%.4f;%s;%d;%d", position.page(),
                position.offset(), position.zoom().key(), position.rotation().degrees(), clock.getAsLong()));
        if (properties.size() > LIMIT) {
            List<String> oldest = properties.stringPropertyNames().stream()
                    .sorted(Comparator.comparingLong(this::savedAt)).limit(properties.size() - LIMIT).toList();
            oldest.forEach(properties::remove);
        }
        PropertiesFile.store(file, properties, "amade-pdfviewer reading positions");
    }

    private long savedAt(String key) {
        String[] parts = properties.getProperty(key, "").split(";");
        try {
            return parts.length >= 5 ? Long.parseLong(parts[4]) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String key(Path document) {
        return document.toAbsolutePath().normalize().toString();
    }
}
