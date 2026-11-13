package dev.priceshare.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends captured pages to the site from one background thread, in batches of at most 10, never
 * more often than every 5 seconds (or the wait the site asks for). Pages wait in memory (the 30
 * newest, at most 10 minutes old) until the player's account is verified; nothing is written to
 * disk except the upload token, and a batch the site won't take is dropped, not kept.
 */
public final class Uploader {
    /** What the uploader needs from the game: who is playing, and Mojang's join call. */
    public interface Session {
        String name();

        /** The player's UUID without dashes. */
        String uuid();

        /** Tells Mojang this player is joining {@code serverId}, as a server join does. Blocking. */
        void join(String serverId) throws Exception;
    }

    /** Mojang refused the session join; {@code status} is its HTTP status, or -1 if unknown. */
    public static final class JoinException extends Exception {
        public final int status;

        public JoinException(int status) {
            super(status > 0 ? "HTTP " + status : "no status");
            this.status = status;
        }
    }

    /** How long to wait between things. Tests shorten them. */
    record Timing(long minGapMs, long[] joinBackoffMs, long siteRefusedMs) {
        static final Timing DEFAULT = new Timing(5_000, new long[]{90_000, 180_000, 600_000}, 10 * 60_000);

        /** The wait after the {@code failures}th Mojang join failure in a row (1 = first). */
        long joinBackoff(int failures) {
            return joinBackoffMs[Math.min(Math.max(failures, 1), joinBackoffMs.length) - 1];
        }
    }

    public static final int BATCH = 10;
    /** Pages held at most, newest kept: while waiting for login, or behind a slow site. */
    static final int QUEUE_MAX = 30;
    /** Pages older than this are dropped rather than sent. */
    static final long MAX_AGE_MS = 10 * 60_000;
    static final long MIN_GAP_MS = Timing.DEFAULT.minGapMs();
    static final int ATTEMPTS = 3; // the first try plus two retries

    private final Config config;
    private final String base;
    private final String modVersion;
    private final String mcVersion;
    private final Session session;
    private final Timing timing;
    private final ArrayDeque<Page> queue = new ArrayDeque<>();
    private final HttpClient http;
    private final Thread thread;
    private volatile long notBefore;
    /** No login attempt before this. */
    private long loginNotBefore;
    /** Mojang join failures in a row, for the backoff. */
    private int joinFailures;
    private volatile String lastStatus = "nothing sent yet";
    private volatile int sentPages;

    public Uploader(Config config, String base, String modVersion, String mcVersion, Session session) {
        this(config, base, modVersion, mcVersion, session, Timing.DEFAULT);
    }

    Uploader(Config config, String base, String modVersion, String mcVersion, Session session, Timing timing) {
        this.config = config;
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.modVersion = modVersion;
        this.mcVersion = mcVersion;
        this.session = session;
        this.timing = timing;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        this.thread = new Thread(this::run, "Price Share upload");
        this.thread.setDaemon(true);
        this.thread.setPriority(Thread.MIN_PRIORITY);
        this.thread.start();
    }

    /** Queues a page. Never blocks; past 30 pages the oldest is dropped. */
    public void offer(Page page) {
        if (!config.enabled()) return;
        synchronized (queue) {
            queue.addLast(page);
            prune(System.currentTimeMillis());
            queue.notifyAll();
        }
    }

    /** Throws away anything not yet sent (sharing was switched off). */
    public void clear() {
        synchronized (queue) {
            queue.clear();
        }
    }

    public String status() { return lastStatus; }
    public int sentPages() { return sentPages; }

    int queued() {
        synchronized (queue) {
            return queue.size();
        }
    }

    /** Keeps the 30 newest pages that are under 10 minutes old. Call holding the queue lock. */
    private void prune(long now) {
        queue.removeIf(p -> now - p.at() > MAX_AGE_MS);
        while (queue.size() > QUEUE_MAX) queue.pollFirst();
    }

    private void run() {
        while (true) {
            try {
                awaitPages();
                if (!config.enabled()) {
                    clear();
                    continue;
                }
                // Only a token issued to the account playing now: after an account switch in the
                // launcher the old one is ignored and the new account logs in. Pages stay queued
                // while it does.
                String uuid = session.uuid();
                if (config.tokenFor(uuid) == null) {
                    long wait = loginNotBefore - System.currentTimeMillis();
                    if (wait > 0) {
                        lastStatus = "waiting to verify your Minecraft account (" + queued() + " pages held)";
                        Thread.sleep(wait);
                        continue;
                    }
                    if (!login()) continue;
                }
                List<Page> batch = take();
                if (!batch.isEmpty()) send(batch, uuid);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                // Never let the thread die: the worst case is a lost batch.
                Log.warn("Upload failed: " + t.getClass().getSimpleName());
                notBefore = System.currentTimeMillis() + 60_000;
            }
        }
    }

    /** Waits until it is time to send and something is queued. */
    private void awaitPages() throws InterruptedException {
        long wait = notBefore - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        synchronized (queue) {
            while (queue.isEmpty()) queue.wait();
        }
    }

    /** Up to 10 pages, oldest first. */
    private List<Page> take() {
        List<Page> batch = new ArrayList<>();
        synchronized (queue) {
            prune(System.currentTimeMillis());
            while (!queue.isEmpty() && batch.size() < BATCH) batch.add(queue.pollFirst());
        }
        return batch;
    }

    /** Puts a batch back at the front, for after logging in again. */
    private void requeue(List<Page> batch) {
        synchronized (queue) {
            for (int i = batch.size() - 1; i >= 0; i--) queue.addFirst(batch.get(i));
            prune(System.currentTimeMillis());
        }
    }

    private void send(List<Page> batch, String uuid) throws InterruptedException {
        String body = body(batch);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String token = config.tokenFor(uuid);
            if (token == null) {
                requeue(batch);
                return;
            }
            HttpResponse<String> r;
            try {
                r = http.send(post("/api/share/donut", body, token), HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException e) {
                lastStatus = "site unreachable";
                if (attempt == ATTEMPTS) break;
                Thread.sleep(attempt * 5_000L);
                continue;
            }
            int code = r.statusCode();
            if (code == 200) {
                Object reply = parseOrNull(r.body());
                long ask = (long) Json.num(reply, "wait", 0);
                notBefore = System.currentTimeMillis() + Math.max(timing.minGapMs(), Math.min(ask, 3_600_000));
                sentPages += batch.size();
                lastStatus = "shared " + sentPages + " pages this session";
                return;
            }
            if (code == 401) {
                // The token was revoked or expired: forget it and keep the pages for after the
                // next login.
                config.setToken(null, null);
                requeue(batch);
                return;
            }
            if (code == 429) {
                long after = r.headers().firstValueAsLong("Retry-After").orElse(-1);
                if (after < 0) {
                    Object reply = parseOrNull(r.body());
                    Object data = reply instanceof java.util.Map<?, ?> m ? m.get("data") : null;
                    after = (long) Json.num(data, "retryAfter", 60);
                }
                notBefore = System.currentTimeMillis() + Math.max(timing.minGapMs(), Math.min(after, 3600) * 1000);
                lastStatus = "the site asked to slow down";
                return;
            }
            if (code >= 500 && attempt < ATTEMPTS) {
                Thread.sleep(attempt * 5_000L);
                continue;
            }
            lastStatus = "the site refused the upload (" + code + ")";
            break;
        }
        notBefore = System.currentTimeMillis() + 30_000;
    }

    /**
     * Proves the player owns their Minecraft account (see PROTOCOL.md) and stores the token. One
     * log line per attempt. A Mojang failure (often 429, too many joins) waits 90 s, then 3 min,
     * then 10 min; the site refusing waits 10 min.
     */
    private boolean login() throws InterruptedException {
        String name = session.name();
        String uuid = session.uuid();
        try {
            HttpResponse<String> c = http.send(post("/api/share/challenge",
                    "{\"name\":" + Json.quote(name) + ",\"uuid\":" + Json.quote(uuid) + "}", null), HttpResponse.BodyHandlers.ofString());
            String serverId = c.statusCode() == 200 ? Json.str(parseOrNull(c.body()), "serverId") : null;
            if (serverId == null || !serverId.matches("[0-9a-f]{1,64}")) {
                return siteRefused("challenge", c.statusCode());
            }
            try {
                // The game's own session call: the Mojang token goes to Mojang and nowhere else.
                session.join(serverId);
            } catch (Exception e) {
                int status = e instanceof JoinException j ? j.status : -1;
                long wait = timing.joinBackoff(++joinFailures);
                loginNotBefore = System.currentTimeMillis() + wait;
                Log.warn("Verify: Mojang refused the session join (" + (status > 0 ? "HTTP " + status : e.getClass().getSimpleName())
                        + "), trying again in " + seconds(wait));
                lastStatus = "Mojang is busy, trying again soon";
                return false;
            }
            HttpResponse<String> l = http.send(post("/api/share/login",
                    "{\"name\":" + Json.quote(name) + ",\"serverId\":" + Json.quote(serverId) + "}", null), HttpResponse.BodyHandlers.ofString());
            String token = l.statusCode() == 200 ? Json.str(parseOrNull(l.body()), "token") : null;
            if (token == null || token.isBlank() || token.length() > 512) {
                return siteRefused("login", l.statusCode());
            }
            config.setToken(uuid, token);
            joinFailures = 0;
            Log.info("Verified as " + name);
            return true;
        } catch (java.io.IOException e) {
            // The site is down or unreachable: the short backoff, like a Mojang hiccup.
            long wait = timing.joinBackoff(1);
            loginNotBefore = System.currentTimeMillis() + wait;
            Log.warn("Verify: couldn't reach the site (" + e.getClass().getSimpleName() + "), trying again in " + seconds(wait));
            lastStatus = "site unreachable";
            return false;
        }
    }

    private boolean siteRefused(String step, int status) {
        long wait = timing.siteRefusedMs();
        loginNotBefore = System.currentTimeMillis() + wait;
        Log.warn("Verify: the site refused the " + step + " (HTTP " + status + "), trying again in " + seconds(wait));
        lastStatus = "couldn't verify your Minecraft account";
        return false;
    }

    static String seconds(long ms) {
        return ms >= 60_000 && ms % 60_000 == 0 ? ms / 60_000 + " min" : Math.round(ms / 1000.0) + " s";
    }

    private String body(List<Page> batch) {
        StringBuilder b = new StringBuilder(batch.size() * 4096);
        b.append("{\"v\":1,\"mod\":");
        Json.string(b, modVersion);
        b.append(",\"mc\":");
        Json.string(b, mcVersion);
        b.append(",\"showOnSite\":").append(config.showOnSite());
        b.append(",\"pages\":[");
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) b.append(',');
            batch.get(i).write(b);
        }
        b.append("]}");
        return b.toString();
    }

    private HttpRequest post(String path, String json, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("User-Agent", "PriceShare/" + modVersion + " (Minecraft " + mcVersion + ")")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b.build();
    }

    private static Object parseOrNull(String s) {
        try {
            return Json.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
