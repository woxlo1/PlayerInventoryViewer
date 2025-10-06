package com.example.playerinventoryviewer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;

public class PlayerInventoryViewer extends JavaPlugin {

    private HttpServer server;
    private Gson gson = new Gson();

    @Override
    public void onEnable() {
        getLogger().info("PlayerInventoryViewer プラグインが有効になりました！");

        // コンフィグファイルを保存
        saveDefaultConfig();

        // Webサーバーを開始
        startWebServer();

        getLogger().info("Webサーバーが http://localhost:8080 で開始されました");
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop(0);
            getLogger().info("Webサーバーを停止しました");
        }
    }

    private void startWebServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(8080), 0);

            // 静的ファイル用ハンドラー
            server.createContext("/", new StaticFileHandler());

            // API エンドポイント
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/api/inventory", new InventoryHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

        } catch (IOException e) {
            getLogger().severe("Webサーバーの開始に失敗しました: " + e.getMessage());
        }
    }

    // 静的ファイルハンドラー
    class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();

            if (requestPath.equals("/")) {
                requestPath = "/index.html";
            }

            try {
                InputStream inputStream = getResource("web" + requestPath);
                if (inputStream != null) {
                    byte[] response = inputStream.readAllBytes();

                    // Content-Typeを設定
                    String contentType = getContentType(requestPath);
                    exchange.getResponseHeaders().set("Content-Type", contentType);

                    exchange.sendResponseHeaders(200, response.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response);
                    os.close();
                } else {
                    String notFound = "404 - ファイルが見つかりません";
                    exchange.sendResponseHeaders(404, notFound.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(notFound.getBytes());
                    os.close();
                }
            } catch (Exception e) {
                String error = "500 - サーバーエラー";
                exchange.sendResponseHeaders(500, error.length());
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".json")) return "application/json";
            return "text/plain";
        }
    }

    // プレイヤー一覧取得ハンドラー
    class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            JsonArray playersArray = new JsonArray();

            // オンラインプレイヤー
            for (Player player : Bukkit.getOnlinePlayers()) {
                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("name", player.getName());
                playerObj.addProperty("uuid", player.getUniqueId().toString());
                playerObj.addProperty("online", true);
                playerObj.addProperty("world", player.getWorld().getName());
                playerObj.addProperty("x", player.getLocation().getX());
                playerObj.addProperty("y", player.getLocation().getY());
                playerObj.addProperty("z", player.getLocation().getZ());
                playersArray.add(playerObj);
            }

            // オフラインプレイヤー（過去にログインしたことがある）
            for (org.bukkit.OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (!offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) continue;

                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("name", offlinePlayer.getName());
                playerObj.addProperty("uuid", offlinePlayer.getUniqueId().toString());
                playerObj.addProperty("online", false);
                playerObj.addProperty("lastSeen", offlinePlayer.getLastPlayed());
                playersArray.add(playerObj);
            }

            String response = gson.toJson(playersArray);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes("UTF-8"));
            os.close();
        }
    }

    // インベントリ取得ハンドラー
    class InventoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String playerName = null;

            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "player".equals(keyValue[0])) {
                        playerName = keyValue[1];
                        break;
                    }
                }
            }

            if (playerName == null) {
                String error = "プレイヤー名が指定されていません";
                exchange.sendResponseHeaders(400, error.getBytes("UTF-8").length);
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes("UTF-8"));
                os.close();
                return;
            }

            JsonObject inventoryData = new JsonObject();
            Player player = Bukkit.getPlayer(playerName);

            if (player != null && player.isOnline()) {
                // オンラインプレイヤーのインベントリ
                inventoryData.addProperty("playerName", player.getName());
                inventoryData.addProperty("online", true);
                inventoryData.addProperty("health", player.getHealth());
                inventoryData.addProperty("maxHealth", player.getMaxHealth());
                inventoryData.addProperty("foodLevel", player.getFoodLevel());
                inventoryData.addProperty("level", player.getLevel());
                inventoryData.addProperty("exp", player.getExp());

                // インベントリ
                JsonArray inventory = new JsonArray();
                ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    JsonObject slot = new JsonObject();
                    slot.addProperty("slot", i);
                    if (contents[i] != null && contents[i].getType() != org.bukkit.Material.AIR) {
                        slot.addProperty("material", contents[i].getType().toString());
                        slot.addProperty("amount", contents[i].getAmount());
                        slot.addProperty("displayName", contents[i].hasItemMeta() && contents[i].getItemMeta().hasDisplayName() 
                            ? contents[i].getItemMeta().getDisplayName() : contents[i].getType().toString());
                    } else {
                        slot.addProperty("material", "AIR");
                        slot.addProperty("amount", 0);
                    }
                    inventory.add(slot);
                }
                inventoryData.add("inventory", inventory);

                // 装備
                JsonObject armor = new JsonObject();
                ItemStack[] armorContents = player.getInventory().getArmorContents();
                String[] armorSlots = {"boots", "leggings", "chestplate", "helmet"};
                for (int i = 0; i < armorContents.length; i++) {
                    if (armorContents[i] != null && armorContents[i].getType() != org.bukkit.Material.AIR) {
                        armor.addProperty(armorSlots[i], armorContents[i].getType().toString());
                    } else {
                        armor.addProperty(armorSlots[i], "AIR");
                    }
                }
                inventoryData.add("armor", armor);

            } else {
                // オフラインプレイヤー（簡易版）
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    inventoryData.addProperty("playerName", playerName);
                    inventoryData.addProperty("online", false);
                    inventoryData.addProperty("message", "オフラインプレイヤーのデータは取得できません");
                } else {
                    String error = "プレイヤーが見つかりません";
                    exchange.sendResponseHeaders(404, error.getBytes("UTF-8").length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(error.getBytes("UTF-8"));
                    os.close();
                    return;
                }
            }

            String response = gson.toJson(inventoryData);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes("UTF-8"));
            os.close();
        }
    }
}