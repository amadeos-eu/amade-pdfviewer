package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.domain.DocumentSession;
import eu.amadeos.apps.pdfviewer.domain.OutlineEntry;
import eu.amadeos.apps.pdfviewer.domain.PageSize;
import eu.amadeos.apps.pdfviewer.domain.Rotation;
import eu.amadeos.apps.pdfviewer.domain.Target;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** The sidebar: thumbnails of the pages, or the table of contents, switched by two buttons at the top. */
final class Sidebar extends JPanel {
    private static final String PAGES = "pages";
    private static final String CONTENTS = "contents";
    private static final int CELL_WIDTH = DocumentSession.THUMBNAIL_WIDTH + 24;

    private final JToggleButton pagesButton;
    private final JToggleButton contentsButton;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final PageModel model = new PageModel();
    private final JList<Integer> thumbnails = new JList<>(model);
    private final JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    private DocumentSession session;
    private Rotation rotation = Rotation.NONE;
    private boolean updating;
    private IntConsumer pageChosen = page -> { };
    private Consumer<Target> targetChosen = target -> { };
    private Consumer<Boolean> tabChosen = contents -> { };

    Sidebar(Messages msg) {
        super(new BorderLayout(0, 4));
        setName("sidebar");
        setBorder(BorderFactory.createCompoundBorder(AmadeUI.raised(), BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        pagesButton = new JToggleButton(msg.get("sidebar.pages"), true);
        pagesButton.setName("sidebar.pages");
        contentsButton = new JToggleButton(msg.get("sidebar.contents"));
        contentsButton.setName("sidebar.contents");
        for (JToggleButton b : List.of(pagesButton, contentsButton)) b.setFocusable(false);
        ButtonGroup group = new ButtonGroup();
        group.add(pagesButton);
        group.add(contentsButton);
        pagesButton.addActionListener(e -> tabChosen.accept(false));
        contentsButton.addActionListener(e -> tabChosen.accept(true));
        JPanel tabs = new JPanel(new GridLayout(1, 2, 2, 0));
        tabs.add(pagesButton);
        tabs.add(contentsButton);
        add(tabs, BorderLayout.NORTH);

        thumbnails.setName("thumbnails");
        thumbnails.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        thumbnails.setCellRenderer(new ThumbnailRenderer());
        thumbnails.setFixedCellWidth(CELL_WIDTH);
        thumbnails.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && thumbnails.getSelectedIndex() >= 0) {
                pageChosen.accept(thumbnails.getSelectedIndex());
            }
        });
        AmadeUI.style(thumbnails, (list, theme) -> list.setBackground(theme.sunkenBg()));

        tree.setName("contents");
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        DefaultTreeCellRenderer cells = new DefaultTreeCellRenderer();
        cells.setLeafIcon(null);
        cells.setOpenIcon(null);
        cells.setClosedIcon(null);
        tree.setCellRenderer(cells);
        AmadeUI.style(tree, (t, theme) -> {
            t.setBackground(theme.sunkenBg());
            cells.setBackgroundNonSelectionColor(theme.sunkenBg());
            cells.setBackgroundSelectionColor(theme.selectionBackground());
            cells.setTextSelectionColor(theme.selectionText());
            cells.setBorderSelectionColor(theme.selectionBackground());
        });
        tree.addTreeSelectionListener(e -> {
            if (updating || tree.getSelectionPath() == null) return;
            if (tree.getSelectionPath().getLastPathComponent() instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof OutlineEntry entry) {
                entry.target().ifPresent(targetChosen);
            }
        });

        body.add(scroll(thumbnails), PAGES);
        body.add(scroll(tree), CONTENTS);
        add(body, BorderLayout.CENTER);
        setPreferredSize(new Dimension(Math.max(CELL_WIDTH + 52, Math.round(240 * AmadeUI.fontScale())), 400));
    }

    private static JScrollPane scroll(JComponent c) {
        JScrollPane pane = new JScrollPane(c);
        pane.setBorder(AmadeUI.sunken());
        pane.getVerticalScrollBar().setUnitIncrement(24);
        return pane;
    }

    void onPage(IntConsumer listener) {
        pageChosen = listener;
    }

    void onTarget(Consumer<Target> listener) {
        targetChosen = listener;
    }

    void onTab(Consumer<Boolean> listener) {
        tabChosen = listener;
    }

    void setDocument(DocumentSession newSession, List<OutlineEntry> outline) {
        updating = true;
        try {
            session = newSession;
            model.setSize(session == null ? 0 : session.pageCount());
            if (session != null && session.pageCount() > 0) {
                PageSize first = rotation.apply(session.sizes().getFirst());
                int height = (int) Math.round(DocumentSession.THUMBNAIL_WIDTH * first.height()
                        / Math.max(first.width(), first.height() * 0.75));
                thumbnails.setFixedCellHeight(Math.min(height, DocumentSession.THUMBNAIL_WIDTH * 4 / 3 + 1) + 28);
            }
            DefaultMutableTreeNode root = new DefaultMutableTreeNode();
            int count = add(root, outline);
            tree.setModel(new DefaultTreeModel(root));
            for (int i = 0; i < tree.getRowCount(); i++) {
                TreePath path = tree.getPathForRow(i);
                if (count <= 100 || path.getPathCount() <= 2) tree.expandRow(i);
            }
            contentsButton.setEnabled(!outline.isEmpty());
        } finally {
            updating = false;
        }
    }

    private static int add(DefaultMutableTreeNode parent, List<OutlineEntry> entries) {
        int count = 0;
        for (OutlineEntry entry : entries) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
            parent.add(node);
            count += 1 + add(node, entry.children());
        }
        return count;
    }

    boolean hasContents() {
        return contentsButton.isEnabled();
    }

    /** Shows the contents (true) or the thumbnails; without a table of contents always the thumbnails. */
    void showTab(boolean contents) {
        boolean shown = contents && contentsButton.isEnabled();
        (shown ? contentsButton : pagesButton).setSelected(true);
        cards.show(body, shown ? CONTENTS : PAGES);
    }

    void setRotation(Rotation newRotation) {
        if (newRotation == rotation) return;
        rotation = newRotation;
        if (session != null) setDocument(session, outline());
    }

    private List<OutlineEntry> outline() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        List<OutlineEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            entries.add((OutlineEntry) ((DefaultMutableTreeNode) root.getChildAt(i)).getUserObject());
        }
        return entries;
    }

    void showPage(int page) {
        updating = true;
        try {
            if (page >= 0 && page < model.getSize()) {
                thumbnails.setSelectedIndex(page);
                thumbnails.ensureIndexIsVisible(page);
            }
        } finally {
            updating = false;
        }
    }

    void thumbnailReady(int page) {
        Rectangle r = page < model.getSize() ? thumbnails.getCellBounds(page, page) : null;
        if (r != null) thumbnails.repaint(r);
    }

    JList<Integer> thumbnailList() {
        return thumbnails;
    }

    JTree contentsTree() {
        return tree;
    }

    private static final class PageModel extends AbstractListModel<Integer> {
        private int size;

        void setSize(int newSize) {
            int old = size;
            size = newSize;
            if (old > 0) fireIntervalRemoved(this, 0, old - 1);
            if (newSize > 0) fireIntervalAdded(this, 0, newSize - 1);
        }

        @Override
        public int getSize() {
            return size;
        }

        @Override
        public Integer getElementAt(int index) {
            return index;
        }
    }

    /** A thumbnail with the page number under it; the current page has a frame in the selection colour. */
    private final class ThumbnailRenderer extends JComponent implements ListCellRenderer<Integer> {
        private int page;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends Integer> list, Integer value, int index,
                                                      boolean isSelected, boolean focus) {
            page = value;
            selected = isSelected;
            setFont(list.getFont());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(AmadeUI.theme().sunkenBg());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (session == null || session.closed()) return;
            BufferedImage image = session.thumbnail(page, rotation);
            PageSize size = rotation.apply(session.sizes().get(page));
            double scale = DocumentSession.THUMBNAIL_WIDTH / Math.max(size.width(), size.height() * 0.75);
            int w = image != null ? image.getWidth() : (int) Math.round(size.width() * scale);
            int h = image != null ? image.getHeight() : (int) Math.round(size.height() * scale);
            int x = (getWidth() - w) / 2;
            int y = 6;
            if (selected) {
                g.setColor(AmadeUI.theme().selectionBackground());
                g.fillRect(x - 4, y - 4, w + 8, h + 8);
            }
            g.setColor(Color.WHITE);
            g.fillRect(x, y, w, h);
            if (image != null) g.drawImage(image, x, y, null);
            g.setColor(new Color(0, 0, 0, 80));
            g.drawRect(x, y, w - 1, h - 1);
            g.setColor(AmadeUI.theme().textActive());
            Font font = getFont();
            g.setFont(font.deriveFont(selected ? Font.BOLD : Font.PLAIN));
            FontMetrics fm = g.getFontMetrics();
            String label = String.valueOf(page + 1);
            g.drawString(label, (getWidth() - fm.stringWidth(label)) / 2, y + h + 4 + fm.getAscent());
        }
    }
}
