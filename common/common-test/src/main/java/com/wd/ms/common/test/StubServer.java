package com.wd.ms.common.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用的"假下游服务"：用 JDK 自带的 HttpServer，不引入 WireMock 之类的依赖。
 *
 * <p>能做的事：按路径前缀返回指定的状态码和 JSON、给响应加延迟（模拟服务变慢）、统计每个路径收到的请求数、
 * 记录收到的请求体（契约测试用来比对"消费方实际发出的请求"）。/actuator/health 默认返回 UP，负载均衡的健康检查能通过。
 */
public final class StubServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();

    private StubServer(HttpServer server) {
        this.server = server;
    }

    public static StubServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            StubServer stub = new StubServer(server);
            server.createContext("/", stub::handle);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            stub.respond("/actuator/health", 200, "{\"status\":\"UP\"}");
            return stub;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "http://127.0.0.1:" + port();
    }

    /** 路径以 pathPrefix 开头的请求，返回 status 和 body */
    public StubServer respond(String pathPrefix, int status, String body) {
        return respond(pathPrefix, status, body, 0);
    }

    public StubServer respond(String pathPrefix, int status, String body, long delayMillis) {
        routes.put(pathPrefix, new Route(status, body, delayMillis));
        return this;
    }

    public int count(String pathPrefix) {
        return counts.getOrDefault(pathPrefix, new AtomicInteger()).get();
    }

    public List<Received> received() {
        return List.copyOf(received);
    }

    public void reset() {
        counts.clear();
        received.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Route route = routes.entrySet().stream()
                .filter(entry -> path.startsWith(entry.getKey()))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(new Route(404, "{\"code\":404,\"message\":\"stub 没有配置这个路径\",\"data\":null}", 0));
        routes.keySet().stream()
                .filter(path::startsWith)
                .forEach(prefix -> counts.computeIfAbsent(prefix, key -> new AtomicInteger())
                        .incrementAndGet());
        received.add(new Received(
                exchange.getRequestMethod(),
                path,
                exchange.getRequestURI().getQuery(),
                body,
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Gray")));
        if (route.delayMillis() > 0) {
            try {
                Thread.sleep(route.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = route.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(route.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record Route(int status, String body, long delayMillis) {}

    /** 收到的一个请求 */
    public record Received(String method, String path, String query, String body, String authorization, String gray) {}
}
