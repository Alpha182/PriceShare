package dev.priceshare.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The upload thread against a fake DonutMarket on localhost. */
class UploaderTest {
    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> auths = new CopyOnWriteArrayList<>();
    private final AtomicInteger uploadReplies = new AtomicInteger();
    private volatile int[] uploadCodes = {200};
    private volatile int[] challengeCodes = {200};
    private final AtomicInteger challengeReplies = new AtomicInteger();
    private final List<Long> challengeTimes = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(path);
        String reply;
        int code = 200;
        switch (path) {
            case "/api/share/challenge" -> {
                challengeTimes.add(System.currentTimeMillis());
                code = challengeCodes[Math.min(challengeReplies.getAndIncrement(), challengeCodes.length - 1)];
                reply = code == 200 ? "{\"serverId\":\"abc123\",\"expiresIn\":60}" : "<html>nope</html>";
            }
            case "/api/share/login" -> reply = body.contains("\"serverId\":\"abc123\"") ? "{\"token\":\"tok-" + calls.size() + "\",\"uuid\":\"u\",\"name\":\"Tester\"}" : "{}";
            case "/api/share/donut" -> {
                auths.add(ex.getRequestHeaders().getFirst("Authorization"));
                bodies.add(body);
                int n = uploadReplies.getAndIncrement();
                code = uploadCodes[Math.min(n, uploadCodes.length - 1)];
                reply = code == 429 ? "{\"data\":{\"retryAfter\":1}}" : "{\"ok\":true,\"accepted\":1,\"wait\":0}";
            }
            default -> { code = 404; reply = "{}"; }
        }
        byte[] out = reply.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    static final String UUID_A = "0123456789abcdef0123456789abcdef";
    static final String UUID_B = "fedcba9876543210fedcba9876543210";

    private static final class FakeSession implements Uploader.Session {
        final List<String> joined = new CopyOnWriteArrayList<>();
        volatile String uuid = UUID_A;
        public String name() { return "Tester"; }
        public String uuid() { return uuid; }
        /** Join attempts that fail with Mojang's 429 before one succeeds. */
        volatile int failJoins;
        final List<Long> joinTimes = new CopyOnWriteArrayList<>();

        public void join(String serverId) throws Uploader.JoinException {
            joinTimes.add(System.currentTimeMillis());
            if (failJoins > 0) {
                failJoins--;
                throw new Uploader.JoinException(429);
            }
            joined.add(serverId);
        }
    }

    private static Page page(int n) {
        return page(n, System.currentTimeMillis());
    }

    private static Page page(int n, long at) {
        return new Page(Menus.Kind.AH, at, "Auction (Page " + n + ")",
                List.of(new Slot(0, "diamond", 64, "Diamond", List.of("$ 1.2M"), Map.of(), null)));
    }

    private static void waitFor(java.util.function.BooleanSupplier ok) throws InterruptedException {
        for (int i = 0; i < 200 && !ok.getAsBoolean(); i++) Thread.sleep(50);
        assertTrue(ok.getAsBoolean(), "timed out");
    }

    @Test
    void logsInWithMojangJoinThenUploads(@TempDir Path dir) throws Exception {
        String url = start();
        Config cfg = new Config(dir);
        cfg.answer(true);
        cfg.setShowOnSite(true);
        FakeSession s = new FakeSession();
        Uploader up = new Uploader(cfg, url, "1.0.0", "26.2", s);
        up.offer(page(1));
        up.offer(page(2));
        waitFor(() -> !bodies.isEmpty());
        assertEquals(List.of("/api/share/challenge", "/api/share/login", "/api/share/donut"), calls.subList(0, 3));
        assertEquals(List.of("abc123"), s.joined, "the code is joined through Mojang, nothing else");
        assertEquals("Bearer tok-2", auths.get(0));
        Object b = Json.parse(bodies.get(0));
        assertEquals(1.0, Json.num(b, "v", 0));
        assertEquals("26.2", Json.str(b, "mc"));
        assertTrue(Json.bool(b, "showOnSite", false));
        List<?> pages = (List<?>) ((Map<?, ?>) b).get("pages");
        assertEquals(2, pages.size(), "both pages in one batch");
        assertEquals("tok-2", cfg.tokenFor(UUID_A), "token kept for next time, for this account");
    }

    @Test
    void reLogsInOn401AndBacksOffOn429(@TempDir Path dir) throws Exception {
        String url = start();
        Config cfg = new Config(dir);
        cfg.answer(true);
        cfg.setToken(UUID_A, "old");
        uploadCodes = new int[]{401, 429, 200};
        FakeSession s = new FakeSession();
        Uploader up = new Uploader(cfg, url, "1.0.0", "26.2", s);
        up.offer(page(1));
        waitFor(() -> bodies.size() >= 2);
        assertEquals("Bearer old", auths.get(0));
        assertTrue(auths.get(1).startsWith("Bearer tok-"), "logged in again after 401");
        assertEquals(1, s.joined.size());
        long t429 = System.currentTimeMillis();
        up.offer(page(2));
        waitFor(() -> bodies.size() >= 3);
        assertTrue(System.currentTimeMillis() - t429 >= Uploader.MIN_GAP_MS - 200, "waited after 429");
    }

    @Test
    void switchingAccountsLogsInAgain(@TempDir Path dir) throws Exception {
        String url = start();
        Config cfg = new Config(dir);
        cfg.answer(true);
        cfg.setToken(UUID_A, "token-of-a");
        FakeSession s = new FakeSession();
        s.uuid = UUID_B; // the launcher now plays account B
        Uploader up = new Uploader(cfg, url, "1.0.0", "26.2", s);
        up.offer(page(1));
        waitFor(() -> !bodies.isEmpty());
        assertEquals(List.of("/api/share/challenge", "/api/share/login", "/api/share/donut"), calls.subList(0, 3));
        assertNotEquals("Bearer token-of-a", auths.get(0), "A's token is never sent for B");
        assertEquals(1, s.joined.size());
        assertNull(cfg.tokenFor(UUID_A));
        assertNotNull(cfg.tokenFor(UUID_B));
    }

    /** Short waits so the tests run in seconds: 300/600/900 ms for Mojang, 1.5 s for a site refusal. */
    private static final Uploader.Timing FAST = new Uploader.Timing(50, new long[]{300, 600, 900}, 1_500);

    private static List<String> titles(List<String> uploadBodies) {
        List<String> out = new java.util.ArrayList<>();
        for (String b : uploadBodies) {
            for (Object p : (List<?>) ((Map<?, ?>) Json.parse(b)).get("pages")) out.add(Json.str(p, "title"));
        }
        return out;
    }

    @Test
    void backoffSchedule() {
        Uploader.Timing t = Uploader.Timing.DEFAULT;
        assertEquals(90_000, t.joinBackoff(1));
        assertEquals(180_000, t.joinBackoff(2));
        assertEquals(600_000, t.joinBackoff(3));
        assertEquals(600_000, t.joinBackoff(9), "10 min at most");
        assertEquals(600_000, t.siteRefusedMs());
        assertEquals("90 s", Uploader.seconds(90_000));
        assertEquals("3 min", Uploader.seconds(180_000));
    }

    @Test
    void mojang429BacksOffThenSendsTheHeldPages(@TempDir Path dir) throws Exception {
        String url = start();
        Config cfg = new Config(dir);
        cfg.answer(true);
        FakeSession s = new FakeSession();
        s.failJoins = 2;
        Uploader up = new Uploader(cfg, url, "1.0.1", "26.2", s, FAST);
        for (int i = 1; i <= 3; i++) up.offer(page(i));
        waitFor(() -> !bodies.isEmpty());
        assertEquals(3, s.joinTimes.size(), "two refused joins, then one that worked");
        assertTrue(s.joinTimes.get(1) - s.joinTimes.get(0) >= 300 - 20, "first wait 300 ms");
        assertTrue(s.joinTimes.get(2) - s.joinTimes.get(1) >= 600 - 20, "second wait 600 ms");
        assertEquals(List.of("Auction (Page 1)", "Auction (Page 2)", "Auction (Page 3)"), titles(bodies),
                "pages captured before the login are sent after it");
    }

    @Test
    void holdsThe30NewestPagesWhileWaiting(@TempDir Path dir) throws Exception {
        String url = start();
        Config cfg = new Config(dir);
        cfg.answer(true);
        FakeSession s = new FakeSession();
        s.failJoins = 1_000;
        Uploader up = new Uploader(cfg, url, "1.0.1", "26.2", s, FAST);
        up.offer(page(0, System.currentTimeMillis() - Uploader.MAX_AGE_MS - 1)); // too old to keep
        for (int i = 1; i <= 35; i++) up.offer(page(i));
        assertEquals(30, up.queued());
        waitFor(() -> !s.joinTimes.isEmpty());
        assertTrue(bodies.isEmpty(), "nothing sent before the account is verified");
        s.failJoins = 0;
        waitFor(() -> bodies.size() >= 3);
        List<String> sent = titles(bodies);
        assertEquals(30, sent.size());
        assertEquals("Auction (Page 6)", sent.get(0), "the 5 oldest were dropped");
        assertEquals("Auction (Page 35)", sent.get(29));
        assertFalse(sent.contains("Auction (Page 0)"));
        assertEquals(0, up.queued());
    }

    @Test
    void siteRefusingTheChallengeWaitsTheLongGap(@TempDir Path dir) throws Exception {
        String url = start();
        Config cfg = new Config(dir);
        cfg.answer(true);
        challengeCodes = new int[]{403, 200};
        FakeSession s = new FakeSession();
        Uploader up = new Uploader(cfg, url, "1.0.1", "26.2", s, FAST);
        up.offer(page(1));
        waitFor(() -> !bodies.isEmpty());
        assertEquals(2, challengeTimes.size());
        assertTrue(challengeTimes.get(1) - challengeTimes.get(0) >= 1_500 - 20, "site refusal: the long wait, not Mojang's");
        assertEquals(1, s.joinTimes.size(), "no join after the site refused");
    }

    @Test
    void sendsNothingWhenOff(@TempDir Path dir) throws Exception {
        String url = start();
        Config cfg = new Config(dir);
        Uploader up = new Uploader(cfg, url, "1.0.0", "26.2", new FakeSession());
        up.offer(page(1));
        Thread.sleep(500);
        assertTrue(calls.isEmpty());
    }
}
