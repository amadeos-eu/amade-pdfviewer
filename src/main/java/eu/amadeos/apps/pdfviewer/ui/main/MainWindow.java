package eu.amadeos.apps.pdfviewer.ui.main;

import com.google.inject.Inject;
import eu.amadeos.apps.pdfviewer.bootstrap.AmadePdfViewerApplication;
import eu.amadeos.apps.pdfviewer.domain.DocumentProperties;
import eu.amadeos.apps.pdfviewer.domain.DocumentSession;
import eu.amadeos.apps.pdfviewer.domain.OutlineEntry;
import eu.amadeos.apps.pdfviewer.domain.PageLink;
import eu.amadeos.apps.pdfviewer.domain.Rotation;
import eu.amadeos.apps.pdfviewer.domain.Settings;
import eu.amadeos.apps.pdfviewer.domain.ViewMode;
import eu.amadeos.apps.pdfviewer.domain.Zoom;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.commons.swing.StandardGlyph;
import eu.amadeos.commons.swing.ToolButtons;
import eu.amadeos.commons.swing.ToolIcon;

import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.awt.print.PrinterJob;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The window: toolbar, the sidebar with thumbnails or contents, the pages and the status bar. Documents can
 * also be dropped onto it.
 */
public final class MainWindow extends JFrame implements MainView {
    private final Messages msg;
    private final LocaleSettings locales;
    private final PageView pages;
    private final JScrollPane scroll;
    private final Sidebar sidebar;
    private final JTextField pageField = new JTextField(4);
    private final JLabel pageCount = new JLabel();
    private final JComboBox<String> zoomBox = new JComboBox<>();
    private final JTextField searchField = new JTextField(14);
    private final JLabel searchLabel = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JLabel note = new JLabel(" ");
    private final Timer noteTimer = new Timer(5000, e -> note.setText(" "));
    private final List<Action> documentActions = new ArrayList<>();
    private final Action sidebarAction;
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private Settings settings = Settings.DEFAULT;
    private DocumentSession session;
    private PresentationWindow presentation;
    private Zoom zoom = Zoom.FIT_WIDTH;
    private String hover;
    private boolean updating;

    @Inject
    public MainWindow(Messages msg, LocaleSettings locales) {
        super(msg.get("window.title"));
        EdtExecutor.requireEdt();
        this.msg = msg;
        this.locales = locales;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        java.net.URL icon = getClass().getResource("/icons/amade-pdfviewer.png");
        if (icon != null) setIconImage(new ImageIcon(icon).getImage());
        pages = new PageView(new PageView.Callbacks() {
            @Override
            public void pageShown(int page) {
                if (presenter != null) presenter.pageShown(page);
            }

            @Override
            public void follow(PageLink link) {
                if (presenter != null) presenter.follow(link);
            }

            @Override
            public void hover(String link) {
                showHover(link);
            }

            @Override
            public void wheelZoom(boolean in) {
                if (presenter == null) return;
                if (in) presenter.zoomIn();
                else presenter.zoomOut();
            }
        });
        pages.setEmptyText(msg.get("empty.title"), msg.get("empty.hint"));
        scroll = new JScrollPane(pages);
        scroll.setName("scroll");
        scroll.setBorder(AmadeUI.sunken());
        scroll.getViewport().setBackground(PageView.DESK);
        scroll.getViewport().addChangeListener(e -> pages.viewportMoved());
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                pages.viewportResized();
            }
        });
        sidebar = new Sidebar(msg);
        sidebarAction = action("tool.sidebar", Glyph.SIDEBAR, () -> presenter.toggleSidebar());
        sidebarAction.putValue(Action.SELECTED_KEY, true);
    }

    public void bind(MainPresenter newPresenter) {
        EdtExecutor.requireEdt();
        presenter = newPresenter;
        menuBar = new MainMenuBar(presenter, msg, locales);
        setJMenuBar(menuBar);
        sidebar.onPage(page -> presenter.goTo(page));
        sidebar.onTarget(target -> presenter.follow(target));
        sidebar.onTab(contents -> presenter.showContents(contents));

        JPanel centre = new JPanel(new BorderLayout(4, 0));
        centre.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        centre.add(sidebar, BorderLayout.WEST);
        centre.add(scroll, BorderLayout.CENTER);
        add(toolBar(), BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);
        keys();
        TransferHandler drop = new FileDrop();
        pages.setTransferHandler(drop);
        sidebar.setTransferHandler(drop);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                pages.requestFocusInWindow();
            }
        });
        setSize(Math.round(1100 * AmadeUI.fontScale()), Math.round(820 * AmadeUI.fontScale()));
        setMinimumSize(new Dimension(520, 380));
        setLocationRelativeTo(null);
        noteTimer.setRepeats(false);
        setDocumentOpen(false, false);
    }

    // ---------------------------------------------------------------- toolbar and status bar

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(ToolButtons.toggle(sidebarAction));
        bar.addSeparator();
        bar.add(ToolButtons.button(action("tool.open", StandardGlyph.OPEN, presenter::chooseAndOpen)));
        bar.add(ToolButtons.button(document(action("tool.print", StandardGlyph.PRINT, presenter::print))));
        bar.addSeparator();
        bar.add(ToolButtons.button(document(action("tool.previous", Glyph.PREVIOUS_PAGE, presenter::previous))));
        bar.add(ToolButtons.button(document(action("tool.next", Glyph.NEXT_PAGE, presenter::next))));
        bar.add(Box.createHorizontalStrut(4));
        pageField.setName("page");
        pageField.setHorizontalAlignment(JTextField.RIGHT);
        pageField.setToolTipText(msg.get("tool.page"));
        pageField.setMaximumSize(pageField.getPreferredSize());
        pageField.addActionListener(e -> {
            if (presenter.goToTyped(pageField.getText())) pages.requestFocusInWindow();
        });
        bar.add(pageField);
        pageCount.setName("page.count");
        bar.add(pageCount);
        bar.addSeparator();
        bar.add(ToolButtons.button(document(action("tool.zoom-out", StandardGlyph.ZOOM_OUT, presenter::zoomOut))));
        bar.add(ToolButtons.button(document(action("tool.zoom-in", StandardGlyph.ZOOM_IN, presenter::zoomIn))));
        bar.add(Box.createHorizontalStrut(4));
        zoomBox.setName("zoom");
        zoomBox.setEditable(true);
        zoomBox.setToolTipText(msg.get("tool.zoom"));
        zoomBox.addItem(msg.get("zoom.fit-page"));
        zoomBox.addItem(msg.get("zoom.fit-width"));
        for (double step : Zoom.STEPS) {
            if (step >= 0.25 && step <= 4) zoomBox.addItem(percent(step));
        }
        zoomBox.setPrototypeDisplayValue(msg.get("zoom.fit-width") + "MM");
        zoomBox.setMaximumSize(new Dimension(zoomBox.getPreferredSize().width + 10, zoomBox.getPreferredSize().height));
        zoomBox.addActionListener(e -> {
            if (updating || !"comboBoxEdited".equals(e.getActionCommand()) && zoomBox.isPopupVisible()) return;
            Object chosen = zoomBox.getSelectedItem();
            if (chosen == null) return;
            String text = chosen.toString();
            if (text.equals(msg.get("zoom.fit-page"))) presenter.setZoom(Zoom.FIT_PAGE);
            else if (text.equals(msg.get("zoom.fit-width"))) presenter.setZoom(Zoom.FIT_WIDTH);
            else presenter.setZoomTyped(text);
            pages.requestFocusInWindow();
        });
        bar.add(zoomBox);
        bar.addSeparator();
        bar.add(ToolButtons.button(document(action("tool.rotate-left", Glyph.ROTATE_LEFT, presenter::rotateLeft))));
        bar.add(ToolButtons.button(document(action("tool.rotate-right", Glyph.ROTATE_RIGHT, presenter::rotateRight))));
        bar.add(Box.createHorizontalGlue());
        JLabel find = new JLabel(new ToolIcon(StandardGlyph.FIND));
        find.setToolTipText(msg.get("tool.find"));
        find.setLabelFor(searchField);
        bar.add(find);
        bar.add(Box.createHorizontalStrut(4));
        searchField.setName("search");
        searchField.setToolTipText(msg.get("tool.find"));
        searchField.setMaximumSize(searchField.getPreferredSize());
        searchField.addActionListener(e -> presenter.search(searchField.getText(),
                (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0));
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "back");
        searchField.getActionMap().put("back", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                presenter.search(searchField.getText(), true);
            }
        });
        searchField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        searchField.getActionMap().put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.setText("");
                presenter.clearSearch();
                pages.requestFocusInWindow();
            }
        });
        bar.add(searchField);
        bar.add(ToolButtons.button(document(action("tool.find-previous", Glyph.FIND_PREVIOUS,
                () -> presenter.search(searchField.getText(), true)))));
        bar.add(ToolButtons.button(document(action("tool.find-next", Glyph.FIND_NEXT,
                () -> presenter.search(searchField.getText(), false)))));
        searchLabel.setName("search.result");
        searchLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        bar.add(searchLabel);
        return bar;
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        status.setName("status");
        note.setName("status.note");
        for (JLabel label : List.of(status, note)) {
            label.setBorder(BorderFactory.createCompoundBorder(AmadeUI.sunken(), BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        }
        bar.add(status, BorderLayout.CENTER);
        note.setPreferredSize(new Dimension(Math.round(320 * AmadeUI.fontScale()), status.getPreferredSize().height));
        bar.add(note, BorderLayout.EAST);
        return bar;
    }

    private Action action(String key, ToolIcon.Glyph glyph, Runnable run) {
        AbstractAction action = new AbstractAction(msg.get(key)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        action.putValue(Action.LARGE_ICON_KEY, new ToolIcon(glyph));
        action.putValue(Action.SHORT_DESCRIPTION, msg.get(key));
        action.putValue(Action.ACTION_COMMAND_KEY, key);
        return action;
    }

    private Action document(Action action) {
        documentActions.add(action);
        return action;
    }

    private void setDocumentOpen(boolean open, boolean hasContents) {
        for (Action action : documentActions) action.setEnabled(open);
        pageField.setEnabled(open);
        zoomBox.setEnabled(open);
        searchField.setEnabled(open);
        if (menuBar != null) menuBar.setDocumentOpen(open, hasContents);
    }

    // --8<-- [start:keys]
    /** Keys for reading: they scroll the pages while the pages (not a text field) have the focus. */
    private void keys() {
        bind(KeyEvent.VK_DOWN, 0, () -> pages.scrollBy(0, 48));
        bind(KeyEvent.VK_UP, 0, () -> pages.scrollBy(0, -48));
        bind(KeyEvent.VK_RIGHT, 0, () -> sideways(true));
        bind(KeyEvent.VK_LEFT, 0, () -> sideways(false));
        bind(KeyEvent.VK_PAGE_DOWN, 0, () -> pageDown(true));
        bind(KeyEvent.VK_PAGE_UP, 0, () -> pageDown(false));
        bind(KeyEvent.VK_SPACE, 0, () -> pageDown(true));
        bind(KeyEvent.VK_SPACE, InputEvent.SHIFT_DOWN_MASK, () -> pageDown(false));
        bind(KeyEvent.VK_BACK_SPACE, 0, () -> pageDown(false));
        bind(KeyEvent.VK_HOME, 0, presenter::first);
        bind(KeyEvent.VK_END, 0, presenter::last);
        bind(KeyEvent.VK_ESCAPE, 0, () -> {
            pages.clearSelection();
            searchField.setText("");
            presenter.clearSearch();
        });
        // Ctrl with + and - as they are typed on different keyboards, beside the menu's Ctrl+Plus.
        JComponent root = getRootPane();
        for (int code : new int[]{KeyEvent.VK_EQUALS, KeyEvent.VK_ADD}) {
            for (int modifiers : new int[]{InputEvent.CTRL_DOWN_MASK, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK}) {
                global(root, code, modifiers, "zoom-in", presenter::zoomIn);
            }
        }
        global(root, KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK, "zoom-out", presenter::zoomOut);
        global(root, KeyEvent.VK_NUMPAD0, InputEvent.CTRL_DOWN_MASK, "actual-size", () -> presenter.setZoom(Zoom.of(1)));
    }
    // --8<-- [end:keys]

    private void bind(int code, int modifiers, Runnable action) {
        String name = "key-" + code + "-" + modifiers;
        pages.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(code, modifiers), name);
        pages.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static void global(JComponent root, int code, int modifiers, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(code, modifiers), name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** Page Down scrolls by a screen; at the bottom of a page in single page view it turns the page. */
    private void pageDown(boolean down) {
        if (session == null) return;
        if (pages.canScroll(down)) {
            int height = scroll.getViewport().getHeight() - 48;
            pages.scrollBy(0, down ? height : -height);
        } else if (down) {
            presenter.next();
        } else if (pages.mode() == ViewMode.SINGLE && presenter.page() > 0) {
            presenter.previous();
            pages.scrollTo(presenter.page(), 1, false);
        }
    }

    /** Left and right: scroll sideways when the pages are wider than the window, else turn the page. */
    private void sideways(boolean right) {
        if (session == null) return;
        if (pages.wider()) pages.scrollBy(right ? 48 : -48, 0);
        else if (right) presenter.next();
        else presenter.previous();
    }

    // ---------------------------------------------------------------- MainView

    @Override
    public void showSettings(Settings newSettings) {
        EdtExecutor.requireEdt();
        settings = newSettings;
        menuBar.update(settings);
        sidebarAction.putValue(Action.SELECTED_KEY, settings.sidebar());
        sidebar.setVisible(settings.sidebar());
        sidebar.showTab(settings.contents());
        revalidate();
    }

    @Override
    public void showDocument(DocumentSession newSession, String title, List<OutlineEntry> outline) {
        endPresentation();
        session = newSession;
        setTitle(title);
        pages.setDocument(session);
        sidebar.setDocument(session, outline);
        sidebar.showTab(settings.contents());
        setDocumentOpen(session != null, !outline.isEmpty());
        pageCount.setText(session == null ? "" : " " + msg.format("tool.of", session.pageCount()) + " ");
        pageField.setText(session == null ? "" : "1");
        searchField.setText("");
        showSearch(SearchState.NONE);
        updateStatus();
    }

    @Override
    public void showLayout(Zoom newZoom, Rotation rotation, int page) {
        zoom = newZoom;
        pages.setView(settings.view(), newZoom, rotation, page);
        sidebar.setRotation(rotation);
        updating = true;
        try {
            zoomBox.setSelectedItem(switch (newZoom.kind()) {
                case FIT_PAGE -> msg.get("zoom.fit-page");
                case FIT_WIDTH -> msg.get("zoom.fit-width");
                case FIXED -> percent(newZoom.factor());
            });
        } finally {
            updating = false;
        }
        updateStatus();
    }

    @Override
    public void showPage(int page) {
        pages.setCurrent(page);
        pageField.setText(String.valueOf(page + 1));
        sidebar.showPage(page);
        updateStatus();
    }

    private void updateStatus() {
        if (session == null || presenter == null) {
            status.setText(" ");
            return;
        }
        int page = Math.min(presenter.page(), session.pageCount() - 1);
        status.setText(msg.format("status.page", page + 1, session.pageCount()) + "     "
                + presenter.pageSize(session.sizes().get(page)) + "     " + percent(pages.zoomFactor()));
    }

    private String percent(double factor) {
        return msg.format("status.zoom", Math.round(factor * 100));
    }

    @Override
    public void scrollTo(int page, double offset, boolean points) {
        pages.scrollTo(page, offset, points);
    }

    @Override
    public void reveal(int page, Rectangle2D area) {
        pages.reveal(page, area);
    }

    @Override
    public double pageOffset() {
        return pages.pageOffset();
    }

    @Override
    public double zoomFactor() {
        return pages.zoomFactor();
    }

    @Override
    public void showSearch(SearchState state) {
        pages.setSearch(state);
        if (!state.active()) {
            searchLabel.setText(" ");
        } else if (state.hits().isEmpty()) {
            searchLabel.setText(state.running() ? "…" : msg.get("search.none"));
        } else {
            searchLabel.setText(msg.format("search.count", state.number(), state.hits().size())
                    + (state.running() ? "…" : ""));
        }
        searchLabel.setForeground(state.active() && !state.running() && state.hits().isEmpty()
                ? new Color(0xb00000) : AmadeUI.theme().textActive());
    }

    @Override
    public void showHistory(boolean back, boolean forward) {
        menuBar.showHistory(back, forward);
    }

    @Override
    public void showRecent(List<Path> files) {
        menuBar.showRecent(files);
    }

    @Override
    public void showStatus(String message) {
        note.setText(message == null || message.isEmpty() ? " " : message);
        noteTimer.restart();
    }

    private void showHover(String link) {
        if (link == null) {
            if (hover != null) note.setText(" ");
        } else {
            note.setText(link.startsWith("#") ? msg.format("status.link.page", link.substring(1)) : link);
        }
        hover = link;
    }

    @Override
    public String selectedText() {
        return pages.selectedText();
    }

    @Override
    public void selectAll(int page) {
        pages.selectAll(page);
    }

    @Override
    public void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @Override
    public Optional<Path> chooseFile(Optional<Path> folder) {
        JFileChooser chooser = new JFileChooser(folder.map(Path::toFile).orElse(null));
        chooser.setDialogTitle(msg.get("dialog.open.title"));
        chooser.setFileFilter(new FileNameExtensionFilter(msg.get("dialog.open.filter"), "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        return Optional.of(chooser.getSelectedFile().toPath());
    }

    @Override
    public Optional<String> askPassword(String fileName, boolean wrong) {
        JPasswordField field = new JPasswordField(20);
        field.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                SwingUtilities.invokeLater(field::requestFocusInWindow);
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(msg.format("dialog.password.text", fileName)), BorderLayout.NORTH);
        if (wrong) {
            JLabel again = new JLabel(msg.get("dialog.password.wrong"));
            again.setForeground(new Color(0xb00000));
            panel.add(again, BorderLayout.CENTER);
        }
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(msg.get("dialog.password.label")), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        panel.add(row, BorderLayout.SOUTH);
        int answer = JOptionPane.showConfirmDialog(this, panel, msg.get("dialog.password.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        char[] password = field.getPassword();
        try {
            return answer == JOptionPane.OK_OPTION ? Optional.of(new String(password)) : Optional.empty();
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    @Override
    public boolean printDialog(PrinterJob job, PrintRequestAttributeSet attributes) {
        try {
            return job.printDialog(attributes);
        } catch (java.awt.HeadlessException e) {
            return false;
        }
    }

    // --8<-- [start:properties]
    @Override
    public void showProperties(DocumentProperties p, Path file, String pageSize) {
        JPanel grid = new JPanel(new GridBagLayout());
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"prop.file", file.getFileName().toString()});
        rows.add(new String[]{"prop.folder", file.getParent() == null ? "" : file.getParent().toString()});
        rows.add(new String[]{"prop.size", fileSize(p.fileSize())});
        rows.add(new String[]{"prop.title", p.title()});
        rows.add(new String[]{"prop.author", p.author()});
        rows.add(new String[]{"prop.subject", p.subject()});
        rows.add(new String[]{"prop.keywords", p.keywords()});
        rows.add(new String[]{"prop.created", date(p.created())});
        rows.add(new String[]{"prop.modified", date(p.modified())});
        rows.add(new String[]{"prop.creator", p.creator()});
        rows.add(new String[]{"prop.producer", p.producer()});
        rows.add(new String[]{"prop.version", p.version()});
        rows.add(new String[]{"prop.pages", String.valueOf(p.pages())});
        rows.add(new String[]{"prop.paper", pageSize});
        rows.add(new String[]{"prop.encrypted", msg.get(p.encrypted() ? "prop.yes" : "prop.no")});
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(2, 0, 2, 12);
        int y = 0;
        for (String[] row : rows) {
            c.gridy = y++;
            c.gridx = 0;
            c.weightx = 0;
            JLabel key = new JLabel(msg.get(row[0]));
            key.setForeground(AmadeUI.theme().textInactive());
            grid.add(key, c);
            c.gridx = 1;
            c.weightx = 1;
            JTextField value = new JTextField(row[1].isEmpty() ? "—" : row[1]);
            value.setEditable(false);
            value.setBorder(BorderFactory.createEmptyBorder());
            value.setOpaque(false);
            value.setColumns(Math.min(40, Math.max(10, row[1].length())));
            value.setName(row[0]);
            grid.add(value, c);
        }
        JOptionPane.showMessageDialog(this, grid, msg.format("dialog.properties.title", file.getFileName().toString()),
                JOptionPane.PLAIN_MESSAGE);
    }
    // --8<-- [end:properties]

    private String fileSize(long bytes) {
        if (bytes < 1024 * 1024) return msg.format("prop.size.kb", Math.max(1, Math.round(bytes / 1024.0)));
        return msg.format("prop.size.mb", String.format(msg.locale(), "%.1f", bytes / 1024.0 / 1024.0));
    }

    private String date(Optional<ZonedDateTime> date) {
        return date.map(d -> d.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(msg.locale())))
                .orElse("");
    }

    @Override
    public void startPresentation(int page) {
        if (session == null) return;
        endPresentation();
        presentation = new PresentationWindow(session, pages.rotation(), page, last -> {
            presentation = null;
            presenter.presentationEnded(last);
            toFront();
            pages.requestFocusInWindow();
        });
        presentation.start(getGraphicsConfiguration());
    }

    private void endPresentation() {
        if (presentation != null) presentation.end();
        presentation = null;
    }

    @Override
    public void focusSearch() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    @Override
    public void focusPage() {
        pageField.requestFocusInWindow();
        pageField.selectAll();
    }

    @Override
    public void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, plain(message), msg.get("dialog.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, plain(message), getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showAbout() {
        JOptionPane.showMessageDialog(this,
                "<html><center><h2>amade-pdfviewer " + AmadePdfViewerApplication.version() + "</h2>"
                        + "<p>" + msg.get("dialog.about.text") + "</p><br>"
                        + "<small>&copy; 2026 datazuul.com &bull; EUPL-1.2</small></center></html>",
                msg.get("dialog.about.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        endPresentation();
        noteTimer.stop();
        dispose();
    }

    private static JTextArea plain(String message) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setOpaque(false);
        return text;
    }

    // ---------------------------------------------------------------- DocumentSession.Listener

    @Override
    public void pageReady(int page) {
        pages.repaintPage(page);
        if (presentation != null) presentation.pageReady(page);
    }

    @Override
    public void thumbnailReady(int page) {
        sidebar.thumbnailReady(page);
        pages.repaintPage(page);
    }

    @Override
    public void textReady(int page) {
        pages.repaintPage(page);
    }

    // ---------------------------------------------------------------- dropping files

    // --8<-- [start:drop]
    /** Files dropped from a file manager: as a file list, or as {@code text/uri-list} under X11. */
    private final class FileDrop extends TransferHandler {
        private static final DataFlavor URI_LIST = uriList();

        private static DataFlavor uriList() {
            try {
                return new DataFlavor("text/uri-list;class=java.lang.String");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || support.isDataFlavorSupported(URI_LIST);
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                Transferable t = support.getTransferable();
                Path dropped = null;
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<?> files = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty() && files.getFirst() instanceof File f) dropped = f.toPath();
                } else {
                    for (String line : ((String) t.getTransferData(URI_LIST)).split("\r?\n")) {
                        if (line.startsWith("file:")) {
                            dropped = Path.of(URI.create(line.strip()));
                            break;
                        }
                    }
                }
                if (dropped == null) return false;
                presenter.open(dropped);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
    // --8<-- [end:drop]

    // ---------------------------------------------------------------- for tests

    MainMenuBar menu() {
        return menuBar;
    }

    PageView pageView() {
        return pages;
    }

    Sidebar sidebar() {
        return sidebar;
    }

    JTextField pageField() {
        return pageField;
    }

    JLabel pageCountLabel() {
        return pageCount;
    }

    JTextField searchField() {
        return searchField;
    }

    JLabel searchLabel() {
        return searchLabel;
    }

    JLabel statusLabel() {
        return status;
    }

    JLabel noteLabel() {
        return note;
    }

    JComboBox<String> zoomBox() {
        return zoomBox;
    }

    JScrollPane scrollPane() {
        return scroll;
    }

    JButton toolButton(String name) {
        return find(getContentPane(), name);
    }

    private static JButton find(java.awt.Container c, String name) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof JButton b && name.equals(b.getName())) return b;
            if (child instanceof java.awt.Container inner) {
                JButton found = find(inner, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    JToggleButton sidebarButton() {
        for (java.awt.Component child : ((JToolBar) getContentPane().getComponent(0)).getComponents()) {
            if (child instanceof JToggleButton b && "tool.sidebar".equals(b.getName())) return b;
        }
        return null;
    }
}
