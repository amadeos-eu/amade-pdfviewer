package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.domain.Settings;
import eu.amadeos.apps.pdfviewer.domain.ViewMode;
import eu.amadeos.apps.pdfviewer.domain.Zoom;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** File, Edit, View, Go, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    private final Messages msg;
    private final MainPresenter presenter;
    private final JMenu recent;
    private final JCheckBoxMenuItem sidebar;
    private final JRadioButtonMenuItem pagesTab;
    private final JRadioButtonMenuItem contentsTab;
    private final Map<ViewMode, JRadioButtonMenuItem> modes = new EnumMap<>(ViewMode.class);
    private final JMenuItem back;
    private final JMenuItem forward;
    /** Items that need an open document. */
    private final List<JMenuItem> documentItems = new ArrayList<>();
    private boolean updating;

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales) {
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.presenter = presenter;
        setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        JMenu file = menu("menu.file");
        file.add(item("menu.file.open", key(KeyEvent.VK_O, CTRL), presenter::chooseAndOpen));
        recent = Mnemonics.apply(new JMenu(), msg.get("menu.file.recent"));
        recent.setName("menu.file.recent");
        file.add(recent);
        file.add(document(item("menu.file.reload", key(KeyEvent.VK_R, CTRL), presenter::reload)));
        file.addSeparator();
        file.add(document(item("menu.file.print", key(KeyEvent.VK_P, CTRL), presenter::print)));
        file.add(document(item("menu.file.properties", key(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK),
                presenter::properties)));
        file.addSeparator();
        file.add(item("menu.file.exit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        JMenu edit = menu("menu.edit");
        edit.add(document(item("menu.edit.copy", key(KeyEvent.VK_C, CTRL), presenter::copy)));
        edit.add(document(item("menu.edit.select-all", key(KeyEvent.VK_A, CTRL), presenter::selectAll)));
        edit.addSeparator();
        edit.add(document(item("menu.edit.find", key(KeyEvent.VK_F, CTRL), presenter::find)));
        edit.add(document(item("menu.edit.find-next", key(KeyEvent.VK_F3, 0), presenter::findNext)));
        edit.add(document(item("menu.edit.find-previous", key(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK),
                presenter::findPrevious)));

        JMenu view = menu("menu.view");
        sidebar = Mnemonics.apply(new JCheckBoxMenuItem(), msg.get("menu.view.sidebar"));
        sidebar.setName("menu.view.sidebar");
        sidebar.setAccelerator(key(KeyEvent.VK_F9, 0));
        sidebar.addActionListener(e -> {
            if (!updating) presenter.setSidebar(sidebar.isSelected());
        });
        view.add(sidebar);
        ButtonGroup tabs = new ButtonGroup();
        pagesTab = radio("menu.view.pages", tabs, () -> presenter.showContents(false));
        contentsTab = radio("menu.view.contents", tabs, () -> presenter.showContents(true));
        view.add(pagesTab);
        view.add(contentsTab);
        view.addSeparator();
        ButtonGroup layouts = new ButtonGroup();
        int number = KeyEvent.VK_1;
        for (ViewMode mode : ViewMode.values()) {
            JRadioButtonMenuItem item = radio("menu.view." + mode.key(), layouts, () -> presenter.setViewMode(mode));
            item.setAccelerator(key(number++, CTRL));
            modes.put(mode, item);
            view.add(item);
        }
        view.addSeparator();
        view.add(document(item("menu.view.zoom-in", key(KeyEvent.VK_PLUS, CTRL), presenter::zoomIn)));
        view.add(document(item("menu.view.zoom-out", key(KeyEvent.VK_MINUS, CTRL), presenter::zoomOut)));
        view.add(document(item("menu.view.actual-size", key(KeyEvent.VK_0, CTRL), () -> presenter.setZoom(Zoom.of(1)))));
        view.add(document(item("menu.view.fit-page", null, () -> presenter.setZoom(Zoom.FIT_PAGE))));
        view.add(document(item("menu.view.fit-width", null, () -> presenter.setZoom(Zoom.FIT_WIDTH))));
        view.addSeparator();
        view.add(document(item("menu.view.rotate-left", key(KeyEvent.VK_LEFT, CTRL), presenter::rotateLeft)));
        view.add(document(item("menu.view.rotate-right", key(KeyEvent.VK_RIGHT, CTRL), presenter::rotateRight)));
        view.addSeparator();
        view.add(document(item("menu.view.presentation", key(KeyEvent.VK_F5, 0), presenter::presentation)));

        JMenu go = menu("menu.go");
        go.add(document(item("menu.go.previous", key(KeyEvent.VK_PAGE_UP, CTRL), presenter::previous)));
        go.add(document(item("menu.go.next", key(KeyEvent.VK_PAGE_DOWN, CTRL), presenter::next)));
        go.add(document(item("menu.go.first", key(KeyEvent.VK_HOME, CTRL), presenter::first)));
        go.add(document(item("menu.go.last", key(KeyEvent.VK_END, CTRL), presenter::last)));
        go.add(document(item("menu.go.page", key(KeyEvent.VK_G, CTRL), presenter::goToPage)));
        go.addSeparator();
        back = item("menu.go.back", key(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), presenter::back);
        forward = item("menu.go.forward", key(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), presenter::forward);
        go.add(back);
        go.add(forward);

        JMenu options = menu("menu.options");
        if (locales != null) options.add(languageMenu(locales));

        JMenu help = menu("menu.help");
        help.add(item("menu.help.about", null, presenter::about));
        showRecent(List.of());
    }

    void update(Settings settings) {
        updating = true;
        try {
            sidebar.setSelected(settings.sidebar());
            (settings.contents() ? contentsTab : pagesTab).setSelected(true);
            modes.get(settings.view()).setSelected(true);
        } finally {
            updating = false;
        }
    }

    void setDocumentOpen(boolean open, boolean hasContents) {
        for (JMenuItem item : documentItems) item.setEnabled(open);
        contentsTab.setEnabled(open && hasContents);
    }

    void showHistory(boolean canGoBack, boolean canGoForward) {
        back.setEnabled(canGoBack);
        forward.setEnabled(canGoForward);
    }

    // --8<-- [start:recent]
    /** File → Open Recent: the files with a number as mnemonic, the folder as tooltip. */
    void showRecent(List<Path> files) {
        recent.removeAll();
        if (files.isEmpty()) {
            JMenuItem none = new JMenuItem(msg.get("menu.file.recent.empty"));
            none.setEnabled(false);
            recent.add(none);
            return;
        }
        int number = 1;
        for (Path file : files) {
            String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
            JMenuItem item = new JMenuItem(number <= 9 ? number + " " + name : name);
            if (number <= 9) item.setMnemonic(KeyEvent.VK_0 + number);
            item.setToolTipText(file.toString());
            item.addActionListener(e -> presenter.openRecent(file));
            recent.add(item);
            number++;
        }
        recent.addSeparator();
        recent.add(item("menu.file.recent.clear", null, presenter::clearRecent));
    }
    // --8<-- [end:recent]

    JMenu recentMenu() {
        return recent;
    }

    JRadioButtonMenuItem modeItem(ViewMode mode) {
        return modes.get(mode);
    }

    JCheckBoxMenuItem sidebarItem() {
        return sidebar;
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem document(JMenuItem item) {
        documentItems.add(item);
        return item;
    }

    private JRadioButtonMenuItem radio(String key, ButtonGroup group, Runnable action) {
        JRadioButtonMenuItem item = Mnemonics.apply(new JRadioButtonMenuItem(), msg.get(key));
        item.setName(key);
        item.addActionListener(e -> {
            if (!updating) action.run();
        });
        group.add(item);
        return item;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        return item;
    }
}
