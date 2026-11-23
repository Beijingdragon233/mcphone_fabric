package com.november.mcphone.core.client.home;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

/** Post-drop movement from the last visible desktop positions to the settled layout. */
public final class HomeReflow {
    public static final long DURATION_MS = 185;
    public static final HomeReflow NONE = new HomeReflow(ImmutableMap.of());

    /** Top-left icon position in logical pixels. */
    public static final class Position {
        private final double x;
        private final double y;

        public Position(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double x() { return x; }

        public double y() { return y; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Position)) return false;
            Position that = (Position) other;
            return Double.compare(this.x, that.x) == 0
                    && Double.compare(this.y, that.y) == 0;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(x);
            hash = 31 * hash + java.util.Objects.hashCode(y);
            return hash;
        }

        @Override public String toString() { return "Position[x=" + x + ", y=" + y + "]"; }
}

    private final Map<String, Position> offsets;

    private HomeReflow(Map<String, Position> offsets) {
        this.offsets = offsets;
    }

    /**
     * Matches icons by stable ID. A new folder can inherit the position of the
     * app it replaces by supplying its ID and the former app ID in formerIds.
     */
    public static HomeReflow between(Map<String, Position> previous, Map<String, Position> next,
                                     Map<String, String> formerIds) {
        Map<String, Position> offsets = new HashMap<>();
        next.forEach((id, resting) -> {
            Position origin = previous.get(id);
            String formerId = formerIds.get(id);
            if (origin == null && formerId != null) origin = previous.get(formerId);
            if (origin != null && !origin.equals(resting)) {
                offsets.put(id, new Position(origin.x() - resting.x(), origin.y() - resting.y()));
            }
        });
        return offsets.isEmpty() ? NONE : new HomeReflow(ImmutableMap.copyOf(offsets));
    }

    public boolean isEmpty() {
        return offsets.isEmpty();
    }

    /** Uses the same position for painting and hit-testing. */
    public Position position(String id, Position resting, long elapsedMs) {
        Position offset = offsets.get(id);
        if (offset == null || elapsedMs >= DURATION_MS) return resting;
        double remaining = elapsedMs <= 0 ? 1.0 : 1.0 - easing((double) elapsedMs / DURATION_MS);
        return new Position(resting.x() + offset.x() * remaining, resting.y() + offset.y() * remaining);
    }

    private static double easing(double progress) {
        // Invert the x component of the demo's cubic-bezier(.2, .72, .22, 1).
        double low = 0;
        double high = 1;
        for (int i = 0; i < 16; i++) {
            double t = (low + high) / 2;
            if (bezier(t, .2, .22) < progress) {
                low = t;
            } else {
                high = t;
            }
        }
        return bezier((low + high) / 2, .72, 1);
    }

    private static double bezier(double t, double first, double second) {
        double inverse = 1 - t;
        return 3 * inverse * inverse * t * first + 3 * inverse * t * t * second + t * t * t;
    }
}
