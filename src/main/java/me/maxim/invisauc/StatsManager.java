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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StatsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File STATS_DIR = new File("C:/Minecraftlogs");
    private static final File STATS_FILE = new File(STATS_DIR, "global_stats.json");

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static long sessionEarned = 0;

    private static final DecimalFormat MONEY_FORMATTER;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        MONEY_FORMATTER = new DecimalFormat("#,##0;-#,##0", symbols);
    }

    public static void startAutoSave() {
        SCHEDULER.scheduleAtFixedRate(StatsManager::saveStats, 10, 10, TimeUnit.MINUTES);
    }

    public static void addEarnings(double amount) {
        sessionEarned += (long) amount;
    }

    public static void saveStats() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String currentBotName = client.player.getName().getString();

        String formattedTime = ZonedDateTime.now(ZoneId.of("GMT+3"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        JsonObject root = new JsonObject();

        if (!STATS_DIR.exists()) {
            STATS_DIR.mkdirs();
        }

        if (STATS_FILE.exists()) {
            try (FileReader reader = new FileReader(STATS_FILE)) {
                var element = JsonParser.parseReader(reader);
                if (element != null && element.isJsonObject()) {
                    root = element.getAsJsonObject();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String targetKey = null;
        int maxIndex = -1;

        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("player_")) {
                try {
                    int index = Integer.parseInt(key.substring(7));
                    if (index > maxIndex) {
                        maxIndex = index;
                    }
                } catch (NumberFormatException ignored) {}

                JsonObject playerObj = entry.getValue().getAsJsonObject();
                if (playerObj.has("name") && playerObj.get("name").getAsString().equals(currentBotName)) {
                    targetKey = key;
                }
            }
        }

        JsonObject playerData;
        if (targetKey != null) {
            playerData = root.getAsJsonObject(targetKey);
            long currentMoney = 0;
            if (playerData.has("money")) {
                try {
                    String moneyStr = playerData.get("money").getAsString();
                    boolean isNegative = moneyStr.startsWith("-");
                    moneyStr = moneyStr.replaceAll("[^0-9]", "");
                    currentMoney = Long.parseLong(moneyStr);
                    if (isNegative) {
                        currentMoney = -currentMoney;
                    }
                } catch (Exception ignored) {}
            }
            playerData.addProperty("money", MONEY_FORMATTER.format(currentMoney + sessionEarned));
        } else {
            targetKey = "player_" + (maxIndex + 1);
            playerData = new JsonObject();
            playerData.addProperty("name", currentBotName);
            playerData.addProperty("money", MONEY_FORMATTER.format(sessionEarned));
            root.add(targetKey, playerData);
        }

        playerData.addProperty("now", formattedTime);
        sessionEarned = 0;

        try (FileWriter writer = new FileWriter(STATS_FILE)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}