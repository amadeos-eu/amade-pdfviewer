package eu.amadeos.apps.pdfviewer.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.pdfviewer.domain.DocumentThread;
import eu.amadeos.apps.pdfviewer.repository.PdfViewerPreferences;
import eu.amadeos.apps.pdfviewer.repository.ReadingPositions;
import eu.amadeos.apps.pdfviewer.ui.main.MainPresenter;
import eu.amadeos.apps.pdfviewer.ui.main.MainWindow;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import java.util.Locale;

/**
 * amade-pdfviewer bindings. Theme, language and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class PdfViewerModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.pdfviewer.messages";

    @Override
    protected void configure() {
        bind(PdfViewerPreferences.class).in(Scopes.SINGLETON);
        bind(ReadingPositions.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
    }

    /** The one thread that reads and renders the documents; results come back on the Swing event thread. */
    @Provides
    @Singleton
    DocumentThread documentThread(EdtExecutor edt) {
        return new DocumentThread(edt);
    }

    @Provides
    @Singleton
    MainPresenter presenter(MainWindow window, Messages msg, LocaleSettings locales, PdfViewerPreferences preferences,
                            ReadingPositions positions, DocumentThread thread, DesktopLauncher launcher) {
        return new MainPresenter(window, msg, locales, preferences, positions, thread, launcher, true,
                () -> System.exit(0));
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(PdfViewerModule.class, MESSAGES, locale);
    }
}
