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
    private boolean analyticsEnabled, debug, hashUuid, sendPlayerName, sendWorldName;
    private String measurementId, apiSecret, endpoint, clientId;
    private String firebaseProjectName, firebaseAnalyticsLabel, serverId, serverName, serverHost, serverPlatform;
    private String webshopName, webshopUrl, webshopCurrency;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getConfig().getBoolean("tracking.server-start", true)) sendEvent("mc_server_start", baseParams());
        startOnlineTask();
        getLogger().info("DynathiGA4Analytics enabled for " + serverHost + ". Tracking is " + (analyticsEnabled ? "ON" : "OFF") + ".");
    }

    @Override
    public void onDisable() {
        if (onlineTask != null) onlineTask.cancel();
        if (getConfig().getBoolean("tracking.server-stop", true)) sendEvent("mc_server_stop", baseParams()).join();
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
        clientId = cfg.getString("client-id", "dynathismp-mcsh-server").trim();
        firebaseProjectName = cfg.getString("firebase.project-name", "DynathiSMP").trim();
        firebaseAnalyticsLabel = cfg.getString("firebase.analytics-label", "firebase_ga4").trim();
        serverId = cfg.getString("server.id", "dynathismp").trim();
        serverName = cfg.getString("server.name", "DynathiSMP").trim();
        serverHost = cfg.getString("server.host", "DynathiSMP.mcsh.io").trim();
        serverPlatform = cfg.getString("server.platform", "mcsh").trim();
        webshopName = cfg.getString("webshop.name", "DynathiSMP Webshop").trim();
        webshopUrl = cfg.getString("webshop.url", "").trim();
        webshopCurrency = cfg.getString("webshop.currency", "EUR").trim();
        endpoint = cfg.getBoolean("use-eu-endpoint", true) ? "https://region1.google-analytics.com/mp/collect" : "https://www.google-analytics.com/mp/collect";
        if (measurementId.equalsIgnoreCase("G-XXXXXXXXXX") || apiSecret.equalsIgnoreCase("PASTE_YOUR_API_SECRET_HERE")) {
            analyticsEnabled = false;
            getLogger().warning("GA4 measurement-id/api-secret zijn nog niet ingesteld. Tracking blijft uit.");
        }
    }

    private void startOnlineTask() {
        if (onlineTask != null) onlineTask.cancel();
        if (!getConfig().getBoolean("tracking.online-count-task", true)) return;
        long ticks = Math.max(1, getConfig().getLong("tracking.online-count-minutes", 5)) * 60L * 20L;
        onlineTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> sendEvent("mc_online_count", baseParams()), ticks, ticks);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { if (getConfig().getBoolean("tracking.player-join", true)) sendEvent("mc_player_join", playerParams(e.getPlayer())); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { if (getConfig().getBoolean("tracking.player-quit", true)) sendEvent("mc_player_quit", playerParams(e.getPlayer())); }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!getConfig().getBoolean("tracking.player-death", true)) return;
        Map<String, Object> p = playerParams(e.getEntity());
        p.put("death_cause", e.getEntity().getLastDamageCause() == null ? "unknown" : e.getEntity().getLastDamageCause().getCause().name().toLowerCase());
        sendEvent("mc_player_death", p);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String cmd = e.getMessage().split(" ")[0].replace("/", "").toLowerCase();
        String shopCmd = getConfig().getString("tracking.webshop-command", "webshop").replace("/", "").toLowerCase();
        if (getConfig().getBoolean("tracking.webshop-open", true) && cmd.equals(shopCmd)) {
            Map<String, Object> p = playerParams(e.getPlayer());
            p.put("webshop_name", webshopName);
            p.put("webshop_url", webshopUrl);
            p.put("command", cmd);
            sendEvent("mc_webshop_open", p);
        }
        if (getConfig().getBoolean("tracking.player-command", false)) {
            Map<String, Object> p = playerParams(e.getPlayer());
            p.put("command", cmd);
            sendEvent("mc_player_command", p);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!getConfig().getBoolean("tracking.player-chat", false)) return;
        Map<String, Object> p = playerParams(e.getPlayer());
        p.put("message_length", e.getMessage() == null ? 0 : e.getMessage().length());
        sendEvent("mc_player_chat", p);
    }

    private Map<String, Object> baseParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("firebase_project", firebaseProjectName);
        p.put("analytics_source", firebaseAnalyticsLabel);
        p.put("server_id", serverId);
        p.put("server_name", serverName);
        p.put("server_host", serverHost);
        p.put("server_platform", serverPlatform);
        p.put("minecraft_version", Bukkit.getMinecraftVersion());
        p.put("online_players", Bukkit.getOnlinePlayers().size());
        p.put("max_players", Bukkit.getMaxPlayers());
        p.put("engagement_time_msec", 100);
        p.put("session_id", System.currentTimeMillis() / 1000);
        return p;
    }

    private Map<String, Object> playerParams(Player player) {
        Map<String, Object> p = baseParams();
        p.put("player_id", hashUuid ? sha256(player.getUniqueId()) : player.getUniqueId().toString());
        p.put("game_mode", player.getGameMode().name().toLowerCase());
        p.put("client_type", isLikelyBedrock(player) ? "bedrock" : "java");
        if (sendPlayerName) p.put("player_name", player.getName());
        if (sendWorldName) { World w = player.getWorld(); p.put("world", w == null ? "unknown" : w.getName()); }
        return p;
    }

    private Map<String, Object> saleParams(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) return playerParams(online);
        Map<String, Object> p = baseParams();
        p.put("player_id", sha256Text(playerName.toLowerCase()).substring(0, 32));
        if (sendPlayerName) p.put("player_name", playerName);
        p.put("client_type", "unknown");
        p.put("game_mode", "unknown");
        return p;
    }

    private void sendSale(String playerName, String productId, double value, String currency, String orderId) {
        if (!getConfig().getBoolean("tracking.webshop-sales", true)) return;
        Map<String, Object> p = saleParams(playerName);
        p.put("webshop_name", webshopName);
        p.put("webshop_url", webshopUrl);
        p.put("product_id", productId);
        p.put("item_id", productId);
        p.put("item_name", productId);
        p.put("value", value);
        p.put("currency", currency == null || currency.isBlank() ? webshopCurrency : currency.toUpperCase());
        p.put("order_id", orderId == null || orderId.isBlank() ? "manual" : orderId);
        sendEvent("mc_webshop_sale", p);
    }

    private boolean isLikelyBedrock(Player p) { String n = p.getName(); return n.startsWith(".") || n.startsWith("*") || n.contains(" "); }

    private CompletableFuture<Void> sendEvent(String name, Map<String, Object> params) {
        if (!analyticsEnabled) { if (debug) getLogger().info("Skipped event " + name); return CompletableFuture.completedFuture(null); }
        String url = endpoint + "?measurement_id=" + urlEscape(measurementId) + "&api_secret=" + urlEscape(apiSecret);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(buildPayload(name, params), StandardCharsets.UTF_8)).build();
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenAccept(r -> {
            if (debug) getLogger().info("GA4/Firebase event " + name + " -> HTTP " + r.statusCode());
            if (r.statusCode() < 200 || r.statusCode() >= 300) getLogger().warning("GA4/Firebase event mislukt: " + name + " HTTP " + r.statusCode());
        }).exceptionally(err -> { getLogger().warning("GA4/Firebase event fout: " + name + " - " + err.getMessage()); return null; });
    }

    private String buildPayload(String name, Map<String, Object> params) {
        return "{\"client_id\":\"" + jsonEscape(clientId) + "\",\"non_personalized_ads\":true,\"events\":[{\"name\":\"" + jsonEscape(name) + "\",\"params\":" + mapToJson(params) + "}]}";
    }

    private String mapToJson(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder("{"); boolean first = true;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!first) sb.append(','); first = false;
            sb.append("\"").append(jsonEscape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) sb.append(v); else sb.append("\"").append(jsonEscape(String.valueOf(v))).append("\"");
        }
        return sb.append('}').toString();
    }

    private String sha256(UUID uuid) { return sha256Text(uuid.toString()).substring(0, 32); }

    private String sha256Text(String text) {
        try { MessageDigest d = MessageDigest.getInstance("SHA-256"); byte[] h = d.digest(text.getBytes(StandardCharsets.UTF_8)); StringBuilder x = new StringBuilder(); for (byte b : h) x.append(String.format("%02x", b)); return x.toString(); }
        catch (Exception e) { return "unknown00000000000000000000000000"; }
    }

    private String jsonEscape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
    private String urlEscape(String s) { return s.replace(" ", "%20"); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("dynathiga")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { loadSettings(); startOnlineTask(); sender.sendMessage("§aDynathiGA4Analytics herladen."); return true; }
            sender.sendMessage("§6Gebruik: §e/dynathiga reload"); return true;
        }
        if (command.getName().equalsIgnoreCase("dynathisale")) {
            if (args.length < 4) { sender.sendMessage("§cGebruik: /dynathisale <player> <product_id> <price> <currency> [order_id]"); return true; }
            try { sendSale(args[0], args[1], Double.parseDouble(args[2].replace(',', '.')), args[3], args.length >= 5 ? args[4] : "manual"); sender.sendMessage("§aWebshop sale analytics verzonden."); }
            catch (NumberFormatException ex) { sender.sendMessage("§cPrice moet een nummer zijn, bijvoorbeeld 4.99"); }
            return true;
        }
        return false;
    }
}
