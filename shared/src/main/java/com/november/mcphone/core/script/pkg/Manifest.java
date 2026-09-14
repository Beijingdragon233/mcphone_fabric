package com.november.mcphone.core.script.pkg;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * manifest.json（施工方案 §3.2）。字段校验全部硬失败，没有"尽力而为"的分支。
 *
 * <p>{@code id} 的两段与 {@code ui} 的两条路径都拆开存：调用点再去切一次字符串，就会有第二份切法。
 */
public record Manifest(
        int format,
        String namespace,
        String path,
        String version,
        String name,
        String author,
        String description,
        String icon,
        String uiTree,
        String uiStyle,
        String engine) {

    /** 本轮只认这一个包格式版本。 */
    public static final int FORMAT = 1;

    /** 本轮只认这一个引擎。 */
    public static final String ENGINE = "declarative-1";

    /** 内建 App 的命名空间。第三方占了它，PhoneScreenRegistry 的 id 去重会把内建 App 挡在外面。 */
    public static final String RESERVED_NAMESPACE = "mcphone";

    /** JSON 嵌套深度上限。清单是一层对象加一个 ui 子对象，给到 8 已经宽得没边。 */
    private static final int MAX_JSON_DEPTH = 8;

    private static final Pattern ID_SEGMENT = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern PLAIN_INT = Pattern.compile("\\d+");

    private static final int MAX_NAME = 64;
    private static final int MAX_AUTHOR = 32;
    private static final int MAX_DESCRIPTION = 256;

    /** {@code namespace:path}，拼回去的那一个。 */
    public String id() {
        return namespace + ":" + path;
    }

    /** 解析并校验。任何一条不过就抛，不返回半个 Manifest。 */
    public static Manifest parse(String json) {
        strictScan(json);

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw PackageError.of(PackageError.Code.E_PKG_MANIFEST_NOT_OBJECT);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw PackageError.of(PackageError.Code.E_PKG_MANIFEST_SYNTAX, String.valueOf(e.getMessage()));
        }

        int format = requireInt(root, "format");
        if (format != FORMAT) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_FORMAT, format);
        }

        String id = requireString(root, "id");
        int colon = id.indexOf(':');
        if (colon < 0 || id.indexOf(':', colon + 1) >= 0) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_ID, id);
        }
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (!ID_SEGMENT.matcher(namespace).matches() || !ID_SEGMENT.matcher(path).matches()) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_ID, id);
        }
        if (RESERVED_NAMESPACE.equals(namespace)) {
            throw PackageError.of(PackageError.Code.E_PKG_RESERVED_NAMESPACE);
        }

        String version = requireString(root, "version");
        if (!VERSION.matcher(version).matches()) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_VERSION, version);
        }

        String name = text(root, "name", MAX_NAME);
        String author = text(root, "author", MAX_AUTHOR);
        String description = text(root, "description", MAX_DESCRIPTION);

        String icon = requirePath(root, "icon", "icon");

        JsonObject ui = requireObject(root, "ui");
        String uiTree = requirePath(ui, "tree", "ui.tree");
        String uiStyle = requirePath(ui, "style", "ui.style");

        String engine = requireString(root, "engine");
        if (!ENGINE.equals(engine)) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_ENGINE, engine);
        }

        return new Manifest(format, namespace, path, version, name, author, description,
                icon, uiTree, uiStyle, engine);
    }

    /** manifest 指到的三个文件都得真在包里，否则装上是个空壳。 */
    public void requireEntries(Collection<String> entryPaths) {
        requireEntry(entryPaths, "icon", icon);
        requireEntry(entryPaths, "ui.tree", uiTree);
        requireEntry(entryPaths, "ui.style", uiStyle);
    }

    private void requireEntry(Collection<String> entryPaths, String field, String value) {
        if (!entryPaths.contains(value)) {
            throw PackageError.of(PackageError.Code.E_PKG_MISSING_ENTRY, field, value);
        }
    }

    // ============================================================
    //  严格 JSON
    // ============================================================

    /**
     * 走一遍严格 JSON，顺手查重复键。
     *
     * <p>Gson 的 {@code JsonParser} 是宽容的：无引号的键、单引号、注释、{@code NaN} 全收，
     * 而重复键在 {@code JsonObject} 里是后者静默覆盖前者 —— 于是一份清单有两种读法，
     * 审核的人读到第一个 id，注册进去的是第二个。
     */
    private static void strictScan(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            scan(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw PackageError.of(PackageError.Code.E_PKG_MANIFEST_SYNTAX, "末尾还有多余的内容");
            }
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw PackageError.of(PackageError.Code.E_PKG_MANIFEST_SYNTAX, String.valueOf(e.getMessage()));
        }
    }

    private static void scan(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_DEPTH) {
            throw PackageError.of(PackageError.Code.E_PKG_MANIFEST_SYNTAX,
                    "嵌套深度超过 " + MAX_JSON_DEPTH);
        }
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> keys = new HashSet<>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (!keys.add(key)) {
                        throw PackageError.of(PackageError.Code.E_PKG_MANIFEST_DUP_KEY, key);
                    }
                    scan(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) scan(reader, depth + 1);
                reader.endArray();
            }
            // 数字按字符串吃掉：这里只管形状，值的范围留给 requireInt 报自己的码
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw PackageError.of(PackageError.Code.E_PKG_MANIFEST_SYNTAX,
                    "不该出现的记号 " + reader.peek());
        }
    }

    // ============================================================
    //  取值
    // ============================================================

    private static JsonElement require(JsonObject obj, String field, String label) {
        JsonElement e = obj.get(field);
        if (e == null || e.isJsonNull()) {
            throw PackageError.of(PackageError.Code.E_PKG_MISSING_FIELD, label);
        }
        return e;
    }

    private static int requireInt(JsonObject obj, String field) {
        JsonElement e = require(obj, field, field);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_TYPE, field, "整数", typeOf(e));
        }
        // 按字面量判，不按数值：1.0 与 1e0 数值上等于 1，但 §3.2 要的是整数 1。
        String raw = e.getAsJsonPrimitive().getAsString();
        if (!PLAIN_INT.matcher(raw).matches()) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_TYPE, field, "整数", "写成 " + raw + " 的数");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_TYPE, field, "整数", "超出范围的数");
        }
    }

    private static String requireString(JsonObject obj, String field) {
        JsonElement e = require(obj, field, field);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_TYPE, field, "字符串", typeOf(e));
        }
        return e.getAsString();
    }

    private static JsonObject requireObject(JsonObject obj, String field) {
        JsonElement e = require(obj, field, field);
        if (!e.isJsonObject()) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_TYPE, field, "对象", typeOf(e));
        }
        return e.getAsJsonObject();
    }

    /** 会显示给人看的文本字段：长度按代码点数，内容不许有看不见的东西。 */
    private static String text(JsonObject obj, String field, int max) {
        String v = requireString(obj, field);
        int len = v.codePointCount(0, v.length());
        if (len > max) throw PackageError.of(PackageError.Code.E_PKG_TEXT_TOO_LONG, field, max, len);
        for (int i = 0; i < v.length(); ) {
            int cp = v.codePointAt(i);
            if (invisible(cp)) {
                throw PackageError.of(PackageError.Code.E_PKG_TEXT_CONTROL_CHAR, field);
            }
            i += Character.charCount(cp);
        }
        return v;
    }

    /**
     * 控制字符、Unicode 换行、以及方向覆盖那一类格式字符。
     *
     * <p>只判 ASCII 不够：U+202E 能让 UI 上显示出来的名字和实际的名字不一样，U+200B 能把
     * 别人的作者名一字不差地伪装出来。作者名旁边不许有对勾（§12.3），那这一关就得在入口挡。
     */
    private static boolean invisible(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.SURROGATE
                || type == Character.UNASSIGNED;
    }

    private static String requirePath(JsonObject obj, String field, String label) {
        JsonElement e = require(obj, field, label);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw PackageError.of(PackageError.Code.E_PKG_BAD_TYPE, label, "字符串", typeOf(e));
        }
        String v = e.getAsString();
        PackageError.PathRules.require(v);
        return v;
    }

    private static String typeOf(JsonElement e) {
        if (e.isJsonObject()) return "对象";
        if (e.isJsonArray()) return "数组";
        if (e.isJsonNull()) return "null";
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return "布尔";
        if (p.isNumber()) return "数字";
        return "字符串";
    }
}
