package me.maxim.invisauc;

import me.maxim.invisauc.gui.TradeConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.potion.Potions;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.Objects;
import java.util.Random;

@SuppressWarnings({"SpellCheckingInspection", "ConstantConditions", "unused"})
public class InvisAuc implements ClientModInitializer {
    private static KeyBinding startKey, guiKey, invisibilityKey, restockKey, antiAfkToggleKey;
    private static boolean tradingEnabled = false;
    private static boolean autoInvisibilityEnabled = false;
    private static boolean manualRestockActive = false;
    private static boolean waitingForServer = false;
    private static boolean autoReconnectEnabled = true;
    private static boolean antiAfkEnabled = false;

    private static int timer = 0, currentBatch = 0, state = 0, pageCounter = 0;
    private static int drinkingTicks = 0, watchdogTimer = 0, checkTimer = 0, reconnectTimer = 0;
    private static int autoRetryTimer = 0;
    private static int lobbyCheckTimer = 0;
    private static int lobbyAfkTimer = 0;
    private static int scoreboardCheckTimer = 0;

    private static int antiAfkTimer = 0;
    private static int nextAntiAfkTarget = 1200;
    private static final Random random = new Random();

    private static final String PREFIX = "§8[§bIA§8]§r ";
    private static final String ANARCHY_COMMAND = "an223";
    private static ServerInfo lastServer;

    private static int currentPrice = 39000;
    private static int maxItems = 6;
    private static int sellAmount = 1;
    private static long maxBuyPrice = 1000000;
    private static final int requiredTotal = 256;
    private static ItemStack targetStack = ItemStack.EMPTY;

    private static long lastKnownMoney = -1;

    @Override
    public void onInitializeClient() {
        // Завантажуємо збережений конфіг за допомогою нашого нового менеджера
        ConfigManager.loadConfig();

        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "InvisAuc"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "InvisAuc"));
        invisibilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.invisibility", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "InvisAuc"));
        restockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.restock", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "InvisAuc"));
        antiAfkToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.antiafk", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "InvisAuc"));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (message == null) return;
            String lowerText = message.getString().toLowerCase();

            if (lowerText.contains("restart") || lowerText.contains("reboot") || lowerText.contains("lobby")) {
                waitingForServer = true;
                reconnectTimer = 1200;
            }
            if (state >= 60 && (lowerText.contains("insufficient") || lowerText.contains("недостаточно"))) {
                manualRestockActive = false;
                state = 0;
                sendMessage("§cRestock DISABLED.");
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player != null) {
            handleKeybindings(client);
        }

        if (client.world == null || client.player == null) {
            if (tradingEnabled && autoReconnectEnabled) handleAutoReconnect(client);
            return;
        }

        handleAntiAfk(client);
        handleLobbyLogic(client);

        scoreboardCheckTimer++;
        if (scoreboardCheckTimer >= 20) {
            updateMoneyFromSidebar(client);
            scoreboardCheckTimer = 0;
        }

        if (client.getCurrentServerEntry() != null) {
            lastServer = client.getCurrentServerEntry();
        }

        if (client.currentScreen instanceof TradeConfigScreen) return;

        if (waitingForServer) {
            if (reconnectTimer > 0) reconnectTimer--;
            else if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendChatCommand(ANARCHY_COMMAND);
                waitingForServer = false;
                sendMessage("§aReturned to " + ANARCHY_COMMAND);
            }
        }

        if (!tradingEnabled) return;

        if (state != 0) {
            watchdogTimer++;
            if (watchdogTimer > 350) {
                resetTrading();
                if (client.currentScreen != null) client.player.closeHandledScreen();
                sendMessage("§eReset.");
                return;
            }
        } else watchdogTimer = 0;

        if (autoInvisibilityEnabled && state < 50 && (client.currentScreen == null || client.currentScreen instanceof GameMenuScreen)) {
            if (checkTimer <= 0) {
                if (shouldDrink(client.player)) { startDrinkingProcess(client); return; }
                checkTimer = 60;
            } else checkTimer--;
        }

        if (timer > 0) { timer--; return; }

        switch (state) {
            case 0 -> processInventory(client);
            case 1 -> sliceStack(client);
            case 2 -> returnItems(client);
            case 3 -> executeSale(client);
            case 10 -> openAhMenu(client);
            case 11 -> handleStorage(client);
            case 50, 51 -> handleDrinking(client);
            case 60 -> startRestock(client);
            case 61 -> handleBuyingLogic(client);
            case 62 -> { if (client.currentScreen != null) client.player.closeHandledScreen(); state = 0; timer = 15; }
        }
    }

    private void updateMoneyFromSidebar(MinecraftClient client) {
        if (client.world == null) return;

        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective sidebarObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebarObjective == null) return;

        Collection<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(sidebarObjective);

        for (ScoreboardEntry entry : entries) {
            String owner = entry.owner();
            Team team = scoreboard.getScoreHolderTeam(owner);

            StringBuilder fullLine = new StringBuilder();

            if (team != null) {
                fullLine.append(team.getPrefix().getString());
            }

            if (entry.display() != null) {
                fullLine.append(entry.display().getString());
            } else {
                fullLine.append(owner);
            }

            if (team != null) {
                fullLine.append(team.getSuffix().getString());
            }

            String lineText = fullLine.toString();
            if (lineText.isEmpty()) continue;

            if (lineText.contains("Монет") || lineText.contains("Баланс") || lineText.contains("Money")) {
                String cleanLine = lineText.replaceAll("§.", "").trim();
                String digitsOnly = cleanLine.replaceAll("[^0-9]", "");

                if (!digitsOnly.isEmpty()) {
                    try {
                        long currentMoneyOnScreen = Long.parseLong(digitsOnly);

                        if (currentMoneyOnScreen != lastKnownMoney) {
                            lastKnownMoney = currentMoneyOnScreen;
                            StatsManager.updateBalanceFromChat(currentMoneyOnScreen);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    private void handleLobbyLogic(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return;

        boolean isInLobby = false;
        if (client.getCurrentServerEntry() != null) {
            String name = client.getCurrentServerEntry().name.toLowerCase();
            String address = client.getCurrentServerEntry().address.toLowerCase();
            if (name.contains("lobby") || name.contains("hub") || address.contains("lobby")) {
                isInLobby = true;
            }
        }

        if (isInLobby) {
            lobbyCheckTimer++;
            lobbyAfkTimer++;

            if (antiAfkEnabled && lobbyAfkTimer >= (1200 + random.nextInt(1201))) {
                double yawDelta = (random.nextBoolean() ? 1.0 : -1.0) * (0.1 + random.nextDouble() * 1.4);
                client.player.changeLookDirection(yawDelta, 0.0);
                if (client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendChatCommand(ANARCHY_COMMAND);
                }
                lobbyAfkTimer = 0;
            }

            if (lobbyCheckTimer >= 1200) {
                client.getNetworkHandler().sendChatCommand(ANARCHY_COMMAND);
                sendMessage("§eChecking " + ANARCHY_COMMAND + "...");
                lobbyCheckTimer = 0;
            }
        } else {
            lobbyCheckTimer = 0;
            lobbyAfkTimer = 0;
        }
    }

    private void handleAutoReconnect(MinecraftClient client) {
        if (client.currentScreen instanceof DisconnectedScreen) {
            autoRetryTimer++;
            if (autoRetryTimer >= 100) {
                if (lastServer != null) {
                    autoRetryTimer = 0;
                    waitingForServer = true;
                    reconnectTimer = 200;
                    ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), client, ServerAddress.parse(lastServer.address), lastServer, false, null);
                }
            }
        }
    }

    public static boolean isAutoReconnectEnabled() { return autoReconnectEnabled; }
    public static void setAutoReconnectEnabled(boolean enabled) { autoReconnectEnabled = enabled; ConfigManager.saveConfig(); }

    private void handleKeybindings(MinecraftClient client) {
        while (startKey.wasPressed()) {
            setTradingEnabled(!tradingEnabled);
            sendMessage(tradingEnabled ? "§aBot ENABLED" : "§cBot DISABLED");
            ConfigManager.saveConfig();
        }
        while (restockKey.wasPressed()) {
            manualRestockActive = !manualRestockActive;
            if (manualRestockActive && tradingEnabled) { state = 60; pageCounter = 0; }
            sendMessage(manualRestockActive ? "§6Restock ON" : "§7Restock OFF");
            ConfigManager.saveConfig();
        }
        while (invisibilityKey.wasPressed()) {
            autoInvisibilityEnabled = !autoInvisibilityEnabled;
            sendMessage(autoInvisibilityEnabled ? "§bAuto-Invis ON" : "§7Auto-Invis OFF");
            ConfigManager.saveConfig();
        }
        while (antiAfkToggleKey.wasPressed()) {
            antiAfkEnabled = !antiAfkEnabled;
            sendMessage(antiAfkEnabled ? "§aAnti-AFK ON" : "§7Anti-AFK OFF");
            ConfigManager.saveConfig();
        }
        if (guiKey.wasPressed()) client.setScreen(new TradeConfigScreen());
    }

    private void handleAntiAfk(MinecraftClient client) {
        if (!antiAfkEnabled) return;

        antiAfkTimer++;
        if (antiAfkTimer >= nextAntiAfkTarget) {
            double yawDelta = (random.nextBoolean() ? 1.0 : -1.0) * (0.1 + random.nextDouble() * 1.4);
            double pitchDelta = (random.nextBoolean() ? 1.0 : -1.0) * (0.05 + random.nextDouble() * 0.45);

            client.player.changeLookDirection(yawDelta, pitchDelta);

            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendChatCommand(ANARCHY_COMMAND);
            }

            antiAfkTimer = 0;
            nextAntiAfkTarget = 1200 + random.nextInt(1201);
        }
    }

    private void processInventory(MinecraftClient client) {
        if (waitingForServer) waitingForServer = false;
        if (currentBatch >= maxItems) { state = 10; timer = 10; return; }

        int totalInvis = 0;
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (isTargetItem(stack)) {
                totalInvis += stack.getCount();
                if (slot == -1) slot = i;
            }
        }

        if (slot != -1) {
            int sSlot = slot < 9 ? slot + 36 : slot;
            safeClick(client, sSlot, 0, sellAmount == 64 ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP);
            state = sellAmount == 64 ? 3 : 1; timer = 4;
        } else {
            if (manualRestockActive && totalInvis < requiredTotal && client.player.getInventory().getEmptySlot() != -1) {
                state = 60; timer = 5;
            } else {
                state = 10; timer = 10;
            }
        }
    }

    private void startRestock(MinecraftClient client) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("ah search Зелье невидимости");
            state = 61; timer = 40;
        }
    }

    private void handleBuyingLogic(MinecraftClient client) {
        if (!(client.currentScreen instanceof GenericContainerScreen container)) return;
        boolean itemBought = false;

        for (int i = 0; i < 45; i++) {
            ItemStack s = container.getScreenHandler().getSlot(i).getStack();
            int count = s.getCount();
            if (isTargetItem(s) && (count == 64 || count == 16)) {
                if (isLongDuration(s)) {
                    long price = extractPrice(s);
                    long limit = (count == 64) ? maxBuyPrice : (maxBuyPrice / 4);
                    if (price <= limit) {
                        safeClick(client, i, 0, SlotActionType.QUICK_MOVE);
                        itemBought = true; timer = 20; pageCounter = 0;
                        sendMessage("§aBought " + count + " for §6" + price);

                        StatsManager.addEarnings(-price);

                        break;
                    }
                }
            }
        }
        if (itemBought) state = 62;
        else handlePagination(client, container);
    }

    private void handlePagination(MinecraftClient client, GenericContainerScreen container) {
        int nextButtonSlot = 51;
        if (container.getScreenHandler().slots.size() <= nextButtonSlot) return;
        ItemStack nav = container.getScreenHandler().getSlot(nextButtonSlot).getStack();
        if (pageCounter < 10 && !nav.isEmpty()) {
            safeClick(client, nextButtonSlot, 0, SlotActionType.PICKUP);
            pageCounter++; timer = 25;
        } else {
            client.player.closeHandledScreen();
            state = 60; timer = 45; pageCounter = 0;
            sendMessage("§7Restarting search.");
        }
    }

    private boolean isLongDuration(ItemStack s) {
        var lore = s.getTooltip(net.minecraft.item.Item.TooltipContext.DEFAULT, MinecraftClient.getInstance().player, TooltipType.BASIC);
        for (Text line : lore) if (line.getString().contains("8:00")) return true;
        return false;
    }

    private long extractPrice(ItemStack s) {
        try {
            var lore = s.getTooltip(net.minecraft.item.Item.TooltipContext.DEFAULT, MinecraftClient.getInstance().player, TooltipType.BASIC);
            for (Text line : lore) {
                String text = line.getString().toLowerCase();
                if (text.contains("цена") || text.contains("стоимость") || text.contains("$") || text.contains("price")) {
                    String numeric = text.replaceAll("[^0-9]", "");
                    if (!numeric.isEmpty()) return Long.parseLong(numeric);
                }
            }
        } catch (Exception ignored) {}
        return Long.MAX_VALUE;
    }

    private void openAhMenu(MinecraftClient client) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("ah");
            state = 11; timer = 25;
        }
    }

    private void handleStorage(MinecraftClient client) {
        if (!(client.currentScreen instanceof GenericContainerScreen container)) return;
        String title = client.currentScreen.getTitle().getString().toLowerCase();
        if (!title.contains("хранилище") && !title.contains("storage")) {
            if (container.getScreenHandler().slots.size() > 46) {
                safeClick(client, 46, 0, SlotActionType.PICKUP);
                timer = 20;
            } else state = 62;
            return;
        }
        boolean found = false;
        for (int i = 0; i < 45; i++) {
            ItemStack s = container.getScreenHandler().getSlot(i).getStack();
            if (isTargetItem(s) && !s.isEmpty()) {
                safeClick(client, i, 0, SlotActionType.QUICK_MOVE);
                found = true; timer = 8; break;
            }
        }
        if (!found) { client.player.closeHandledScreen(); currentBatch = 0; state = 0; timer = 10; }
    }

    private void executeSale(MinecraftClient client) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("ah sell " + currentPrice);

            StatsManager.addEarnings(currentPrice);

            currentBatch++; timer = 25; state = 0;
        }
    }

    private void startDrinkingProcess(MinecraftClient client) {
        int potSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.isOf(Items.POTION)) {
                var c = s.get(DataComponentTypes.POTION_CONTENTS);
                if (c != null && (c.matches(Potions.LONG_INVISIBILITY) || c.matches(Potions.INVISIBILITY))) { potSlot = i; break; }
            }
        }
        if (potSlot != -1) {
            if (potSlot < 9) client.player.getInventory().selectedSlot = potSlot;
            else { safeClick(client, potSlot, 8, SlotActionType.SWAP); client.player.getInventory().selectedSlot = 8; }
            state = 50; timer = 5;
        }
    }

    private void handleDrinking(MinecraftClient client) {
        if (state == 50) { client.options.useKey.setPressed(true); drinkingTicks = 42; state = 51; }
        else {
            if (drinkingTicks > 0) { drinkingTicks--; timer = 1; }
            else {
                client.options.useKey.setPressed(false);
                for (int i = 0; i < 36; i++) if (client.player.getInventory().getStack(i).isOf(Items.GLASS_BOTTLE))
                    safeClick(client, i < 9 ? i + 36 : i, 1, SlotActionType.THROW);
                state = 0; timer = 8;
            }
        }
    }

    private void safeClick(MinecraftClient client, int slot, int button, SlotActionType type) {
        if (client.interactionManager != null && client.player.currentScreenHandler != null)
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, button, type, client.player);
    }

    private boolean isTargetItem(ItemStack s) {
        if (targetStack.isEmpty() || s.isEmpty() || s.getItem() != targetStack.getItem()) return false;
        PotionContentsComponent c1 = s.get(DataComponentTypes.POTION_CONTENTS);
        PotionContentsComponent c2 = targetStack.get(DataComponentTypes.POTION_CONTENTS);
        if (c1 != null && c2 != null) return Objects.equals(c1.potion(), c2.potion());
        return s.getName().getString().equals(targetStack.getName().getString());
    }

    private boolean shouldDrink(ClientPlayerEntity p) {
        StatusEffectInstance e = p.getStatusEffect(StatusEffects.INVISIBILITY);
        return e == null || e.getDuration() < 1000;
    }

    public static void setTradingEnabled(boolean enabled) { tradingEnabled = enabled; if (!enabled) resetTrading(); }

    private static void resetTrading() {
        state = 0;
        timer = 0;
        currentBatch = 0;
        watchdogTimer = 0;
        antiAfkTimer = 0;
        pageCounter = 0;
        lastKnownMoney = -1;
        nextAntiAfkTarget = 1200 + random.nextInt(1201);
    }

    private void sliceStack(MinecraftClient client) { safeClick(client, 36, 1, SlotActionType.PICKUP); state = 2; timer = 3; }
    private void returnItems(MinecraftClient client) { int empty = client.player.getInventory().getEmptySlot(); if (empty != -1) safeClick(client, empty < 9 ? empty + 36 : empty, 0, SlotActionType.PICKUP); state = 3; timer = 4; }
    private static void sendMessage(String m) { if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(Text.literal(PREFIX + m), false); }

    public static void setCurrentPrice(int p) { currentPrice = p; ConfigManager.saveConfig(); }
    public static int getCurrentPrice() { return currentPrice; }
    public static void setMaxItems(int c) { maxItems = c; ConfigManager.saveConfig(); }
    public static int getMaxItems() { return maxItems; }
    public static void setSellAmount(int a) { sellAmount = a; ConfigManager.saveConfig(); }
    public static int getSellAmount() { return sellAmount; }
    public static long getMaxBuyPrice() { return maxBuyPrice; }
    public static void setMaxBuyPrice(long p) { maxBuyPrice = p; ConfigManager.saveConfig(); }
    public static ItemStack getTargetStack() { return targetStack; }
    public static void setTargetStack(ItemStack s) { if (s != null && !s.isEmpty()) { targetStack = s.copy(); ConfigManager.saveConfig(); } }
}