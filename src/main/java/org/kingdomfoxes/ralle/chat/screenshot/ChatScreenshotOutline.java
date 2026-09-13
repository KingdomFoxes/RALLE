package org.kingdomfoxes.ralle.chat.screenshot;

import java.util.ArrayList;
import java.util.List;

/** Ordered pixel path for the animated outline around a selected chat contour. */
final class ChatScreenshotOutline {
    private ChatScreenshotOutline() {}

    static List<Segment> perimeter(List<ChatScreenshotGeometry.Rectangle> bounds) {
        if (bounds.isEmpty()) return List.of();

        var pixels = new ArrayList<Pixel>();
        ChatScreenshotGeometry.Rectangle first = bounds.getFirst();
        ChatScreenshotGeometry.Rectangle last = bounds.getLast();

        // The animation starts at the bottom-right corner and travels upward,
        // then left across the top edge before returning around the contour.
        appendLine(pixels, last.right() - 1, last.bottom() - 1);
        appendLine(pixels, last.right() - 1, last.top());
        for (int index = bounds.size() - 2; index >= 0; index--) {
            ChatScreenshotGeometry.Rectangle lower = bounds.get(index + 1);
            ChatScreenshotGeometry.Rectangle upper = bounds.get(index);
            int boundary = lower.top();
            appendLine(pixels, upper.right() - 1, boundary);
            appendLine(pixels, upper.right() - 1, boundary - 1);
            appendLine(pixels, upper.right() - 1, upper.top());
        }

        appendLine(pixels, first.left(), first.top());
        appendLine(pixels, first.left(), first.bottom() - 1);
        for (int index = 1; index < bounds.size(); index++) {
            ChatScreenshotGeometry.Rectangle previous = bounds.get(index - 1);
            ChatScreenshotGeometry.Rectangle current = bounds.get(index);
            int boundary = current.top();
            appendLine(pixels, previous.left(), boundary);
            appendLine(pixels, current.left(), boundary);
            appendLine(pixels, current.left(), current.bottom() - 1);
        }
        appendLine(pixels, last.right() - 1, last.bottom() - 1);

        // Closing the loop reaches the starting pixel again; keep that pixel
        // only once so the dash phase does not get an artificial extra unit.
        if (pixels.size() > 1 && pixels.getFirst().equals(pixels.getLast())) pixels.removeLast();
        return compress(pixels);
    }

    private static void appendLine(List<Pixel> pixels, int targetX, int targetY) {
        if (pixels.isEmpty()) {
            pixels.add(new Pixel(targetX, targetY));
            return;
        }

        Pixel start = pixels.getLast();
        int stepX = Integer.compare(targetX, start.x());
        int stepY = Integer.compare(targetY, start.y());
        if (stepX != 0 && stepY != 0) {
            throw new IllegalArgumentException("Outline path must be axis-aligned");
        }
        int x = start.x();
        int y = start.y();
        while (x != targetX || y != targetY) {
            x += stepX;
            y += stepY;
            pixels.add(new Pixel(x, y));
        }
    }

    private static List<Segment> compress(List<Pixel> pixels) {
        if (pixels.isEmpty()) return List.of();
        var segments = new ArrayList<Segment>();
        Pixel start = pixels.getFirst();
        int directionX = 0;
        int directionY = 0;
        int length = 1;
        for (int index = 1; index < pixels.size(); index++) {
            Pixel previous = pixels.get(index - 1);
            Pixel current = pixels.get(index);
            int nextDirectionX = Integer.compare(current.x(), previous.x());
            int nextDirectionY = Integer.compare(current.y(), previous.y());
            if (index == 1) {
                directionX = nextDirectionX;
                directionY = nextDirectionY;
                length++;
            } else if (nextDirectionX == directionX && nextDirectionY == directionY) {
                length++;
            } else {
                segments.add(new Segment(start.x(), start.y(), directionX, directionY, length));
                start = current;
                directionX = nextDirectionX;
                directionY = nextDirectionY;
                length = 1;
            }
        }
        segments.add(new Segment(start.x(), start.y(), directionX, directionY, length));
        return List.copyOf(segments);
    }

    record Segment(int startX, int startY, int directionX, int directionY, int length) {
        Segment {
            if (length < 1 || Math.abs(directionX) + Math.abs(directionY) != 1) {
                throw new IllegalArgumentException("Invalid outline segment");
            }
        }

        int xAt(int offset) { return startX + directionX * offset; }

        int yAt(int offset) { return startY + directionY * offset; }
    }

    private record Pixel(int x, int y) {}
}
