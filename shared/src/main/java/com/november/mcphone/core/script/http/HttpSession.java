package com.november.mcphone.core.script.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One open App's requests. submit/poll/close belong to the UI thread; workers never run script code. */
public final class HttpSession implements AutoCloseable {
    @FunctionalInterface public interface Transport {
        CompletableFuture<HttpRequests.Result> send(HttpRequests.Request request);
    }

    private static final class Flight {
        final int id;
        final CompletableFuture<HttpRequests.Result> future;
        final Consumer<HttpRequests.Result> receiver;

        Flight(int id, CompletableFuture<HttpRequests.Result> future, Consumer<HttpRequests.Result> receiver) {
            this.id = id;
            this.future = future;
            this.receiver = receiver;
        }
    }
    private final List<Flight> pending = new ArrayList<>();
    private final BooleanSupplier active;
    private final Transport transport;
    private boolean closed;
    private boolean polling;

    public HttpSession(BooleanSupplier active) { this(active, ScriptHttpTransport::send); }

    public HttpSession(BooleanSupplier active, Transport transport) {
        this.active = active;
        this.transport = transport;
    }

    public int available() {
        if (closed || !active.getAsBoolean()) { close(); return 0; }
        return HttpRequests.MAX_PENDING - pending.size();
    }

    public void submit(HttpRequests.Request request, Consumer<HttpRequests.Result> receiver) {
        if (available() == 0) return;
        CompletableFuture<HttpRequests.Result> future;
        try { future = transport.send(request); }
        catch (RuntimeException e) { future = CompletableFuture.completedFuture(new HttpRequests.Result(0, "", "NETWORK_ERROR")); }
        pending.add(new Flight(request.id(), future, receiver));
    }

    Set<Integer> requestIds() {
        return Collections.unmodifiableSet(pending.stream().map(flight -> flight.id).filter(id -> id > 0).collect(Collectors.toSet()));
    }

    /** UI-thread only. Remove before cancelling so even an already completed future cannot deliver later. */
    public void cancel(int id) {
        for (Flight flight : new ArrayList<>(pending)) {
            if (flight.id == id && id > 0 && pending.remove(flight)) flight.future.cancel(true);
        }
    }

    /** Deliver a bounded snapshot; callback-created requests cannot recurse or run until another poll. */
    public void poll() {
        if (closed || !active.getAsBoolean()) { close(); return; }
        if (polling) return;
        polling = true;
        try {
            for (Flight flight : new ArrayList<>(pending)) {
                if (closed || !active.getAsBoolean()) { close(); break; }
                if (!flight.future.isDone() || !pending.remove(flight)) continue;
                HttpRequests.Result result;
                try { result = flight.future.join(); }
                catch (RuntimeException e) { result = new HttpRequests.Result(0, "", "NETWORK_ERROR"); }
                flight.receiver.accept(result);
            }
        } finally { polling = false; }
    }

    @Override public void close() {
        closed = true;
        List<Flight> flights = new ArrayList<>(pending);
        pending.clear();
        for (Flight flight : flights) flight.future.cancel(true);
    }

}
