package eu.amadeos.apps.pdfviewer.domain;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text of a page in reading order, with the box of every character on the shown page (points, origin top
 * left). Spaces and line breaks between words and lines have an empty box after the character before them.
 * Search, selection and copying work on positions in this text.
 */
public final class PageText {
    public static final PageText EMPTY = new Builder().build();

    private final String text;
    private final float[] boxes;

    private PageText(String text, float[] boxes) {
        this.text = text;
        this.boxes = boxes;
    }

    public String text() {
        return text;
    }

    public int length() {
        return text.length();
    }

    public Rectangle2D box(int index) {
        int i = index * 4;
        return new Rectangle2D.Float(boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3]);
    }

    // --8<-- [start:find]
    /**
     * Where the query occurs, ignoring case; a space in the query stands for any spaces and line breaks, so a
     * phrase is found across lines.
     */
    public List<int[]> find(String query) {
        Pattern pattern = pattern(query);
        if (pattern == null) return List.of();
        List<int[]> found = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            if (m.end() > m.start()) found.add(new int[]{m.start(), m.end()});
        }
        return found;
    }

    static Pattern pattern(String query) {
        String[] words = query == null ? new String[0] : query.strip().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return null;
        StringBuilder regex = new StringBuilder();
        for (String word : words) {
            if (!regex.isEmpty()) regex.append("\\s+");
            regex.append(Pattern.quote(word));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
    // --8<-- [end:find]

    /** The character whose box holds the point, or -1. */
    public int indexAt(Point2D p) {
        for (int i = 0; i < text.length(); i++) {
            int b = i * 4;
            if (boxes[b + 2] > 0 && p.getX() >= boxes[b] && p.getX() <= boxes[b] + boxes[b + 2]
                    && p.getY() >= boxes[b + 1] && p.getY() <= boxes[b + 1] + boxes[b + 3]) {
                return i;
            }
        }
        return -1;
    }

    /** The word around the character: from the space before it to the space after it. */
    public int[] wordAt(int index) {
        if (index < 0 || index >= text.length() || Character.isWhitespace(text.charAt(index))) {
            return new int[]{index, index};
        }
        int start = index;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start--;
        int end = index;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        return new int[]{start, end};
    }

    // --8<-- [start:caret]
    /**
     * The position between two characters nearest to a point, for selecting with the mouse: in the
     * character under the point, else the nearest character on the same line, else the nearest one at all;
     * before it when the point is in its left half. -1 when the page has no text.
     */
    public int caretAt(Point2D p) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < text.length(); i++) {
            int b = i * 4;
            if (boxes[b + 2] <= 0) continue;
            double left = boxes[b];
            double top = boxes[b + 1];
            double right = left + boxes[b + 2];
            double bottom = top + boxes[b + 3];
            double dx = p.getX() < left ? left - p.getX() : Math.max(0, p.getX() - right);
            double dy = p.getY() < top ? top - p.getY() : Math.max(0, p.getY() - bottom);
            // Being on the line weighs more than being near it horizontally.
            double distance = dy * 1000 + dx;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        if (best < 0) return -1;
        int b = best * 4;
        return p.getX() > boxes[b] + boxes[b + 2] / 2 ? best + 1 : best;
    }
    // --8<-- [end:caret]

    /** The text from {@code start} to {@code end}, the order of the two does not matter. */
    public String text(int start, int end) {
        int from = Math.clamp(Math.min(start, end), 0, text.length());
        int to = Math.clamp(Math.max(start, end), 0, text.length());
        return text.substring(from, to);
    }

    // --8<-- [start:areas]
    /** The boxes of the characters from {@code start} to {@code end}, joined into one rectangle per line. */
    public List<Rectangle2D> areas(int start, int end) {
        int from = Math.clamp(Math.min(start, end), 0, text.length());
        int to = Math.clamp(Math.max(start, end), 0, text.length());
        List<Rectangle2D> areas = new ArrayList<>();
        Rectangle2D line = null;
        for (int i = from; i < to; i++) {
            int b = i * 4;
            if (boxes[b + 2] <= 0 || boxes[b + 3] <= 0) continue;
            Rectangle2D box = box(i);
            if (line != null && sameLine(line, box) && box.getX() >= line.getX() - 1) {
                line = line.createUnion(box);
            } else {
                if (line != null) areas.add(line);
                line = box;
            }
        }
        if (line != null) areas.add(line);
        return areas;
    }
    // --8<-- [end:areas]

    private static boolean sameLine(Rectangle2D a, Rectangle2D b) {
        double overlap = Math.min(a.getMaxY(), b.getMaxY()) - Math.max(a.getY(), b.getY());
        return overlap > Math.min(a.getHeight(), b.getHeight()) / 2;
    }

    /** Collects the characters of a page in reading order. */
    public static final class Builder {
        private final StringBuilder text = new StringBuilder();
        private float[] boxes = new float[256];

        /** A character and its box. */
        public Builder add(char c, double x, double y, double width, double height) {
            int i = text.length() * 4;
            if (i + 4 > boxes.length) boxes = Arrays.copyOf(boxes, boxes.length * 2);
            boxes[i] = (float) x;
            boxes[i + 1] = (float) y;
            boxes[i + 2] = (float) Math.max(0, width);
            boxes[i + 3] = (float) Math.max(0, height);
            text.append(c);
            return this;
        }

        /** A space or line break between words or lines; nothing at the start or twice in a row. */
        public Builder separator(char c) {
            if (text.isEmpty()) return this;
            char last = text.charAt(text.length() - 1);
            if (last == '\n' || last == c) return this;
            if (last == ' ' && c == '\n') {
                text.setCharAt(text.length() - 1, '\n');
                return this;
            }
            int b = (text.length() - 1) * 4;
            return add(c, boxes[b] + boxes[b + 2], boxes[b + 1], 0, boxes[b + 3]);
        }

        public PageText build() {
            String s = text.toString();
            // No trailing line break.
            int length = s.length();
            while (length > 0 && Character.isWhitespace(s.charAt(length - 1))) length--;
            return new PageText(s.substring(0, length), Arrays.copyOf(boxes, length * 4));
        }
    }
}
