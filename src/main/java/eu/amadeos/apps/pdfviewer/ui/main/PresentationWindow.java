package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.domain.DocumentSession;
import eu.amadeos.apps.pdfviewer.domain.DocumentThread.Priority;
import eu.amadeos.apps.pdfviewer.domain.PageSize;
import eu.amadeos.apps.pdfviewer.domain.Rotation;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.IntConsumer;

/**
 * The presentation: one page at a time on a black screen, as large as it fits. Space, Enter, Page Down, the
 * arrow keys and a click turn the pages; Escape ends it and the window goes on at the last page shown.
 * Full screen without changing the display mode; where that is not offered, a borderless window over the
 * whole screen.
 */
final class PresentationWindow extends JFrame {
    private final DocumentSession session;
    private final Rotation rotation;
    private final IntConsumer ended;
    private final Slide slide = new Slide();
    private final Timer hideCursor;
    private GraphicsDevice device;
    private int page;
    private boolean closed;

    PresentationWindow(DocumentSession session, Rotation rotation, int page, IntConsumer ended) {
        super("Presentation");
        this.session = session;
        this.rotation = rotation;
        this.page = page;
        this.ended = ended;
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(Color.BLACK);
        add(slide, BorderLayout.CENTER);
        hideCursor = new Timer(2000, e -> slide.setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(), "none")));
        hideCursor.setRepeats(false);
        slide.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE, KeyEvent.VK_ENTER, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_RIGHT,
                         KeyEvent.VK_DOWN, KeyEvent.VK_N -> show(PresentationWindow.this.page + 1);
                    case KeyEvent.VK_BACK_SPACE, KeyEvent.VK_PAGE_UP, KeyEvent.VK_LEFT, KeyEvent.VK_UP,
                         KeyEvent.VK_P -> show(PresentationWindow.this.page - 1);
                    case KeyEvent.VK_HOME -> show(0);
                    case KeyEvent.VK_END -> show(session.pageCount() - 1);
                    case KeyEvent.VK_ESCAPE, KeyEvent.VK_Q, KeyEvent.VK_F5 -> end();
                    default -> {
                    }
                }
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                show(SwingUtilities.isLeftMouseButton(e) ? PresentationWindow.this.page + 1
                        : PresentationWindow.this.page - 1);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                slide.setCursor(Cursor.getDefaultCursor());
                hideCursor.restart();
            }
        };
        slide.addMouseListener(mouse);
        slide.addMouseMotionListener(mouse);
        slide.setFocusable(true);
    }

    /** Opens on the screen of the main window. */
    void start(GraphicsConfiguration screen) {
        device = screen.getDevice();
        Rectangle bounds = screen.getBounds();
        if (device.isFullScreenSupported()) {
            device.setFullScreenWindow(this);
        } else {
            setBounds(bounds);
            setVisible(true);
        }
        slide.requestFocusInWindow();
        hideCursor.start();
    }

    int page() {
        return page;
    }

    private void show(int newPage) {
        page = Math.clamp(newPage, 0, session.pageCount() - 1);
        slide.repaint();
    }

    void pageReady(int ready) {
        if (ready == page) slide.repaint();
    }

    void end() {
        if (closed) return;
        closed = true;
        hideCursor.stop();
        if (device != null && device.getFullScreenWindow() == this) device.setFullScreenWindow(null);
        dispose();
        ended.accept(page);
    }

    private final class Slide extends JComponent {
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (session.closed() || getWidth() == 0) return;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            draw(g, page, Priority.VISIBLE);
            if (page + 1 < session.pageCount()) picture(page + 1, Priority.AHEAD);
        }

        private Rectangle place(int index) {
            PageSize size = rotation.apply(session.sizes().get(index));
            double scale = Math.min(getWidth() / size.width(), getHeight() / size.height());
            int w = (int) Math.round(size.width() * scale);
            int h = (int) Math.round(size.height() * scale);
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        private BufferedImage picture(int index, Priority priority) {
            Rectangle r = place(index);
            PageSize size = rotation.apply(session.sizes().get(index));
            return session.tile(index, r.width / size.width(), rotation, new Rectangle(0, 0, r.width, r.height),
                    priority);
        }

        private void draw(Graphics2D g, int index, Priority priority) {
            Rectangle r = place(index);
            BufferedImage image = picture(index, priority);
            if (image != null) {
                g.drawImage(image, r.x, r.y, null);
                return;
            }
            g.setColor(Color.WHITE);
            g.fill(r);
            DocumentSession.Preview preview = session.preview(index, rotation);
            if (preview != null) g.drawImage(preview.image(), r.x, r.y, r.width, r.height, null);
        }
    }
}
