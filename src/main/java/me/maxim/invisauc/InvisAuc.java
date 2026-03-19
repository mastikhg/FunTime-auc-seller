package me.maxim.invisauc;

import me.maxim.invisauc.gui.TradeConfigScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

public class InvisAuc implements ModInitializer {
    private static KeyBinding startKey, guiKey;
    private static boolean enabled = false;
    private static int timer = 0, currentBatch = 0, state = 0;

    private static final String PREFIX = "§8[§bInvisAuc§8]§r ";
    private static int currentPrice = 39000;
    private static int maxItems = 6;
    private static int waitTimeTicks = 300;
    private static ItemStack targetStack = ItemStack.EMPTY;

    public static void setCurrentPrice(int price) { currentPrice = price; }
    public static int getCurrentPrice() { return currentPrice; }
    public static void setMaxItems(int count) { maxItems = count; }
    public static int getMaxItems() { return maxItems; }
    public static void setWaitTime(int seconds) { waitTimeTicks = seconds * 20; }
    public static int getWaitTimeSeconds() { return waitTimeTicks / 20; }
    public static ItemStack getTargetStack() { return targetStack; }

    public static void setTargetStack(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            targetStack = stack.copy();
            sendMessage("§aРежим конвеєра активовано.");
        }
    }

    @Override
    public void onInitialize() {
        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "InvisAuc"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "InvisAuc"));

        // Чат нам більше не потрібен для логіки, бо ми не чекаємо "Sold"
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (startKey.wasPressed()) {
                enabled = !enabled;
                state = 0; timer = 0; currentBatch = 0;
                sendMessage(enabled ? "§aКОНВЕЄР ЗАПУЩЕНО" : "§cКОНВЕЄР ЗУПИНЕНО");
            }
            while (guiKey.wasPressed()) client.setScreen(new TradeConfigScreen());
            if (enabled) onTick(client);
        });
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        if (timer > 0) { timer--; return; }

        switch (state) {
            case 0 -> { // КРОК 1: ВИСТАВИТИ 6 ШТУК
                if (currentBatch >= maxItems) {
                    // Виставили 6 — скидаємо лічильник і йдемо в AH
                    currentBatch = 0;
                    client.getNetworkHandler().sendChatCommand("ah");
                    state = 10; timer = 40;
                    return;
                }

                int slot = findTargetItemSlot(client);
                if (slot != -1) {
                    client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot < 9 ? slot + 36 : slot, 0, SlotActionType.PICKUP, client.player);
                    state = 1; timer = 12;
                } else {
                    // Якщо раптом зілля закінчились раніше ніж 6 — все одно в AH
                    client.getNetworkHandler().sendChatCommand("ah");
                    state = 10; timer = 40;
                }
            }
            case 1 -> { // ПКМ (взяти 1 шт)
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 36, 1, SlotActionType.PICKUP, client.player);
                state = 2; timer = 12;
            }
            case 2 -> { // Повернення решти
                int empty = client.player.getInventory().getEmptySlot();
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, (empty != -1 ? (empty < 9 ? empty + 36 : empty) : 9), 0, SlotActionType.PICKUP, client.player);
                state = 3; timer = 12;
            }
            case 3 -> { // Продаж
                client.player.getInventory().selectedSlot = 0;
                client.getNetworkHandler().sendChatCommand("ah sell " + currentPrice);
                currentBatch++; // Рахуємо скільки виставили в цій ітерації
                state = 0; timer = 35;
            }
            case 10 -> { // КРОК 2: ВХІД У СХОВИЩЕ
                if (client.currentScreen instanceof GenericContainerScreen container) {
                    client.interactionManager.clickSlot(container.getScreenHandler().syncId, 46, 0, SlotActionType.PICKUP, client.player);
                    state = 11; timer = 40;
                } else if (timer == 0) state = 0;
            }
            case 11 -> { // КРОК 3: ЗАБРАТИ ВСЕ
                if (client.currentScreen instanceof GenericContainerScreen container) {
                    int slot = findInMenu(container);
                    int invStart = container.getScreenHandler().slots.size() - 36;

                    // Забираємо поки є місце в інвентарі і є зілля в меню
                    if (slot != -1 && slot < invStart && client.player.getInventory().getEmptySlot() != -1) {
                        client.interactionManager.clickSlot(container.getScreenHandler().syncId, slot, 0, SlotActionType.QUICK_MOVE, client.player);
                        timer = 12;
                    } else {
                        // КРОК 4: ПОВТОРИТИ (закриваємо і йдемо на нове коло)
                        client.player.closeHandledScreen();
                        state = 0; timer = 50;
                    }
                } else { state = 0; }
            }
        }
    }

    private int findTargetItemSlot(MinecraftClient client) {
        for (int i = 0; i < 36; i++) {
            if (isTargetItem(client.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private int findInMenu(GenericContainerScreen screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            if (isTargetItem(screen.getScreenHandler().getSlot(i).getStack())) return i;
        }
        return -1;
    }

    private boolean isTargetItem(ItemStack stack) {
        if (targetStack.isEmpty() || stack.isEmpty()) return false;
        if (stack.getItem() != targetStack.getItem()) return false;
        var c1 = stack.get(DataComponentTypes.POTION_CONTENTS);
        var c2 = targetStack.get(DataComponentTypes.POTION_CONTENTS);
        if (c1 != null && c2 != null) return Objects.equals(c1.potion(), c2.potion());
        return stack.getName().getString().equals(targetStack.getName().getString());
    }

    private static void sendMessage(String msg) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(PREFIX + msg), false);
        }
    }
}