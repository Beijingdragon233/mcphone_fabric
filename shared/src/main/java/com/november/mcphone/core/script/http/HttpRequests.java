package com.november.mcphone.core.script.http;

import java.net.URI;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Plain bounded values only. A batch is published after the enclosing state/store transaction commits. */
public final class HttpRequests implements AutoCloseable {
    public static final int MAX_PENDING = 4;
    public static final int MAX_BODY_BYTES = 64 * 1024;
    public static final int DEFAULT_TIMEOUT_MS = 30_000;
    // Never reuse IDs while this client is running, including across App closes and account changes.
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Set<String> RESTRICTED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("connection", "content-length", "expect", "host",
            "upgrade", "transfer-encoding", "proxy-authorization", "proxy-connection")));

    public static final class Request {
        private final String url;
        private final String method;
        private final Map<String, String> headers;
        private final String body;
        private final int timeoutMs;
        private final String callback;
        private final int id;
        public Request(String url, String method, Map<String, String> headers, String body,
                       int timeoutMs, String callback) {
            this(url, method, headers, body, timeoutMs, callback, 0);
        }

        public Request(String url, String method, Map<String, String> headers, String body, int timeoutMs, String callback, int id) {
            if (id < 0) throw invalid("请求编号不能为负数");
            if (url == null || url.length() > 2048) throw invalid("URL 最多 2048 个字符");
            URI uri;
            try { uri = URI.create(url); }
            catch (IllegalArgumentException e) { throw invalid("URL 不合法"); }
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw invalid("只接受完整 HTTP/HTTPS URL，不能包含账号或片段");
            }
            if (!"GET".equals(method) && !"POST".equals(method)) throw invalid("请求方法只支持 GET 和 POST");
            if (body == null || body.length() > MAX_BODY_BYTES || utf8Bytes(body) > MAX_BODY_BYTES) {
                throw invalid("请求正文最多 64 KiB UTF-8 字节");
            }
            if ("GET".equals(method) && !body.isEmpty()) throw invalid("GET 不能携带正文");
            if (timeoutMs < 1000 || timeoutMs > 120_000) throw invalid("超时需为 1000–120000 毫秒的整数");
            if (callback == null || !callback.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) throw invalid("回调函数名不合法");
            if (headers == null || headers.size() > 16) throw invalid("请求头最多 16 项");
            Map<String, String> checked = new LinkedHashMap<>();
            int bytes = 0;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey(), value = entry.getValue();
                if (key == null || key.length() > 64 || !key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                    throw invalid("请求头名称不合法");
                }
                key = key.toLowerCase(Locale.ROOT);
                if (RESTRICTED.contains(key) || checked.containsKey(key)) throw invalid("请求头重复或由宿主管理");
                if (value == null || value.length() > 4096 || value.chars().anyMatch(c -> c < 32 || c > 255 || c == 127)) {
                    throw invalid("请求头值只接受无控制字符的 Latin-1 文本，最多 4096 字符");
                }
                bytes += key.length() + value.length();
                if (bytes > 8192) throw invalid("请求头合计最多 8 KiB");
                checked.put(key, value);
            }
            if ("POST".equals(method)) checked.putIfAbsent("content-type", "application/json; charset=utf-8");
            if (checked.size() > 16 || checked.entrySet().stream().mapToInt(e -> e.getKey().length() + e.getValue().length()).sum() > 8192) {
                throw invalid("含默认 Content-Type 的请求头最多 16 项、合计 8 KiB");
            }
            // Validate with the actual JDK parser now, before state or storage is committed. No I/O here.
            try {
                ScriptHttpTransport.validate(uri, checked);
            } catch (IllegalArgumentException e) { throw invalid("URL 或请求头不被 HTTP 客户端接受"); }
            this.headers = Collections.unmodifiableMap(checked);
            this.url = url;
            this.method = method;
            this.body = body;
            this.timeoutMs = timeoutMs;
            this.callback = callback;
            this.id = id;
        }

        public String url() { return url; }
        public String method() { return method; }
        public Map<String, String> headers() { return headers; }
        public String body() { return body; }
        public int timeoutMs() { return timeoutMs; }
        public String callback() { return callback; }
        public int id() { return id; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Request)) return false;
            Request r = (Request) other;
            return id == r.id && timeoutMs == r.timeoutMs && url.equals(r.url) && method.equals(r.method)
                    && headers.equals(r.headers) && body.equals(r.body) && callback.equals(r.callback);
        }

        @Override public int hashCode() {
            int hash = url.hashCode();
            hash = hash * 31 + method.hashCode();
            hash = hash * 31 + headers.hashCode();
            hash = hash * 31 + body.hashCode();
            hash = hash * 31 + timeoutMs;
            hash = hash * 31 + callback.hashCode();
            return hash * 31 + id;
        }

        @Override public String toString() {
            return "Request[url=" + url + ", method=" + method + ", headers=" + headers + ", body=" + body
                    + ", timeoutMs=" + timeoutMs + ", callback=" + callback + ", id=" + id + "]";
        }
    }

    /** HTTP errors retain status/body; transport errors use status 0 and a stable, non-sensitive code. */
    public static final class Result {
        private final int status;
        private final String body;
        private final String error;

        public Result(int status, String body, String error) {
            this.status = status;
            this.body = body;
            this.error = error;
        }

        public int status() { return status; }
        public String body() { return body; }
        public String error() { return error; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Result)) return false;
            Result r = (Result) other;
            return status == r.status && Objects.equals(body, r.body) && Objects.equals(error, r.error);
        }

        @Override public int hashCode() {
            return (status * 31 + Objects.hashCode(body)) * 31 + Objects.hashCode(error);
        }

        @Override public String toString() {
            return "Result[status=" + status + ", body=" + body + ", error=" + error + "]";
        }
    }

    private final int capacity;
    private final Consumer<List<Request>> sender;
    private final HttpSession session;
    private final Set<Integer> cancellable;
    private final Set<Integer> cancellations = new LinkedHashSet<>();
    private final List<Request> staged = new ArrayList<>();
    private boolean closed;

    public HttpRequests(int capacity, Consumer<List<Request>> sender) {
        this(capacity, sender, null);
    }

    /** Snapshot owned flights on the UI thread; cancellation is published only with a successful transaction. */
    public HttpRequests(HttpSession session, Consumer<List<Request>> sender) {
        this(session == null ? 0 : session.available(), sender, session);
    }

    private HttpRequests(int capacity, Consumer<List<Request>> sender, HttpSession session) {
        this.capacity = Math.max(0, Math.min(MAX_PENDING, capacity));
        this.sender = sender;
        this.session = session;
        this.cancellable = session == null ? Collections.emptySet() : session.requestIds();
    }

    /** Retain the original internal caller signature; scripts use enqueue to obtain the ID. */
    public void add(Request request) { enqueue(request); }

    public int enqueue(Request request) {
        if (closed || sender == null) throw invalid("当前页面没有可用的网络请求宿主");
        if (staged.size() >= capacity + cancellations.size()) throw invalid("当前 App 最多同时等待 4 个请求，请等待已有请求完成");
        int id = NEXT_ID.getAndUpdate(value -> value == Integer.MAX_VALUE ? value : value + 1);
        if (id == Integer.MAX_VALUE) throw invalid("请求编号已用尽，请重启游戏");
        staged.add(new Request(request.url(), request.method(), request.headers(), request.body(),
                request.timeoutMs(), request.callback(), id));
        return id;
    }

    /** Unknown/completed IDs (and the initial state value 0) are harmless no-ops. */
    public void cancel(int id) {
        if (closed || sender == null) throw invalid("当前页面没有可用的网络请求宿主");
        if (id < 0) throw invalid("请求编号须为非负的 32 位整数");
        if (!staged.removeIf(request -> request.id() == id) && cancellable.contains(id)) cancellations.add(id);
    }

    public void commit() {
        if (closed) throw new IllegalStateException("请求批次已经结束");
        List<Request> requests = Collections.unmodifiableList(new ArrayList<>(staged));
        Set<Integer> cancelled = new LinkedHashSet<>(cancellations);
        close();
        if (session != null) for (int id : cancelled) session.cancel(id);
        if (!requests.isEmpty()) sender.accept(requests);
    }

    @Override public void close() { closed = true; staged.clear(); cancellations.clear(); }

    private static int utf8Bytes(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw invalid("正文包含不完整的 Unicode 字符");
            } else if (Character.isLowSurrogate(c)) throw invalid("正文包含不完整的 Unicode 字符");
        }
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
