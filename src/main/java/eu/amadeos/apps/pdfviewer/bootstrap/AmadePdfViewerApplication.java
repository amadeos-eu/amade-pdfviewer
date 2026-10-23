package eu.amadeos.apps.pdfviewer.bootstrap;

import eu.amadeos.apps.pdfviewer.ui.main.MainPresenter;
import eu.amadeos.apps.pdfviewer.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.commons.config.XdgDirectories;

import java.util.List;

public final class AmadePdfViewerApplication {
    public static final AppInfo APP = new AppInfo("amade-pdfviewer", "PDF Viewer", "pdfviewer");

    private AmadePdfViewerApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadePdfViewerApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-pdfviewer " + version());
            return;
        }
        // PDFBox keeps the list of system fonts it scanned in a cache file, by default in the home folder.
        if (System.getProperty("pdfbox.fontcache") == null) {
            System.setProperty("pdfbox.fontcache", XdgDirectories.system().appCache(APP.configName()).toString());
        }
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(args));
        }, new PdfViewerModule());
    }
    // --8<-- [end:main]
}
