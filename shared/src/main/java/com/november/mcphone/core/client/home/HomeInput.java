package com.november.mcphone.core.client.home;

/** Pointer state shared by the launcher and folders. Operates on stable IDs in device coordinates. */
public final class HomeInput {
    public static final class Release {
        private final String id;
        private final boolean dragged;
        private final boolean clicked;

        public Release(String id, boolean dragged, boolean clicked) {
            this.id = id;
            this.dragged = dragged;
            this.clicked = clicked;
        }

        public String id() { return id; }

        public boolean dragged() { return dragged; }

        public boolean clicked() { return clicked; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Release)) return false;
            Release that = (Release) other;
            return java.util.Objects.equals(this.id, that.id)
                    && this.dragged == that.dragged
                    && this.clicked == that.clicked;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(id);
            hash = 31 * hash + java.util.Objects.hashCode(dragged);
            hash = 31 * hash + java.util.Objects.hashCode(clicked);
            return hash;
        }

        @Override public String toString() { return "Release[id=" + id + ", dragged=" + dragged + ", clicked=" + clicked + "]"; }
}
    public static final class FolderDrag {
        private final String folderId;
        private final String appId;

        public FolderDrag(String folderId, String appId) {
            this.folderId = folderId;
            this.appId = appId;
        }

        public String folderId() { return folderId; }

        public String appId() { return appId; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FolderDrag)) return false;
            FolderDrag that = (FolderDrag) other;
            return java.util.Objects.equals(this.folderId, that.folderId)
                    && java.util.Objects.equals(this.appId, that.appId);
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(folderId);
            hash = 31 * hash + java.util.Objects.hashCode(appId);
            return hash;
        }

        @Override public String toString() { return "FolderDrag[folderId=" + folderId + ", appId=" + appId + "]"; }
}

    private final int dragThreshold;
    private final int swipeThreshold;
    private boolean pressed;
    private boolean dragging;
    private String id;
    private double startX;
    private double startY;
    private double x;
    private double y;

    public HomeInput(int dragThreshold, int swipeThreshold) {
        this.dragThreshold = dragThreshold;
        this.swipeThreshold = swipeThreshold;
    }

    public void press(String id, double x, double y) {
        pressed = true;
        dragging = false;
        this.id = id;
        startX = this.x = x;
        startY = this.y = y;
    }

    /** Takes over a drag that already crossed the source view's drag threshold. */
    public void continueDrag(String id, double x, double y) {
        press(id, x, y);
        dragging = true;
    }

    /** Returns a page step when a drag starts on a blank part of the launcher. */
    public int drag(double x, double y) {
        if (!pressed) return 0;
        this.x = x;
        this.y = y;

        double dx = x - startX;
        if (id != null) {
            dragging |= Math.abs(dx) >= dragThreshold || Math.abs(y - startY) >= dragThreshold;
        } else if (Math.abs(dx) >= swipeThreshold) {
            startX = x;
            return dx < 0 ? 1 : -1;
        }
        return 0;
    }

    public Release release(String hitId, double x, double y) {
        if (!pressed) return null;
        drag(x, y);
        Release result = new Release(id, dragging, !dragging && id != null && id.equals(hitId));
        cancel();
        return result;
    }

    public boolean pressed() { return pressed; }
    public String id() { return id; }
    public boolean dragging() { return dragging; }
    public double x() { return x; }
    public double y() { return y; }

    public void cancel() {
        pressed = false;
        dragging = false;
        id = null;
    }
}
