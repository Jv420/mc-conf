package nl.dynathismp.analytics;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DynathiGA4Analytics extends JavaPlugin implements Listener {

    private HttpClient httpClient;
    private BukkitTask onlineTask;

    private boolean analyticsEnabled;
    private boolean debug;
    private boolean hashUuid;
    private boolean sendPlayerName;
    private boolean sendWorldName;
    private String measurementId;
    private String apiSecret;
    private String endpoint;
    private String clientId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("tracking.server-start", true)) {
            sendEvent("server_start", baseParams());
        }

        startOnlineTask();
        getLogger().info("DynathiGA4Analytics enabled. Tracking is " + (analyticsEnabled ? "ON" : "OFF") + ".");
    }

    @Override
    public void onDisable() {
        if (onlineTask != null) {
            onlineTask.cancel();
            onlineTask = null;
        }

        if (getConfig().getBoolean("tracking.server-stop", true)) {
            sendEvent("server_stop", baseParams()).join();
        }
    }

    private void loadSettings() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        analyticsEnabled = cfg.getBoolean("enabled", false);
        debug = cfg.getBoolean("debug", false);
        hashUuid = cfg.getBoolean("privacy.hash-player-uuid", true);
        sendPlayerName = cfg.getBoolean("privacy.send-player-name", false);
        sendWorldName = cfg.getBoolean("privacy.send-world-name", true);
        measurementId = cfg.getString("measurement-id", "G-XXXXXXXXXX").trim();
        apiSecret = cfg.getString("api-secret", "PASTE_YOUR_API_SECRET_HERE").trim();
        clientId = cfg.getString("client-id", "dynathismp-server").trim();

        boolean eu = cfg.getBoolean("use-eu-endpoint", true);
        endpoint = eu ? "https://region1.google-analytics.com/mp/collect" : "https://www.google-analytics.com/mp/collect";

        if (measurementId.equalsIgnoreCase("G-XXXXXXXXXX") || apiSecret.equalsIgnoreCase("PASTE_YOUR_API_SECRET_HERE")) {
            analyticsEnabled = false;
            getLogger().warning("GA4 measurement-id/api-secret zijn nog niet ingesteld. Tracking blijft uit.");
        }
    }

    private void startOnlineTask() {
        if (onlineTask != null) {
            onlineTask.cancel();
        }

        if (!getConfig().getBoolean("tracking.online-count-task", true)) {
            return;
        }

        long minutes = Math.max(1, getConfig().getLong("tracking.online-count-minutes", 5));
        long ticks = minutes * 60L * 20L;

        onlineTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            Map<String, Object> params = baseParams();
            params.put("online_players", Bukkit.getOnlinePlayers().size());
            params.put("max_players", Bukkit.getMaxPlayers());
            sendEvent("online_count", params);
        }, ticks, ticks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("tracking.player-join", true)) return;
        Map<String, Object> params = playerParams(event.getPlayer());
        sendEvent("player_join", params);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!getConfig().getBoolean("tracking.player-quit", true)) return;
        Map<String, Object> params = playerParams(event.getPlayer());
        sendEvent("player_quit", params);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!getConfig().getBoolean("tracking.player-death", true)) return;
        Player player = event.getEntity();
        Map<String, Object> params = playerParams(player);
        params.put("death_cause", player.getLastDamageCause() == null ? "unknown" : player.getLastDamageCause().getCause().name().toLowerCase());
        sendEvent("player_death", params);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!getConfig().getBoolean("tracking.player-command", false)) return;
        String raw = event.getMessage();
        String command = raw.split(" ")[0].replace("/", "").toLowerCase();
        Map<String, Object> params = playerParams(event.getPlayer());
        params.put("command", command);
        sendEvent("player_command", params);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!getConfig().getBoolean("tracking.player-chat", false)) return;
        Map<String, Object> params = playerParams(event.getPlayer());
        params.put("message_length", event.getMessage() == null ? 0 : event.getMessage().length());
        sendEvent("player_chat", params);
    }

    private Map<String, Object> baseParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("server_name", "DynathiSMP");
        params.put("server_version", Bukkit.getMinecraftVersion());
        params.put("online_players", Bukkit.getOnlinePlayers().size());
        params.put("max_players", Bukkit.getMaxPlayers());
        params.put("engagement_time_msec", 100);
        params.put("session_id", System.currentTimeMillis() / 1000);
        return params;
    }

    private Map<String, Object> playerParams(Player player) {
        Map<String, Object> params = baseParams();
        params.put("player_id", hashUuid ? sha256(player.getUniqueId()) : player.getUniqueId().toString());
        params.put("game_mode", player.getGameMode().name().toLowerCase());
        params.put("bedrock_or_java", isLikelyBedrock(player) ? "bedrock" : "java");

        if (sendPlayerName) {
            params.put("player_name", player.getName());
        }
        if (sendWorldName) {
            World world = player.getWorld();
            params.put("world", world == null ? "unknown" : world.getName());
        }
        return params;
    }

    private boolean isLikelyBedrock(Player player) {
        String name = player.getName();
        return name.startsWith(".") || name.startsWith("*") || name.contains(" ");
    }

    private CompletableFuture<Void> sendEvent(String eventName, Map<String, Object> params) {
        if (!analyticsEnabled) {
            if (debug) getLogger().info("Debug: tracking disabled, skipped event " + eventName);
            return CompletableFuture.completedFuture(null);
        }

        String url = endpoint + "?measurement_id=" + urlEscape(measurementId) + "&api_secret=" + urlEscape(apiSecret);
        String body = buildPayload(eventName, params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (debug) {
                        getLogger().info("GA4 event " + eventName + " -> HTTP " + response.statusCode());
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        getLogger().warning("GA4 event mislukt: " + eventName + " HTTP " + response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    getLogger().warning("GA4 event kon niet verzonden worden: " + eventName + " - " + error.getMessage());
                    return null;
                });
    }

    private String buildPayload(String eventName, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"client_id\":\"").append(jsonEscape(clientId)).append("\",");
        sb.append("\"non_personalized_ads\":true,");
        sb.append("\"events\":[{");
        sb.append("\"name\":\"").append(jsonEscape(eventName)).append("\",");
        sb.append("\"params\":").append(mapToJson(params));
        sb.append("}]");
        sb.append('}');
        return sb.toString();
    }

    private String mapToJson(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("\"").append(jsonEscape(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(jsonEscape(String.valueOf(value))).append("\"");
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private String sha256(UUID uuid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(uuid.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 32);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String jsonEscape(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String urlEscape(String input) {
        return input.replace(" ", "%20");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dynathiga")) return false;

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            startOnlineTask();
            sender.sendMessage("§aDynathiGA4Analytics config opnieuw geladen. Tracking: " + (analyticsEnabled ? "§aON" : "§cOFF"));
            return true;
        }

        sender.sendMessage("§6DynathiGA4Analytics §7- gebruik: §e/dynathiga reload");
        return true;
    }
}
