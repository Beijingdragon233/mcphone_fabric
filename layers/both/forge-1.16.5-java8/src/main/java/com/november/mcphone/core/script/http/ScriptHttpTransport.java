package com.november.mcphone.core.script.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;

/** Uses the HTTP client bundled with Minecraft 1.16.5. Network threads never call scripts. */
final class ScriptHttpTransport {
    private ScriptHttpTransport() {}

    private static final class Network {
        static final int CONCURRENCY = 16;
        static final Semaphore SLOTS = new Semaphore(CONCURRENCY);
        static final ExecutorService WORKERS = Executors.newFixedThreadPool(CONCURRENCY, r -> daemon(r, "mcphone-http"));
        static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, r -> daemon(r, "mcphone-http-deadline"));
        static final CloseableHttpClient CLIENT = HttpClients.custom()
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                .disableContentCompression().disableAuthCaching()
                .setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()))
                .setMaxConnTotal(CONCURRENCY).setMaxConnPerRoute(CONCURRENCY).build();
        static { TIMER.setRemoveOnCancelPolicy(true); }
    }

    private static Thread daemon(Runnable job, String name) {
        Thread t = new Thread(job, name);
        t.setDaemon(true);
        return t;
    }

    static void validate(URI uri, Map<String, String> headers) {
        // HttpRequests validates the URI authority, method and HTTP header grammar.
        new HttpGet(uri);
    }

    static CompletableFuture<HttpRequests.Result> send(HttpRequests.Request request) {
        HttpRequestBase wire = prepare(request);
        if (!Network.SLOTS.tryAcquire()) return CompletableFuture.completedFuture(error("BUSY"));
        CompletableFuture<HttpRequests.Result> result = new CompletableFuture<>();
        ScheduledFuture<?> deadline = Network.TIMER.schedule(
                () -> result.complete(error("TIMEOUT")), request.timeoutMs(), TimeUnit.MILLISECONDS);
        result.whenComplete((value, failure) -> {
            deadline.cancel(false);
            // Abort shuts down the active connection and interrupts an outstanding body read.
            if (result.isCancelled() || value != null && "TIMEOUT".equals(value.error())) wire.abort();
        });
        try {
            Network.WORKERS.execute(() -> {
                try {
                    if (!result.isDone()) result.complete(exchange(wire));
                } catch (SocketTimeoutException | ConnectTimeoutException e) {
                    result.complete(error("TIMEOUT"));
                } catch (CharacterCodingException e) {
                    result.complete(error("INVALID_UTF8"));
                } catch (IOException | RuntimeException e) {
                    result.complete(error("NETWORK_ERROR"));
                } finally {
                    Network.SLOTS.release();
                }
            });
        } catch (RejectedExecutionException e) {
            Network.SLOTS.release();
            result.complete(error("NETWORK_ERROR"));
        }
        return result;
    }

    private static HttpRequestBase prepare(HttpRequests.Request request) {
        URI uri = URI.create(request.url());
        HttpRequestBase wire;
        if ("POST".equals(request.method())) {
            HttpPost post = new HttpPost(uri);
            post.setEntity(new ByteArrayEntity(request.body().getBytes(StandardCharsets.UTF_8)));
            wire = post;
        } else {
            wire = new HttpGet(uri);
        }
        wire.setConfig(RequestConfig.custom().setConnectTimeout(Math.min(10_000, request.timeoutMs()))
                .setConnectionRequestTimeout(request.timeoutMs()).setSocketTimeout(request.timeoutMs())
                .setAuthenticationEnabled(false).build());
        request.headers().forEach(wire::setHeader);
        return wire;
    }

    private static HttpRequests.Result exchange(HttpRequestBase wire) throws IOException {
        try (CloseableHttpResponse response = Network.CLIENT.execute(wire)) {
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (entity != null) {
                // The response owns the stream. Closing the stream early can drain the remaining body.
                InputStream in = entity.getContent();
                byte[] chunk = new byte[4096];
                int count;
                while ((count = in.read(chunk)) != -1) {
                    if (count > HttpRequests.MAX_BODY_BYTES - bytes.size()) {
                        wire.abort();
                        return error("RESPONSE_TOO_LARGE");
                    }
                    bytes.write(chunk, 0, count);
                }
            }
            String body = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            return new HttpRequests.Result(status, body, status >= 200 && status < 300 ? "" : "HTTP_STATUS");
        }
    }

    private static HttpRequests.Result error(String code) {
        return new HttpRequests.Result(0, "", code);
    }
}
