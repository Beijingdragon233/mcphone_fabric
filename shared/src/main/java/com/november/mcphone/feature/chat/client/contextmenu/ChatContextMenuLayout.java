package com.november.mcphone.feature.chat.client.contextmenu;

/** 菜单绘制和命中共用几何；图标与文本保持统一列，菜单向手机内容区内避让。 */
final class ChatContextMenuLayout {
    static final int PAD = 3, ICON_SIZE = 8, ICON_GAP = 4;
    static final class Bounds {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int x() { return x; }

        public int y() { return y; }

        public int width() { return width; }

        public int height() { return height; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Bounds)) return false;
            Bounds that = (Bounds) other;
            return this.x == that.x
                    && this.y == that.y
                    && this.width == that.width
                    && this.height == that.height;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(x);
            hash = 31 * hash + java.util.Objects.hashCode(y);
            hash = 31 * hash + java.util.Objects.hashCode(width);
            hash = 31 * hash + java.util.Objects.hashCode(height);
            return hash;
        }

        @Override public String toString() { return "Bounds[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]"; }

        boolean contains(double mx,double my) { return mx>=x && mx<x+width && my>=y && my<y+height; }
    }
    static final class Layout {
        private final Bounds bounds;
        private final int rowHeight;
        private final int rows;
        private final int contentInset;

        Layout(Bounds bounds, int rowHeight, int rows, int contentInset) {
            this.bounds = bounds;
            this.rowHeight = rowHeight;
            this.rows = rows;
            this.contentInset = contentInset;
        }

        public Bounds bounds() { return bounds; }

        public int rowHeight() { return rowHeight; }

        public int rows() { return rows; }

        public int contentInset() { return contentInset; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Layout)) return false;
            Layout that = (Layout) other;
            return java.util.Objects.equals(this.bounds, that.bounds)
                    && this.rowHeight == that.rowHeight
                    && this.rows == that.rows
                    && this.contentInset == that.contentInset;
        }

        @Override public int hashCode() {
            int hash = 0;
            hash = 31 * hash + java.util.Objects.hashCode(bounds);
            hash = 31 * hash + java.util.Objects.hashCode(rowHeight);
            hash = 31 * hash + java.util.Objects.hashCode(rows);
            hash = 31 * hash + java.util.Objects.hashCode(contentInset);
            return hash;
        }

        @Override public String toString() { return "Layout[bounds=" + bounds + ", rowHeight=" + rowHeight + ", rows=" + rows + ", contentInset=" + contentInset + "]"; }

        int rowAt(double mx,double my) {
            if (!bounds.contains(mx,my) || my<bounds.y()+PAD || my>=bounds.y()+PAD+rows*rowHeight) return -1;
            return (int)((my-bounds.y()-PAD)/rowHeight);
        }
        int iconX() { return bounds.x()+contentInset; }
        int textX() { return iconX()+ICON_SIZE+ICON_GAP; }
    }
    static Layout place(double mx,double my,Bounds viewport,int labelWidth,int textHeight,int rows) {
        int rowHeight = Math.max(textHeight+6,ICON_SIZE+4);
        int contentWidth = labelWidth+ICON_SIZE+ICON_GAP;
        int width = Math.min(Math.max(40,contentWidth+PAD*2+4),viewport.width());
        int height = rows*rowHeight+PAD*2;
        int x = Math.max(viewport.x(),Math.min((int)Math.floor(mx),viewport.x()+viewport.width()-width));
        int y = Math.max(viewport.y(),Math.min((int)Math.floor(my),viewport.y()+viewport.height()-height));
        int inset = Math.max(PAD,(width-contentWidth)/2);
        return new Layout(new Bounds(x,y,width,height),rowHeight,rows,inset);
    }
}
