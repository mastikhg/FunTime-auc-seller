package me.maxim.invisauc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {

    private static final String CONFIG_DIR = "C:\\MinecraftLogs";
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    public static void saveConfig() {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("[InvisAuc] Не вдалося створити папку C:\\MinecraftLogs");
            }


            JsonObject json = new JsonObject();
            json.addProperty("currentPrice", InvisAuc.getCurrentPrice());
            json.addProperty("maxItems", InvisAuc.getMaxItems());
            json.addProperty("sellAmount", InvisAuc.getSellAmount());
            json.addProperty("maxBuyPrice", InvisAuc.getMaxBuyPrice());
            json.addProperty("autoReconnect", InvisAuc.isAutoReconnectEnabled());

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }


            long currentMoney = StatsManager.getCurrentBalance();
            StatsManager.updateBalanceFromChat(currentMoney);

        } catch (IOException e) {
            System.err.println("[InvisAuc] Помилка збереження конфігу: " + e.getMessage());
        }
    }

    public static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    if (json.has("currentPrice")) InvisAuc.setCurrentPrice(json.get("currentPrice").getAsInt());
                    if (json.has("maxItems")) InvisAuc.setMaxItems(json.get("maxItems").getAsInt());
                    if (json.has("sellAmount")) InvisAuc.setSellAmount(json.get("sellAmount").getAsInt());
                    if (json.has("maxBuyPrice")) InvisAuc.setMaxBuyPrice(json.get("maxBuyPrice").getAsLong());
                    if (json.has("autoReconnect")) InvisAuc.setAutoReconnectEnabled(json.get("autoReconnect").getAsBoolean());
                }
            } catch (IOException e) {
                System.err.println("[InvisAuc] Помилка завантаження конфігу: " + e.getMessage());
            }
        }


        File statsFile = new File(CONFIG_DIR, "global_stats.json");
        MinecraftClient client = MinecraftClient.getInstance();
        if (statsFile.exists() && client.player != null) {
            String currentNickname = client.player.getName().getString();
            try (FileReader reader = new FileReader(statsFile)) {
                var element = JsonParser.parseReader(reader);
                if (element != null && element.isJsonObject()) {
                    JsonObject root = element.getAsJsonObject();

                    // Перебираємо player_0, player_1 і т.д.
                    for (int i = 0; i < 10; i++) {
                        String key = "player_" + i;
                        if (root.has(key)) {
                            JsonObject playerObj = root.getAsJsonObject(key);
                            if (playerObj.has("name") && playerObj.get("name").getAsString().equalsIgnoreCase(currentNickname)) {
                                if (playerObj.has("money")) {
                                    String moneyStr = playerObj.get("money").getAsString().replaceAll("[^0-9]", "");
                                    if (!moneyStr.isEmpty()) {
                                        StatsManager.setCurrentBalance(Long.parseLong(moneyStr));
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[InvisAuc] Не вдалося підтягнути старий баланс при старті: " + e.getMessage());
            }
        }
    }
}