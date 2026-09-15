package com.november.mcphone.core.script.sfc;

import com.november.mcphone.core.script.layout.Node;
import com.november.mcphone.core.script.layout.NodeParser;
import com.november.mcphone.core.script.layout.NodeType;
import com.november.mcphone.core.script.layout.UiState;
import com.november.mcphone.core.script.sfc.CompiledTemplate.BoundProp;
import com.november.mcphone.core.script.sfc.CompiledTemplate.Chain;
import com.november.mcphone.core.script.sfc.CompiledTemplate.Child;
import com.november.mcphone.core.script.sfc.CompiledTemplate.Element;
import com.november.mcphone.core.script.sfc.CompiledTemplate.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 一页模板的实例化（施工方案 §9.9）。每次重排调一次 {@link #instantiate}：v-if 过滤、v-for 展开成真实的兄弟节点、
 * 绑定求值，产出一棵普通的 Node 树 —— 布局引擎永远看到静态树，不知道 v-for 存在，单遍布局才成立。
 *
 * <p>点击动作不进 Node：{@code @click} 的值是语句，§4.5 的 onClick 装不下。按节点对象查 {@link #clickOf}，
 * 所以要用同一次 instantiate 产出的节点去查。
 */
public final class TemplateInstance {

    public static final int MAX_FOR_ITEMS = 2048;

    private static final PropRules.Spec TEXT = PropRules.of(NodeType.TEXT).get("text");

    private final CompiledTemplate compiled;
    private final String file;
    private Map<Node, Statements.Bound> clicks = new IdentityHashMap<>();
    private List<String> warnings = List.of();
    private boolean truncated;

    public TemplateInstance(CompiledTemplate compiled, String file) {
        this.compiled = Objects.requireNonNull(compiled, "compiled");
        this.file = Objects.requireNonNull(file, "file");
    }

    /** 一次实例化的现场：节点预算、已用过的 id、点击表。 */
    private static final class Pass {
        int budget = NodeParser.MAX_NODES;
        boolean truncated;
        final Set<String> ids = new HashSet<>();
        final Map<Node, Statements.Bound> clicks = new IdentityHashMap<>();
    }

    /** 按当前 state 产出 Node 树。不抛：超限截断、求值出错降级，原因见 {@link #warnings()}。 */
    public Node instantiate(UiState state) {
        EvalContext c = new EvalContext(state.values(), file);
        Pass pass = new Pass();
        Node root = node(compiled.root(), c, pass, null);
        if (pass.truncated) c.warn("节点超过 " + NodeParser.MAX_NODES + " 个，多出来的没有进树");
        clicks = pass.clicks;
        warnings = c.warnings();
        truncated = pass.truncated;
        return root;
    }

    /** 这个节点的 @click，没有返回 null。toggle / tab-bar 的 bind 写入之后再执行它（§9.4.6）。 */
    public Statements.Bound clickOf(Node node) {
        return clicks.get(node);
    }

    /** 上一次实例化的 warn，去重后最多 {@value EvalContext#MAX_WARNINGS} 条。 */
    public List<String> warnings() {
        return warnings;
    }

    public boolean truncated() {
        return truncated;
    }

    public String file() {
        return file;
    }

    // ============================================================

    /** 预算用完时返回 null。siblingKeys 是同一次 v-for 展开里已经用过的 :key。 */
    private Node node(Element e, EvalContext c, Pass pass, Set<String> siblingKeys) {
        if (pass.budget <= 0) {
            pass.truncated = true;
            return null;
        }
        pass.budget--;
        String tag = e.type().json;

        Map<String, Object> props = new LinkedHashMap<>(e.staticProps());
        for (Map.Entry<String, BoundProp> b : e.boundProps().entrySet()) {
            Object v = PropRules.normalize(b.getValue().spec(), b.getValue().expr().run(c), c, tag);
            if (v != null) props.put(b.getKey(), v);
        }
        if (e.key() != null) {
            String key = Values.text(e.key().run(c));
            if (siblingKeys != null && !siblingKeys.add(key)) {
                c.warn("<" + tag + "> 的 :key '" + key + "' 在同一个 v-for 里重复，这一项按下标找回滚动位置");
            } else {
                props.put("key", key);
            }
        }

        String id = e.id();
        if (id != null && !pass.ids.add(id)) {
            c.warn("id '" + id + "' 在树里出现了不止一次，后面的去掉 id");
            id = null;
        }

        List<Node> kids = new ArrayList<>();
        for (Child ch : e.children()) expand(ch, c, pass, kids);

        Node out = new Node(e.type(), id, e.classes(), Collections.unmodifiableMap(props), List.copyOf(kids), null, null);
        if (e.click() != null) pass.clicks.put(out, new Statements.Bound(e.click(), c.boundNames(), c.boundValues()));
        return out;
    }

    private void expand(Child ch, EvalContext c, Pass pass, List<Node> out) {
        if (ch instanceof Chain chain) {
            for (int i = 0; i < chain.branches().size(); i++) {
                Expr.Compiled cond = chain.conditions().get(i);
                if (cond == null || Values.truthy(cond.run(c))) {
                    add(out, node(chain.branches().get(i), c, pass, null));
                    return;
                }
            }
        } else if (ch instanceof Text t) {
            if (pass.budget <= 0) {
                pass.truncated = true;
                return;
            }
            pass.budget--;
            Object text = t.literal() != null ? t.literal() : PropRules.normalize(TEXT, t.expr().run(c), c, "text");
            out.add(new Node(NodeType.TEXT, null, List.of(), Map.of("text", text), List.of(), null, null));
        } else {
            Element e = (Element) ch;
            if (e.vFor() == null) {
                add(out, node(e, c, pass, null));
            } else {
                forEach(e, c, pass, out);
            }
        }
    }

    /** v-for 先、v-if 后（对每一项判断，§9.4.4）。 */
    private void forEach(Element e, EvalContext c, Pass pass, List<Node> out) {
        CompiledTemplate.ForSpec spec = e.vFor();
        Object source = spec.source().run(c);
        List<?> items = null;
        int count;
        if (source instanceof List<?> list) {
            items = list;
            count = list.size();
        } else if (source instanceof Integer n) {
            count = Math.max(0, n);
        } else {
            // null 是数据还没到，不算错
            if (source != null) c.warn("v-for 的来源是 " + Values.kind(source) + "，要数组或整数，按空处理");
            count = 0;
        }
        if (count > MAX_FOR_ITEMS) {
            c.warn("v-for 有 " + count + " 项，截到 " + MAX_FOR_ITEMS);
            count = MAX_FOR_ITEMS;
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < count; i++) {
            if (pass.budget <= 0) {
                pass.truncated = true;
                return;
            }
            c.push(spec.item(), items != null ? items.get(i) : (Object) (i + 1));
            if (spec.index() != null) c.push(spec.index(), i);
            if (e.vIf() == null || Values.truthy(e.vIf().run(c))) add(out, node(e, c, pass, keys));
            if (spec.index() != null) c.pop();
            c.pop();
        }
    }

    private static void add(List<Node> out, Node n) {
        if (n != null) out.add(n);
    }
}
