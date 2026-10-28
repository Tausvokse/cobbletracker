package com.cobbletracker.mod.announce;

import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.common.util.DiscordMessage;
import com.cobbletracker.mod.CobbleTracker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DiscordWebhook {

    private DiscordWebhook() {
    }

    private static final int QUEUE_CAPACITY = 64;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int DROP_LOG_INTERVAL = 25;

    private static final BlockingQueue<Message> QUEUE = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicInteger DROPPED = new AtomicInteger();

    private static volatile HttpClient client;
    private static volatile Thread worker;
    private static volatile boolean running;

    private record Message(String url, String json) {
    }

    public static synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        QUEUE.clear();
        DROPPED.set(0);
        client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        worker = new Thread(DiscordWebhook::drain, "CobbleTracker-Discord");
        worker.setDaemon(true);  
        worker.start();
    }

    public static synchronized void stop() {
        running = false;
        QUEUE.clear();
        Thread t = worker;
        if (t != null) {
            t.interrupt();
        }
        worker = null;
        client = null;
    }

    public static void post(String url, String template, SpawnRecord record, int x, int y, int z) {
        post(url, template, record, x, y, z, null, 0xFFFFAA00);
    }

    public static void post(String url, String template, SpawnRecord record, int x, int y, int z,
            com.cobbletracker.mod.config.DiscordIntegrationSettings settings, int categoryColor) {
        if (url == null || url.isBlank() || !running) {
            return;
        }
        if (settings != null && !settings.enabled()) {
            return;
        }
        if (!url.startsWith("https://")) {
            CobbleTracker.LOGGER.warn("Ignoring a discord-webhook for '{}' that isn't an https:// url",
                    record.category());
            return;
        }
        String json;
        if (settings != null && settings.richEmbeds()) {
            json = buildRichEmbedJson(record, x, y, z, categoryColor);
        } else {
            String speciesName = record.species();
            if (record.traits() != null && !record.traits().isBlank()) {
                List<String> list = new ArrayList<>();
                for (String t : record.traits().split(",")) {
                    String clean = t.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!clean.isEmpty()) list.add(clean);
                }
                List<String> parts = new ArrayList<>();
                if (list.contains("boss")) parts.add("Boss");
                if (record.shiny() || list.contains("shiny")) parts.add("✨ Shiny");
                if (list.contains("legendary")) parts.add("Legendary");
                if (list.contains("mythic") || list.contains("mythical")) parts.add("Mythic");
                if (list.contains("ultra-beast") || list.contains("ultrabeast")) parts.add("Ultra-Beast");
                if (list.contains("ancient")) parts.add("Ancient");
                if (list.contains("starter")) parts.add("Starter");
                if (!parts.isEmpty()) {
                    speciesName = String.join(" ", parts) + " " + speciesName;
                }
            } else if (record.shiny()) {
                speciesName = "✨ Shiny " + speciesName;
            }
            json = DiscordMessage.body(DiscordMessage.render(template, speciesName, record.category(),
                    pretty(record.biome()), pretty(record.world()), x, y, z));
        }
        if (json == null) {
            return;
        }
        enqueue(new Message(url, json));
    }

    public static void postOutcome(String url, SpawnRecord record, String outcome, String by,
            com.cobbletracker.mod.config.DiscordIntegrationSettings settings, int color) {
        if (url == null || url.isBlank() || !running || settings == null || !settings.enabled()) {
            return;
        }
        if (!url.startsWith("https://")) {
            return;
        }
        boolean isCatch = "caught".equalsIgnoreCase(outcome);
        boolean isDefeat = "defeated".equalsIgnoreCase(outcome);
        boolean isDespawn = "despawned".equalsIgnoreCase(outcome);
        if (isCatch && !settings.postCaptures()) return;
        if (isDefeat && !settings.postDefeats()) return;
        if (isDespawn && !settings.postDespawns()) return;

        String actionPhrase = isCatch ? "was caught by " + (by == null || by.isBlank() ? "someone" : by) + "!"
                : isDefeat ? "was defeated by " + (by == null || by.isBlank() ? "someone" : by) + "!"
                : "despawned.";

        String shinyPrefix = record.shiny() ? "✨ Shiny " : "";
        String json;
        if (settings.richEmbeds()) {
            int embedColor = isCatch ? 0x55FF55 : isDefeat ? 0xFF5555 : 0xAAAAAA;
            json = "{\"embeds\":[{"
                    + "\"title\":\"" + DiscordMessage.escape("🏆 " + shinyPrefix + record.species() + " " + (isCatch ? "Caught" : isDefeat ? "Defeated" : "Despawned")) + "\","
                    + "\"description\":\"**" + DiscordMessage.escape(shinyPrefix + record.species()) + "** " + DiscordMessage.escape(actionPhrase) + "\","
                    + "\"color\":" + embedColor + ","
                    + "\"footer\":{\"text\":\"CobbleTracker\"}"
                    + "}]}";
        } else {
            json = DiscordMessage.body("🏆 **" + shinyPrefix + record.species() + "** " + actionPhrase);
        }
        enqueue(new Message(url, json));
    }

    private static String buildRichEmbedJson(SpawnRecord record, int x, int y, int z, int color) {
        StringBuilder sb = new StringBuilder();
        String shinyPrefix = record.shiny() ? "✨ Shiny " : "";
        String title = shinyPrefix + record.species() + " Spawned!";
        String description = "A wild **" + shinyPrefix + record.species() + "** was detected in the world!";
        int embedColor = color & 0xFFFFFF;

        sb.append("{\"embeds\":[{");
        sb.append("\"title\":\"").append(DiscordMessage.escape(title)).append("\",");
        sb.append("\"description\":\"").append(DiscordMessage.escape(description)).append("\",");
        sb.append("\"color\":").append(embedColor).append(",");
        sb.append("\"fields\":[");
        sb.append("{\"name\":\"Category\",\"value\":\"").append(DiscordMessage.escape(pretty(record.category()))).append("\",\"inline\":true},");
        sb.append("{\"name\":\"Biome\",\"value\":\"").append(DiscordMessage.escape(pretty(record.biome()))).append("\",\"inline\":true},");
        sb.append("{\"name\":\"Coordinates\",\"value\":\"").append(x).append(", ").append(y).append(", ").append(z).append("\",\"inline\":true}");
        if (record.details() != null && !record.details().isEmpty()) {
            if (record.details().level() > 0) {
                sb.append(",{\"name\":\"Level\",\"value\":\"").append(record.details().level()).append("\",\"inline\":true}");
            }
            if (!record.details().nature().isEmpty()) {
                sb.append(",{\"name\":\"Nature\",\"value\":\"").append(DiscordMessage.escape(pretty(record.details().nature()))).append("\",\"inline\":true}");
            }
            if (!record.details().ability().isEmpty()) {
                sb.append(",{\"name\":\"Ability\",\"value\":\"").append(DiscordMessage.escape(pretty(record.details().ability()))).append("\",\"inline\":true}");
            }
        }
        sb.append("],\"footer\":{\"text\":\"CobbleTracker\"}");
        sb.append("}]}");
        return sb.toString();
    }

    private static void enqueue(Message message) {
        if (!QUEUE.offer(message)) {
            QUEUE.poll();
            if (!QUEUE.offer(message)) {
                countDrop();
                return;
            }
            countDrop();
        }
    }

    private static void countDrop() {
        int n = DROPPED.incrementAndGet();
        if (n % DROP_LOG_INTERVAL == 1) {
            CobbleTracker.LOGGER.warn("Discord webhook is falling behind - {} announcement(s) dropped so far",
                    n);
        }
    }

    private static void drain() {
        Thread self = Thread.currentThread();
        while (running && worker == self) {
            Message message;
            try {
                message = QUEUE.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                self.interrupt();
                return;
            }
            if (message != null) {
                send(message);
            }
        }
    }

    private static void send(Message message) {
        HttpClient http = client;
        if (http == null) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(message.url()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "CobbleTracker")
                    .POST(HttpRequest.BodyPublishers.ofString(message.json()))
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                CobbleTracker.LOGGER.warn("Discord webhook returned HTTP {}", status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            CobbleTracker.LOGGER.warn("Discord webhook post failed: {}", e.getClass().getSimpleName());
        }
    }

    private static String pretty(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        s = s.replace('_', ' ');
        return s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
