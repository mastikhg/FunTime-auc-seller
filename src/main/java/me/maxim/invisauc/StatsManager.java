package me.maxim.invisauc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
public class StatsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("InvisAuc-Stats");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Use dynamic paths so it saves inside your .minecraft folder safely
    private static File STATS_DIR;
    private static File STATS_FILE;
    private static File LOG_FILE;

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat MONEY_FORMATTER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        MONEY_FORMATTER = new DecimalFormat("#,##0", symbols);
    }

    // Call this to safely set up directories without triggering Windows permission blocks
    private static void initFiles() {
        if (STATS_DIR == null) {
            // This safely gets your .minecraft folder (or custom launcher instance folder)
            File gameDir = MinecraftClient.getInstance().runDirectory;
            STATS_DIR = new File(gameDir, "logs/InvisAuc");
            STATS_FILE = new File(STATS_DIR, "global_stats.json");
            LOG_FILE = new File(STATS_DIR, "money_tracker.log");
        }
        if (!STATS_DIR.exists()) {
            STATS_DIR.mkdirs();
        }
    }

    public static void startAutoSave() {}
    public static void addEarnings(double amount) {}

    public static void logMoneyToFile(String playerName, long money) {
        initFiles(); // Ensure folders exist

        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {

            String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
            pw.println("[" + timestamp + "] Player " + playerName + " Money Updated: $" + MONEY_FORMATTER.format(money));
            LOGGER.info("Logged money to file for " + playerName + ": $" + money);

        } catch (IOException e) {
            LOGGER.error("Не вдалося записати в money_tracker.log!", e);
        }
    }

    public static void updateBalanceFromChat(long actualMoney) {
        initFiles(); // Ensure folders exist

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String currentBotName = client.player.getName().getString();
        String formattedTime = ZonedDateTime.now(ZoneId.of("GMT+3"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        JsonObject root = new JsonObject();

        if (STATS_FILE.exists()) {
            try (FileReader reader = new FileReader(STATS_FILE)) {
                var element = JsonParser.parseReader(reader);
                if (element != null && element.isJsonObject()) {
                    root = element.getAsJsonObject();
                }
            } catch (Exception e) {
                LOGGER.error("Помилка читання файлу статистики", e);
            }
        }

        String targetKey = null;
        int maxIndex = -1;

        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("player_")) {
                try {
                    int index = Integer.parseInt(key.substring(7));
                    if (index > maxIndex) maxIndex = index;
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
        } else {
            targetKey = "player_" + (maxIndex + 1);
            playerData = new JsonObject();
            playerData.addProperty("name", currentBotName);
            root.add(targetKey, playerData);
        }

        playerData.addProperty("money", MONEY_FORMATTER.format(actualMoney));
        playerData.addProperty("now", formattedTime);

        try (FileWriter writer = new FileWriter(STATS_FILE)) {
            GSON.toJson(root, writer);
            LOGGER.info("[InvisAuc] JSON оновлено через сайдбар для {}: {}", currentBotName, actualMoney);

            // Log it right after saving JSON successfully!
            logMoneyToFile(currentBotName, actualMoney);

        } catch (IOException e) {
            LOGGER.error("Помилка запису файлу статистики", e);
        }
    }
}