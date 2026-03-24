package me.maxim.invisauc;

import me.maxim.invisauc.gui.TradeConfigScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@SuppressWarnings("unused")
public class InvisAuc implements ModInitializer {
    private static KeyBinding startKey, guiKey;
    private static boolean enabled = false;
    private static int timer = 0, currentBatch = 0, state = 0;
    private static int clickCounter = 0;
    private static final Random random = new Random();

    private static final Set<BlockPos> ignoredBlocks = new HashSet<>();
    private static BlockPos currentContainerPos = null;

    private static final String PREFIX = "§8[§bIA§8]§r ";
    private static int currentPrice = 39000;
    private static int maxItems = 6;
    private static int sellAmount = 1;
    private static ItemStack targetStack = ItemStack.EMPTY;

    public static void setCurrentPrice(int price) { currentPrice = price; }
    public static int getCurrentPrice() { return currentPrice; }
    public static void setMaxItems(int count) { maxItems = count; }
    public static int getMaxItems() { return maxItems; }
    public static void setSellAmount(int amount) { sellAmount = amount; }
    public static int getSellAmount() { return sellAmount; }
    public static ItemStack getTargetStack() { return targetStack; }

    public static void setWaitTime(int ignored) {}
    public static int getWaitTimeSeconds() { return 1; }

    public static void setTargetStack(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            targetStack = stack.copy();
            sendMessage("§aSET");
        }
    }

    @Override
    public void onInitialize() {
        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "InvisAuc"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "InvisAuc"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (startKey.wasPressed()) {
                enabled = !enabled;
                state = 0; timer = 0; currentBatch = 0;
                ignoredBlocks.clear();
                sendMessage(enabled ? "§aON" : "§cOFF");
            }
            while (guiKey.wasPressed()) client.setScreen(new TradeConfigScreen());
            if (enabled) onTick(client);
        });
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null || client.world == null) return;
        if (timer > 0) { timer--; return; }

        switch (state) {
            case 0 -> {
                if (currentBatch >= maxItems) {
                    currentBatch = 0;
                    client.getNetworkHandler().sendChatCommand("ah");
                    state = 10; timer = 15;
                    return;
                }
                int slot = findTargetItemSlot(client);
                if (slot != -1) {
                    client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot < 9 ? slot + 36 : slot, 0, SlotActionType.PICKUP, client.player);
                    state = 1; timer = 5; clickCounter = 0;
                } else {
                    state = 20; timer = 5;
                }
            }
            case 1 -> {
                int button = (sellAmount > 1) ? 0 : 1;
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 36, button, SlotActionType.PICKUP, client.player);
                clickCounter++;
                if (sellAmount <= 1 || clickCounter >= sellAmount) { state = 2; timer = 5; } else { timer = 2; }
            }
            case 2 -> {
                int empty = client.player.getInventory().getEmptySlot();
                int target = (empty != -1 ? (empty < 9 ? empty + 36 : empty) : 9);
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, target, 0, SlotActionType.PICKUP, client.player);
                state = 3; timer = 6;
            }
            case 3 -> {
                client.player.getInventory().selectedSlot = 0;
                client.getNetworkHandler().sendChatCommand("ah sell " + currentPrice);
                currentBatch++; state = 0; timer = 22 + random.nextInt(5);
            }
            case 10, 11 -> handleStorage(client);
            case 20 -> handleSearching(client);
        }
    }

    private void handleSearching(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        BlockPos containerPos = findNearbyContainer(client);
        if (containerPos != null) {
            currentContainerPos = containerPos;
            BlockHitResult hit = new BlockHitResult(new Vec3d(containerPos.getX(), containerPos.getY(), containerPos.getZ()), Direction.UP, containerPos, false);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            state = 11; timer = 15;
        } else {
            sendMessage("§cNo containers found!");
            enabled = false;
        }
    }

    private BlockPos findNearbyContainer(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        BlockPos playerPos = client.player.getBlockPos();
        int r = 4;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (!ignoredBlocks.contains(pos)) {
                        var state = client.world.getBlockState(pos);
                        if (state.isOf(Blocks.BARREL) || state.isOf(Blocks.CHEST)) return pos;
                    }
                }
            }
        }
        return null;
    }

    private void handleStorage(MinecraftClient client) {
        if (client.interactionManager == null || client.player == null) return;
        if (state == 10) {
            if (client.currentScreen instanceof GenericContainerScreen container) {
                client.interactionManager.clickSlot(container.getScreenHandler().syncId, 46, 0, SlotActionType.PICKUP, client.player);
                state = 11; timer = 10;
            } else if (timer == 0) state = 0;
        } else if (state == 11) {
            if (client.currentScreen instanceof GenericContainerScreen container) {
                int slot = findInMenu(container);
                int invStart = container.getScreenHandler().slots.size() - 36;
                if (slot != -1 && slot < invStart && client.player.getInventory().getEmptySlot() != -1) {
                    client.interactionManager.clickSlot(container.getScreenHandler().syncId, slot, 0, SlotActionType.QUICK_MOVE, client.player);
                    timer = 8;
                } else {
                    if (currentContainerPos != null) {
                        ignoredBlocks.add(currentContainerPos);
                        currentContainerPos = null;
                    }
                    client.player.closeHandledScreen();
                    state = 0; timer = 12;
                }
            } else if (timer == 0) state = 0;
        }
    }

    private int findTargetItemSlot(MinecraftClient client) {
        if (client.player == null) return -1;
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