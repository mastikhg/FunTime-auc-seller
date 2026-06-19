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
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
public class StatsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("InvisAuc-Stats");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_DIR = "C:\\MinecraftLogs";
    private static File STATS_DIR;
    private static File STATS_FILE;


    // private static File LOG_FILE;

    private static final DecimalFormat MONEY_FORMATTER;

    private static long totalEarnings = 0;
    private static long currentBalance = 0;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        MONEY_FORMATTER = new DecimalFormat("#,##0", symbols);
    }

    private static void initFiles() {
        if (STATS_DIR == null) {
            STATS_DIR = new File(CONFIG_DIR);
            STATS_FILE = new File(STATS_DIR, "global_stats.json");
            // LOG_FILE = new File(STATS_DIR, "money_tracker.log");
        }
        if (!STATS_DIR.exists()) {
            if (!STATS_DIR.mkdirs()) {
                LOGGER.error("Не вдалося створити папку C:\\MinecraftLogs");
            }
        }
    }

    public static void startAutoSave() {}

    public static void addEarnings(long amount) {
        totalEarnings += amount;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            updateBalanceFromChat(currentBalance);
        }
    }

    public static void addEarnings(double amount) {
        addEarnings((long) amount);
    }


    public static void logMoneyToFile(String playerName, long money) {
        /* initFiles();
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
            pw.println("[" + timestamp + "] Player " + playerName + " Money Updated: $" + MONEY_FORMATTER.format(money));
            LOGGER.info("Logged money to file for " + playerName + ": $" + money);
        } catch (IOException e) {
            LOGGER.error("Не вдалося записати в money_tracker.log!", e);
        }
        */
    }

    public static void updateBalanceFromChat(long actualMoney) {
        currentBalance = actualMoney;
        initFiles();

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


            // logMoneyToFile(currentBotName, actualMoney);

        } catch (IOException e) {
            LOGGER.error("Помилка запису файлу статистики", e);
        }
    }

    public static long getCurrentBalance() { return currentBalance; }
    public static void setCurrentBalance(long balance) { currentBalance = balance; }
    public static long getTotalEarnings() { return totalEarnings; }
    public static void setTotalEarnings(long earnings) { totalEarnings = earnings; }
}