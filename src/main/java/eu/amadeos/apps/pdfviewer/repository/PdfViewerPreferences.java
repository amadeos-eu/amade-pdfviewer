package eu.amadeos.apps.pdfviewer.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.pdfviewer.domain.Settings;
import eu.amadeos.apps.pdfviewer.domain.ViewMode;
import eu.amadeos.apps.pdfviewer.domain.Zoom;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Settings of amade-pdfviewer in {@code ~/.config/amadeos/pdfviewer/pdfviewer.properties}: view, zoom, sidebar,
 * the recently opened files and the folder of the last Open. Each change is written at once; unknown or broken
 * values fall back to the defaults.
 */
public class PdfViewerPreferences {
    public static final String FILE = "pdfviewer.properties";
    public static final int RECENT = 10;

    private final Path file;
    private final Properties properties;

    @Inject
    public PdfViewerPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public PdfViewerPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public Settings settings() {
        Settings d = Settings.DEFAULT;
        return new Settings(ViewMode.forKey(properties.getProperty("view", "")).orElse(d.view()),
                Zoom.parse(properties.getProperty("zoom")).orElse(d.zoom()), flag("sidebar", d.sidebar()),
                "contents".equals(properties.getProperty("sidebar-tab")));
    }

    private boolean flag(String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : !"false".equals(value.strip());
    }

    public void setSettings(Settings settings) {
        properties.setProperty("view", settings.view().key());
        properties.setProperty("zoom", settings.zoom().key());
        properties.setProperty("sidebar", String.valueOf(settings.sidebar()));
        properties.setProperty("sidebar-tab", settings.contents() ? "contents" : "pages");
        save();
    }

    /** The recently opened files, the last one first. */
    public List<Path> recentFiles() {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < RECENT; i++) {
            path(properties.getProperty("recent." + i)).ifPresent(files::add);
        }
        return files;
    }

    /** Puts the file at the top of the recent files. */
    public void addRecent(Path opened) {
        Path absolute = opened.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>(recentFiles());
        files.remove(absolute);
        files.addFirst(absolute);
        setRecent(files);
    }

    public void clearRecent() {
        setRecent(List.of());
    }

    private void setRecent(List<Path> files) {
        for (int i = 0; i < RECENT; i++) {
            if (i < files.size()) properties.setProperty("recent." + i, files.get(i).toString());
            else properties.remove("recent." + i);
        }
        save();
    }

    /** The folder of the last Open, if one was remembered. */
    public Optional<Path> lastFolder() {
        return path(properties.getProperty("last-folder"));
    }

    public void setLastFolder(Path folder) {
        properties.setProperty("last-folder", folder.toAbsolutePath().normalize().toString());
        save();
    }

    private static Optional<Path> path(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(Path.of(value.strip()));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private void save() {
        PropertiesFile.store(file, properties, "amade-pdfviewer");
    }
}
