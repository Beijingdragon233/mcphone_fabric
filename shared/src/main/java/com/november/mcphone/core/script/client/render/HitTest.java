package com.november.mcphone.core.script.client.render;

import com.november.mcphone.api.client.ui.PhoneCanvas;
import com.november.mcphone.core.script.layout.LayoutEngine;
import com.november.mcphone.core.script.layout.LayoutNode;
import com.november.mcphone.core.script.layout.TextMeasure;

import java.util.HashSet;
import java.util.Set;

/**
 * 命中判定（施工方案 §8.5）。点击、悬停、滚轮走同一次遍历，偏移与 {@link Renderer} 画的一致：
 * 子节点逆序测（后画的在上），scroll / list 的裁剪区逐级取交集、子节点减去 scrollY，list 只测可见区里的项。
 *
 * <p>收 PhoneCanvas 的版本把鼠标四舍五入到整数：悬停用的 {@link PhoneCanvas#mouseX()} 是宿主四舍五入过的，
 * 点击与滚轮收到的是小数，取整方式不同的话两者在边界那一格判给不同的节点。
 */
public final class HitTest {

    /** 滚轮一格滚动的像素，约两行字。 */
    public static final int SCROLL_STEP = 12;

    private HitTest() {
    }

    /** 鼠标下最上面那个可交互节点（button / toggle / tab-bar），没有返回 null。禁用的也返回：它要吃掉点击。 */
    public static LayoutNode pick(LayoutNode root, PhoneCanvas c, double mx, double my) {
        return pick(root, c.x(), c.y(), c.width(), c.height(), Math.round(mx), Math.round(my), new FontMeasure(c.font()));
    }

    /** 同上，画布矩形与鼠标直接给数。 */
    public static LayoutNode pick(LayoutNode root, int x, int y, int w, int h, double mx, double my, TextMeasure tm) {
        return find(root, x, y, mx, my, x, y, (long) x + w, (long) y + h, tm, false, null);
    }

    /** 鼠标下最内层的 scroll / list，没有返回 null。不往外冒泡：内层滚到头也不带着外层滚（§5.1）。 */
    public static LayoutNode pickScroller(LayoutNode root, PhoneCanvas c, double mx, double my) {
        return pickScroller(root, c.x(), c.y(), c.width(), c.height(), Math.round(mx), Math.round(my),
                new FontMeasure(c.font()));
    }

    /** 同上，画布矩形与鼠标直接给数。 */
    public static LayoutNode pickScroller(LayoutNode root, int x, int y, int w, int h, double mx, double my,
                                          TextMeasure tm) {
        return find(root, x, y, mx, my, x, y, (long) x + w, (long) y + h, tm, true, null);
    }

    /** pick 命中的节点连同它的全部祖先；没命中是空集。 */
    public static Set<LayoutNode> hoverChain(LayoutNode root, int x, int y, int w, int h, double mx, double my,
                                             TextMeasure tm) {
        Set<LayoutNode> chain = new HashSet<>();
        find(root, x, y, mx, my, x, y, (long) x + w, (long) y + h, tm, false, chain);
        return chain;
    }

    /** 滚一格滚轮，amount 为正是往上。滚到头返回 false，把事件留给手机页面（§8.7）。 */
    public static boolean scroll(LayoutNode scroller, double amount) {
        int before = Renderer.clampScroll(scroller);
        long next = before - (long) (amount * SCROLL_STEP);
        scroller.scrollY = (int) Math.max(0, Math.min(next, scroller.scrollMax()));
        return scroller.scrollY != before;
    }

    /** tab-bar 上 mx 落在第几段。切法与 Renderer 画的一样：等宽，余数给最后一段。 */
    public static int segmentAt(LayoutNode tabBar, PhoneCanvas c, double mx) {
        return segmentAt(tabBar, c.x(), Math.round(mx));
    }

    /** 同上，ox 是画布左边界。 */
    public static int segmentAt(LayoutNode tabBar, int ox, double mx) {
        int count = Renderer.tabs(tabBar).size();
        int segW = count == 0 ? 0 : tabBar.w / count;
        if (segW <= 0) return 0;
        long i = (long) Math.floor((mx - ((long) tabBar.x + ox)) / segW);
        return (int) Math.max(0, Math.min(count - 1, i));
    }

    /**
     * (mx, my) 处最上面那个要找的节点：scrollers 为 true 找 scroll / list，否则找可交互节点。chain 不为 null 时收下命中节点与祖先。
     *
     * <p>只有 scroll / list 按自己的矩形剪掉子树。别的容器不裁剪，溢出去的子节点画在外面，也就得点得到。
     */
    private static LayoutNode find(LayoutNode n, int ox, int oy, double mx, double my,
                                   long clipL, long clipT, long clipR, long clipB,
                                   TextMeasure tm, boolean scrollers, Set<LayoutNode> chain) {
        if (mx < clipL || mx >= clipR || my < clipT || my >= clipB) return null;
        long x = (long) n.x + ox;
        long y = (long) n.y + oy;
        boolean inSelf = n.w > 0 && n.h > 0 && mx >= x && mx < x + n.w && my >= y && my < y + n.h;
        boolean scroller = Renderer.isScroller(n);
        LayoutNode hit = null;
        if (scroller) {
            if (!inSelf) return null;
            int sy = Renderer.clampScroll(n);
            int[] range = Renderer.childRange(n, sy);
            for (int i = range[1]; i >= range[0] && hit == null; i--) {
                LayoutEngine.layoutItem(n, i, tm);
                hit = find(n.children.get(i), ox, oy - sy, mx, my, Math.max(clipL, x), Math.max(clipT, y),
                        Math.min(clipR, x + n.w), Math.min(clipB, y + n.h), tm, scrollers, chain);
            }
        } else {
            for (int i = n.children.size() - 1; i >= 0 && hit == null; i--) {
                hit = find(n.children.get(i), ox, oy, mx, my, clipL, clipT, clipR, clipB, tm, scrollers, chain);
            }
        }
        if (hit == null && inSelf && (scrollers ? scroller : n.node.type().interactive)) hit = n;
        if (hit != null && chain != null) chain.add(n);
        return hit;
    }
}
