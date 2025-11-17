package com.kamisama0109.playerinventoryviewer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.awt.Desktop;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * PlayerInventoryViewer (FULL VERSION)
 * - Webサーバー制御コマンド
 * - status / url / open / restart
 * - JSONレスポンス統一 / CORS
 * - 静的ファイル提供
 */
public class PlayerInventoryViewer extends JavaPlugin {

    private HttpServer server;
    private boolean serverRunning = false;
    private int port;

    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        port = getConfig().getInt("web.port", 8080);

        registerCommands();

        try {
            startWebServer();
            getLogger().info("Webサーバー起動: http://localhost:" + port);
        } catch (IOException e) {
            getLogger().severe("Webサーバー起動エラー: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        stopWebServer();
    }

    // ───────────────────────────────────────────────
    // Command Registration
    // ───────────────────────────────────────────────
    private void registerCommands() {
        getCommand("piv").setExecutor((sender, cmd, label, args) -> {

            if (args.length == 0) {
                sender.sendMessage("§a---- PlayerInventoryViewer ----");
                sender.sendMessage("§e/piv reload §7- 設定リロード");
                sender.sendMessage("§e/piv start §7- Webサーバー起動");
                sender.sendMessage("§e/piv stop §7- Webサーバー停止");
                sender.sendMessage("§e/piv restart §7- Webサーバー再起動");
                sender.sendMessage("§e/piv status §7- 状態表示");
                sender.sendMessage("§e/piv url §7- URL表示");
                sender.sendMessage("§e/piv inventory <player>");
                sender.sendMessage("§e/piv open <player> §7- ブラウザで開く");
                return true;
            }

            String sub = args[0].toLowerCase();

            switch (sub) {

                case "reload":
                    reloadConfig();
                    port = getConfig().getInt("web.port", 8080);
                    sender.sendMessage("§a設定をリロードしました。");
                    break;

                case "start":
                    try {
                        startWebServer();
                        sender.sendMessage("§aWebサーバーを起動しました: http://localhost:" + port);
                    } catch (Exception e) {
                        sender.sendMessage("§c起動エラー: " + e.getMessage());
                    }
                    break;

                case "stop":
                    stopWebServer();
                    sender.sendMessage("§cWebサーバーを停止しました。");
                    break;

                case "restart":
                    stopWebServer();
                    try {
                        startWebServer();
                        sender.sendMessage("§aWebサーバーを再起動しました。");
                    } catch (Exception e) {
                        sender.sendMessage("§c再起動エラー: " + e.getMessage());
                    }
                    break;

                case "status":
                    if (serverRunning) {
                        sender.sendMessage("§aWebサーバー稼働中: http://localhost:" + port);
                    } else {
                        sender.sendMessage("§cWebサーバーは停止中");
                    }
                    break;

                case "url":
                    sender.sendMessage("§aURL: §bhttp://localhost:" + port);
                    break;

                case "inventory":
                    if (args.length < 2) {
                        sender.sendMessage("§c/piv inventory <player>");
                        return true;
                    }
                    String pName = args[1];
                    Player p = Bukkit.getPlayer(pName);

                    if (p == null) {
                        sender.sendMessage("§cプレイヤーが見つかりません");
                        return true;
                    }

                    sender.sendMessage("§aJSON:");
                    sender.sendMessage(gson.toJson(getInventoryData(p)));
                    break;

                case "open":
                    if (args.length < 2) {
                        sender.sendMessage("§c/piv open <player>");
                        return true;
                    }

                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().browse(new URI("http://localhost:" + port + "/?player=" + args[1]));
                            sender.sendMessage("§aブラウザを開きました");
                        } catch (Exception e) {
                            sender.sendMessage("§cブラウザ起動エラー: " + e.getMessage());
                        }
                    } else {
                        sender.sendMessage("§cこの環境ではブラウザを開けません");
                    }
                    break;

                default:
                    sender.sendMessage("§c不明なコマンドです");
            }

            return true;
        });
    }

    // ───────────────────────────────────────────────
    // Web Server Control
    // ───────────────────────────────────────────────

    private void startWebServer() throws IOException {
        if (serverRunning) return;

        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new StaticHandler());
        server.createContext("/api/players", new PlayersHandler());
        server.createContext("/api/inventory", new InventoryHandler());

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        serverRunning = true;
    }

    private void stopWebServer() {
        if (!serverRunning) return;

        server.stop(0);
        serverRunning = false;
    }

    // ───────────────────────────────────────────────
    // JSON / Error Utility
    // ───────────────────────────────────────────────

    private void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] bytes = gson.toJson(obj).getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange ex, int status, String msg) throws IOException {
        Map<String, Object> json = new HashMap<>();
        json.put("status", status);
        json.put("error", msg);
        sendJson(ex, status, json);
    }

    // *******************************************************************************************
    // Static File Handler
    // *******************************************************************************************

    class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            InputStream in = getResource("web" + path);

            if (in == null) {
                sendError(ex, 404, "File not found");
                return;
            }

            byte[] bytes = in.readAllBytes();

            ex.getResponseHeaders().set("Content-Type", guessMime(path));
            ex.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String guessMime(String p) {
            if (p.endsWith(".html")) return "text/html";
            if (p.endsWith(".css")) return "text/css";
            if (p.endsWith(".js")) return "application/javascript";
            if (p.endsWith(".png")) return "image/png";
            return "text/plain";
        }
    }

    // *******************************************************************************************
    // /api/players
    // *******************************************************************************************

    class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {

            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            if (!ex.getRequestMethod().equals("GET")) {
                sendError(ex, 405, "Only GET allowed");
                return;
            }

            List<Map<String, Object>> list = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", p.getName());
                m.put("uuid", p.getUniqueId().toString());
                m.put("online", true);
                m.put("world", p.getWorld().getName());
                m.put("x", p.getLocation().getX());
                m.put("y", p.getLocation().getY());
                m.put("z", p.getLocation().getZ());
                list.add(m);
            }

            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (!op.hasPlayedBefore() || op.isOnline()) continue;

                Map<String, Object> m = new HashMap<>();
                m.put("name", op.getName());
                m.put("uuid", op.getUniqueId().toString());
                m.put("online", false);
                m.put("lastSeen", op.getLastPlayed());
                list.add(m);
            }

            sendJson(ex, 200, list);
        }
    }

    // =====================================================================
    // /api/inventory
    // =====================================================================

    class InventoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {

            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            if (!ex.getRequestMethod().equals("GET")) {
                sendError(ex, 405, "Only GET allowed");
                return;
            }

            String query = ex.getRequestURI().getQuery();
            if (query == null || !query.contains("player=")) {
                sendError(ex, 400, "player parameter missing");
                return;
            }

            String name = URLDecoder.decode(
                    query.split("player=")[1],
                    StandardCharsets.UTF_8
            );

            Player p = Bukkit.getPlayer(name);

            if (p != null && p.isOnline()) {
                sendJson(ex, 200, getInventoryData(p));
                return;
            }

            OfflinePlayer op = Bukkit.getOfflinePlayer(name);

            if (!op.hasPlayedBefore()) {
                sendError(ex, 404, "player not found");
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("online", false);
            data.put("playerName", name);
            data.put("message", "オフラインプレイヤーのデータ取得は未対応");

            sendJson(ex, 200, data);
        }
    }

    // =====================================================================
    // Utility: Inventory JSON
    // =====================================================================

    private Map<String, Object> getInventoryData(Player p) {

        Map<String, Object> data = new HashMap<>();

        data.put("playerName", p.getName());
        data.put("online", true);
        data.put("health", p.getHealth());
        data.put("maxHealth", p.getMaxHealth());
        data.put("foodLevel", p.getFoodLevel());
        data.put("level", p.getLevel());
        data.put("exp", p.getExp());

        // Inventory
        List<Map<String, Object>> inv = new ArrayList<>();
        ItemStack[] cont = p.getInventory().getContents();

        for (int i = 0; i < cont.length; i++) {

            Map<String, Object> slot = new HashMap<>();
            slot.put("slot", i);

            ItemStack item = cont[i];

            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                slot.put("material", item.getType().toString());
                slot.put("amount", item.getAmount());
                slot.put("displayName",
                        (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
                                ? item.getItemMeta().getDisplayName()
                                : item.getType().toString()
                );
            } else {
                slot.put("material", "AIR");
                slot.put("amount", 0);
            }

            inv.add(slot);
        }

        data.put("inventory", inv);

        // Armor
        Map<String, String> armor = new HashMap<>();
        ItemStack[] armorItems = p.getInventory().getArmorContents();
        String[] armorNames = {"boots", "leggings", "chestplate", "helmet"};

        for (int i = 0; i < armorItems.length; i++) {
            ItemStack item = armorItems[i];
            armor.put(armorNames[i],
                    (item != null && item.getType() != org.bukkit.Material.AIR)
                            ? item.getType().toString()
                            : "AIR"
            );
        }

        data.put("armor", armor);

        return data;
    }
}
