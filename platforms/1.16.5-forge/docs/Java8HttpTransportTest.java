package com.november.mcphone.core.script.http;
import com.november.mcphone.core.script.http.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Java8HttpTransportTest {
    static HttpRequests.Result send(String url, int timeout) throws Exception {
        try (HttpSession s = new HttpSession(() -> true)) {
            AtomicReference<HttpRequests.Result> r = new AtomicReference<>();
            s.submit(new HttpRequests.Request(url, "GET", Collections.emptyMap(), "", timeout, "received"), r::set);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            while (r.get() == null && System.nanoTime() < deadline) { s.poll(); Thread.sleep(10); }
            if (r.get() == null) throw new AssertionError("reply missing");
            return r.get();
        }
    }
    static void serve(HttpExchange ex, int status, byte[] bytes) throws IOException {
        try { ex.sendResponseHeaders(status, bytes.length); ex.getResponseBody().write(bytes); } finally { ex.close(); }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        HttpServer h = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService ex = Executors.newCachedThreadPool(); h.setExecutor(ex);
        CountDownLatch inFlight = new CountDownLatch(1), release = new CountDownLatch(1);
        h.createContext("/ok", x -> serve(x, 200, new byte[] {79, 75}));
        h.createContext("/too-large", x -> serve(x, 200, new byte[65537]));
        h.createContext("/utf8", x -> serve(x, 200, new byte[] {(byte) 0xff}));
        h.createContext("/redirect", x -> { x.getResponseHeaders().add("Location", "/ok"); serve(x, 302, new byte[] {42}); });
        h.createContext("/error", x -> serve(x, 500, new byte[] {42}));
        h.createContext("/slow", x -> { try { inFlight.countDown(); release.await(); serve(x, 200, new byte[] {42}); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); x.close(); } });
        CountDownLatch bodyStarted = new CountDownLatch(16);
        h.createContext("/body-stall", x -> {
            try {
                x.sendResponseHeaders(200, 100);
                x.getResponseBody().write(42); x.getResponseBody().flush();
                bodyStarted.countDown(); release.await();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { x.close(); }
        });
        h.start();
        try {
            String url = "http://127.0.0.1:" + h.getAddress().getPort();
            check(send(url+"/ok", 2000).equals(new HttpRequests.Result(200, "OK", "")), "success");
            check(send(url+"/redirect", 2000).equals(new HttpRequests.Result(302, "*", "HTTP_STATUS")), "redirect");
            check(send(url+"/error", 2000).equals(new HttpRequests.Result(500, "*", "HTTP_STATUS")), "error status");
            check(send(url+"/too-large", 2000).error().equals("RESPONSE_TOO_LARGE"), "size limit");
            check(send(url+"/utf8", 2000).error().equals("INVALID_UTF8"), "UTF-8 validation");
            check(send(url+"/slow", 1000).error().equals("TIMEOUT"), "deadline");
            try (HttpSession s = new HttpSession(() -> true)) {
                s.submit(new HttpRequests.Request(url+"/slow", "GET", Collections.emptyMap(), "", 4000,"f",123), r -> { throw new AssertionError("callback after cancel"); });
                Thread.sleep(150);
                long start = System.nanoTime(); s.cancel(123);
                check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 500, "cancel blocks UI");
                s.poll(); check(s.available() == 4, "callback removed");
            }
            check(send(url+"/body-stall", 1000).error().equals("TIMEOUT"), "body deadline");
            // Fill the transport, expire every body read, then prove that all slots and connections recover.
            List<CompletableFuture<HttpRequests.Result>> stalled = new ArrayList<>();
            for (int i = 0; i < 16; i++) stalled.add(ScriptHttpTransport.send(new HttpRequests.Request(
                    url+"/body-stall", "GET", Collections.emptyMap(), "", 2000, "f")));
            check(bodyStarted.await(3, TimeUnit.SECONDS), "concurrent bodies reached server");
            for (CompletableFuture<HttpRequests.Result> f : stalled)
                check(f.get(4, TimeUnit.SECONDS).error().equals("TIMEOUT"), "concurrent deadline");
            HttpRequests.Result recovered = null;
            for (int i = 0; i < 50; i++) {
                recovered = send(url+"/ok", 2000);
                if (!"BUSY".equals(recovered.error())) break;
                Thread.sleep(20);
            }
            check(recovered != null && recovered.status() == 200, "slots/connections recover after deadlines");
            List<CompletableFuture<HttpRequests.Result>> cancelled = new ArrayList<>();
            for (int i = 0; i < 16; i++) cancelled.add(ScriptHttpTransport.send(new HttpRequests.Request(
                    url+"/body-stall", "GET", Collections.emptyMap(), "", 4000, "f")));
            for (CompletableFuture<HttpRequests.Result> f : cancelled) f.cancel(true);
            Thread.sleep(100);
            check(send(url+"/ok", 2000).status() == 200, "slots recover after cancellation");
            System.out.println("Java8HttpTransportTest PASS, Java " + System.getProperty("java.version"));
        } finally { release.countDown(); h.stop(0); ex.shutdownNow(); }
    }
}
