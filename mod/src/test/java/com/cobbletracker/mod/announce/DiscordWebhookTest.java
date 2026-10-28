package com.cobbletracker.mod.announce;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.mod.config.DiscordIntegrationSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DiscordWebhookTest {
    private static final String URL = "https://discord.invalid/api/webhooks/test/secret-token";
    private final List<Thread> workers = new ArrayList<>();

    @AfterEach
    void stopWorkers() throws Exception {
        DiscordWebhook.stop();
        for (Thread worker : workers) {
            worker.join(3000);
            assertFalse(worker.isAlive(), "Discord worker survived shutdown");
        }
    }

    @Test
    void disabledAndMissingWebhooksNeverReachHttpClient() throws Exception {
        FakeClient client = startClient();
        DiscordWebhook.post(null, "missing", record("Mew"), 0, 0, 0);
        DiscordWebhook.post(" ", "blank", record("Mew"), 0, 0, 0);
        DiscordWebhook.post("http://discord.invalid/secret-token", "insecure", record("Mew"), 0, 0, 0);
        DiscordWebhook.post(URL, "disabled", record("Mew"), 0, 0, 0,
                settings(false, false, true, true, true), 0);
        DiscordWebhook.postOutcome(URL, record("Mew"), "caught", "Ash",
                settings(false, false, true, true, true), 0);
        post("barrier");
        assertEquals("barrier", content(client.next()));
        assertEquals(1, client.requests.size());
    }

    @Test
    void sendsValidEscapedJsonUsingCallerFilteredCoordinatesAndBoundedTimeout() throws Exception {
        FakeClient client = startClient();
        DiscordWebhook.post(URL, "%species%\n%biome% %world% %x%,%y%,%z%",
                record("Mew\"\\\n\t"), 128, 64, -256);
        HttpRequest request = client.next();
        String message = content(request);
        assertEquals("Mew\"\\\n\t\nJungle Overworld 128,64,-256", message);
        assertFalse(message.contains("987654"));
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(Duration.ofSeconds(10), request.timeout().orElseThrow());
    }

    @Test
    void richEmbedsRetainEscapingColorAndOutcomePreferences() throws Exception {
        FakeClient client = startClient();
        DiscordIntegrationSettings settings = settings(true, true, false, true, false);
        DiscordWebhook.post(URL, "ignored", record("Mew\""), 128, 64, -256, settings, 0xFF12AB34);
        JsonObject embed = json(client.next()).getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("Mew\" Spawned!", embed.get("title").getAsString());
        assertEquals(0x12AB34, embed.get("color").getAsInt());
        assertEquals("128, 64, -256", embed.getAsJsonArray("fields").get(2)
                .getAsJsonObject().get("value").getAsString());
        DiscordWebhook.postOutcome(URL, record("Mew"), "caught", "Ash", settings, 0);
        DiscordWebhook.postOutcome(URL, record("Mew"), "despawned", "", settings, 0);
        DiscordWebhook.postOutcome(URL, record("Mew"), "defeated", "Ash\"\n", settings, 0);
        JsonObject outcome = json(client.next()).getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertTrue(outcome.get("description").getAsString().contains("Ash\"\n"));
        post("barrier");
        assertEquals("barrier", content(client.next()));
        assertEquals(3, client.requests.size());
    }

    @Test
    void httpErrorsAndTimeoutDoNotStopSubsequentAnnouncements() throws Exception {
        FakeClient client = startClient();
        List<String> logs = new CopyOnWriteArrayList<>();
        Class<?> appenderType = Class.forName("org.apache.logging.log4j.core.Appender");
        Class<?> eventType = Class.forName("org.apache.logging.log4j.core.LogEvent");
        Object appender = java.lang.reflect.Proxy.newProxyInstance(appenderType.getClassLoader(),
                new Class<?>[] { appenderType }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> "DiscordWebhookTest";
                    case "isStarted", "ignoreExceptions" -> true;
                    case "isStopped" -> false;
                    case "append" -> {
                        Object message = eventType.getMethod("getMessage").invoke(arguments[0]);
                        logs.add((String) Class.forName("org.apache.logging.log4j.message.Message")
                                .getMethod("getFormattedMessage").invoke(message));
                        yield null;
                    }
                    default -> null;
                });
        Object logger = Class.forName("org.apache.logging.log4j.LogManager")
                .getMethod("getLogger", String.class).invoke(null, "CobbleTracker");
        logger.getClass().getMethod("addAppender", appenderType).invoke(logger, appender);
        try {
        client.actions.add(request -> response(request, 429));
        client.actions.add(request -> response(request, 500));
        client.actions.add(request -> { throw new HttpTimeoutException(URL); });
        client.actions.add(request -> { throw new IOException(URL); });
        for (int i = 0; i < 5; i++) post("message-" + i);
        for (int i = 0; i < 5; i++) assertEquals("message-" + i, content(client.next()));
        assertTrue(logs.stream().anyMatch(message -> message.contains("HTTP 429")));
        assertTrue(logs.stream().anyMatch(message -> message.contains("HTTP 500")));
        assertTrue(logs.stream().anyMatch(message -> message.contains("HttpTimeoutException")));
        assertTrue(logs.stream().noneMatch(message -> message.contains("secret-token")
                || message.contains("discord.invalid")));
        } finally {
            logger.getClass().getMethod("removeAppender", appenderType).invoke(logger, appender);
        }
    }

    @Test
    void outageKeepsOnlyNewest64QueuedAnnouncementsWithoutBlockingProducer() throws Exception {
        FakeClient client = startClient();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        client.actions.add(request -> {
            sending.countDown();
            assertTrue(release.await(15, TimeUnit.SECONDS));
            return response(request, 204);
        });
        try (var producer = Executors.newSingleThreadExecutor()) {
            post("in-flight");
            assertTrue(sending.await(3, TimeUnit.SECONDS));
            producer.submit(() -> {
                for (int i = 1; i <= 80; i++) post("queued-" + i);
            }).get(10, TimeUnit.SECONDS);
            assertEquals(1, client.requests.size(), "Producer must finish while HTTP send is blocked");
        } finally {
            release.countDown();
        }
        assertEquals("in-flight", content(client.next()));
        for (int i = 17; i <= 80; i++) assertEquals("queued-" + i, content(client.next()));
        assertEquals(65, client.requests.size());
    }

    @Test
    void stoppedWorkerCannotStealAnnouncementsAfterRestartEvenIfSendConsumesInterrupt() throws Exception {
        FakeClient oldClient = startClient();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        oldClient.actions.add(request -> {
            sending.countDown();
            boolean released = false;
            while (!released) {
                try { released = release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { }
            }
            return response(request, 204);
        });
        post("old-session");
        assertTrue(sending.await(3, TimeUnit.SECONDS));
        Thread oldWorker = workers.getFirst();
        DiscordWebhook.stop();
        FakeClient newClient = startClient();
        release.countDown();
        oldWorker.join(3000);
        assertFalse(oldWorker.isAlive());
        post("new-session");
        assertEquals("new-session", content(newClient.next()));
        assertEquals(1, oldClient.requests.size());
    }

    private FakeClient startClient() throws Exception {
        DiscordWebhook.start();
        Field field = DiscordWebhook.class.getDeclaredField("client");
        field.setAccessible(true);
        FakeClient client = new FakeClient();
        field.set(null, client);
        Field worker = DiscordWebhook.class.getDeclaredField("worker");
        worker.setAccessible(true);
        workers.add((Thread) worker.get(null));
        return client;
    }

    private static void post(String text) {
        DiscordWebhook.post(URL, text, record("Mew"), 128, 64, -256);
    }

    private static SpawnRecord record(String species) {
        return new SpawnRecord(UUID.randomUUID(), "legendary", species, "cobblemon:mew", 123L,
                "minecraft:overworld", 987654, 75, -987654, "minecraft:jungle", false,
                false, null, "", "", "", null);
    }

    private static DiscordIntegrationSettings settings(boolean enabled, boolean rich, boolean captures,
            boolean defeats, boolean despawns) {
        return new DiscordIntegrationSettings(enabled, rich, captures, defeats, despawns);
    }

    private static String content(HttpRequest request) throws Exception {
        return json(request).get("content").getAsString();
    }

    private static JsonObject json(HttpRequest request) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) {
                byte[] value = new byte[item.remaining()];
                item.get(value);
                bytes.writeBytes(value);
            }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return JsonParser.parseString(result.get(3, TimeUnit.SECONDS)).getAsJsonObject();
    }

    @FunctionalInterface
    private interface Action {
        HttpResponse<Void> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private static HttpResponse<Void> response(HttpRequest request, int status) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return request; }
            public Optional<HttpResponse<Void>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
            public Void body() { return null; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return request.uri(); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    private static final class FakeClient extends HttpClient {
        final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
        final BlockingQueue<HttpRequest> completed = new LinkedBlockingQueue<>();
        final BlockingQueue<Action> actions = new LinkedBlockingQueue<>();

        HttpRequest next() throws InterruptedException {
            HttpRequest request = completed.poll(3, TimeUnit.SECONDS);
            assertNotNull(request, "Discord worker did not dispatch the expected announcement");
            return request;
        }

        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            requests.add(request);
            try {
                Action action = actions.poll();
                return (HttpResponse<T>) (action == null ? response(request, 204) : action.send(request));
            } finally {
                completed.add(request);
            }
        }
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        public Optional<Duration> connectTimeout() { return Optional.empty(); }
        public Redirect followRedirects() { return Redirect.NEVER; }
        public Optional<ProxySelector> proxy() { return Optional.empty(); }
        public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        public SSLParameters sslParameters() { return new SSLParameters(); }
        public Optional<Authenticator> authenticator() { return Optional.empty(); }
        public Version version() { return Version.HTTP_1_1; }
        public Optional<Executor> executor() { return Optional.empty(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }
}
