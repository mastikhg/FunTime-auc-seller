package me.maxim.invisauc;

import me.maxim.invisauc.gui.TradeConfigScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
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

public class InvisAuc implements ModInitializer {
    private static KeyBinding startKey, guiKey;
    private static boolean enabled = false;
    private static int timer = 0, currentBatch = 0, state = 0;
    private static int clickCounter = 0;
    private static int drinkingTicks = 0;
    private static final Random random = new Random();

    private static int invisibilityCheckTimer = 0;
    private static final int RECHECK_INTERVAL = 200; // Check every 10 seconds

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
            sendMessage("§aTarget updated!");
        }
    }

    @Override
    public void onInitialize() {
        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "InvisAuc"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "InvisAuc"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            while (startKey.wasPressed()) {
                enabled = !enabled;
                state = 0; timer = 0; currentBatch = 0;
                invisibilityCheckTimer = 0; // Trigger check immediately
                ignoredBlocks.clear();
                client.options.useKey.setPressed(false);
                sendMessage(enabled ? "§aBot ON" : "§cBot OFF");
            }
            while (guiKey.wasPressed()) client.setScreen(new TradeConfigScreen());
            if (enabled) onTick(client);
        });
    }

    private void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null || client.getNetworkHandler() == null || client.world == null) return;

        // Smart Invisibility Check
        if (state < 50) {
            if (invisibilityCheckTimer <= 0) {
                if (shouldDrinkInvisibility(player)) {
                    useInvisibilityPotion(client);
                    return;
                }
                invisibilityCheckTimer = RECHECK_INTERVAL;
            } else {
                invisibilityCheckTimer--;
            }
        }

        if (timer > 0) { timer--; return; }

        switch (state) {
            case 0 -> processInventory(client);
            case 1 -> sliceStack(client);
            case 2 -> returnItems(client);
            case 3 -> executeSale(client);
            case 10, 11 -> handleStorage(client);
            case 20 -> handleSearching(client);
            case 50 -> {
                client.options.useKey.setPressed(true);
                drinkingTicks = 45;
                state = 51;
            }
            case 51 -> {
                if (drinkingTicks > 0) {
                    drinkingTicks--;
                    timer = 1;
                } else {
                    client.options.useKey.setPressed(false);
                    dropEmptyBottles(client);
                    invisibilityCheckTimer = RECHECK_INTERVAL;
                    state = 0;
                    timer = 20;
                    sendMessage("§bPotion consumed!");
                }
            }
        }
    }

    private boolean shouldDrinkInvisibility(ClientPlayerEntity player) {
        StatusEffectInstance effect = player.getStatusEffect(StatusEffects.INVISIBILITY);
        if (effect == null) return true; // No effect - drink!
        return effect.getDuration() < 4800; // Less than 4 minutes - drink!
    }

    private void useInvisibilityPotion(MinecraftClient client) {
        var player = client.player;
        var manager = client.interactionManager;
        if (player == null || manager == null) return;

        int potSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.POTION)) {
                var contents = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (contents != null && (contents.matches(Potions.LONG_INVISIBILITY) || contents.matches(Potions.INVISIBILITY))) {
                    potSlot = i; break;
                }
            }
        }

        if (potSlot != -1) {
            if (potSlot < 9) {
                player.getInventory().selectedSlot = potSlot;
                state = 50;
                timer = 5;
            } else {
                manager.clickSlot(player.currentScreenHandler.syncId, potSlot, 8, SlotActionType.SWAP, player);
                player.getInventory().selectedSlot = 8;
                state = 50;
                timer = 10;
            }
        } else {
            invisibilityCheckTimer = 1200; // No potion found, check again in 1m
            state = 0;
        }
    }

    private void dropEmptyBottles(MinecraftClient client) {
        var player = client.player;
        var manager = client.interactionManager;
        if (player == null || manager == null) return;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.GLASS_BOTTLE)) {
                int slot = i < 9 ? i + 36 : i;
                manager.clickSlot(player.currentScreenHandler.syncId, slot, 1, SlotActionType.THROW, player);
            }
        }
    }

    // --- Standard Logic (Inventory/Sales/Storage) ---
    private void processInventory(MinecraftClient client) {
        var handler = client.getNetworkHandler();
        var manager = client.interactionManager;
        var player = client.player;
        if (handler == null || manager == null || player == null) return;

        if (currentBatch >= maxItems) {
            currentBatch = 0;
            handler.sendChatCommand("ah");
            state = 10; timer = 15;
            return;
        }
        int slot = findTargetItemSlot(player);
        if (slot != -1) {
            manager.clickSlot(player.currentScreenHandler.syncId, slot < 9 ? slot + 36 : slot, 0, SlotActionType.PICKUP, player);
            state = 1; timer = 5; clickCounter = 0;
        } else {
            state = 20; timer = 5;
        }
    }

    private void sliceStack(MinecraftClient client) {
        var manager = client.interactionManager;
        var player = client.player;
        if (manager == null || player == null) return;
        int button = (sellAmount > 1) ? 0 : 1;
        manager.clickSlot(player.currentScreenHandler.syncId, 36, button, SlotActionType.PICKUP, player);
        clickCounter++;
        if (sellAmount <= 1 || clickCounter >= sellAmount) { state = 2; timer = 5; } else { timer = 2; }
    }

    private void returnItems(MinecraftClient client) {
        var manager = client.interactionManager;
        var player = client.player;
        if (manager == null || player == null) return;
        int empty = player.getInventory().getEmptySlot();
        int target = (empty != -1 ? (empty < 9 ? empty + 36 : empty) : 9);
        manager.clickSlot(player.currentScreenHandler.syncId, target, 0, SlotActionType.PICKUP, player);
        state = 3; timer = 6;
    }

    private void executeSale(MinecraftClient client) {
        var handler = client.getNetworkHandler();
        var player = client.player;
        if (handler == null || player == null) return;
        player.getInventory().selectedSlot = 0;
        handler.sendChatCommand("ah sell " + currentPrice);
        currentBatch++; state = 0; timer = 25 + random.nextInt(10);
    }

    private void handleSearching(MinecraftClient client) {
        var player = client.player;
        var manager = client.interactionManager;
        if (player == null || manager == null) return;
        BlockPos containerPos = findNearbyContainer(client);
        if (containerPos != null) {
            currentContainerPos = containerPos;
            BlockHitResult hit = new BlockHitResult(new Vec3d(containerPos.getX(), containerPos.getY(), containerPos.getZ()), Direction.UP, containerPos, false);
            manager.interactBlock(player, Hand.MAIN_HAND, hit);
            state = 11; timer = 15;
        } else {
            sendMessage("§cNo containers found!");
            enabled = false;
        }
    }

    private BlockPos findNearbyContainer(MinecraftClient client) {
        var player = client.player;
        var world = client.world;
        if (player == null || world == null) return null;
        BlockPos playerPos = player.getBlockPos();
        int r = 4;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (!ignoredBlocks.contains(pos)) {
                        var bState = world.getBlockState(pos);
                        if (bState.isOf(Blocks.BARREL) || bState.isOf(Blocks.CHEST)) return pos;
                    }
                }
            }
        }
        return null;
    }

    private void handleStorage(MinecraftClient client) {
        var manager = client.interactionManager;
        var player = client.player;
        if (manager == null || player == null) return;
        if (state == 10) {
            if (client.currentScreen instanceof GenericContainerScreen container) {
                manager.clickSlot(container.getScreenHandler().syncId, 46, 0, SlotActionType.PICKUP, player);
                state = 11; timer = 10;
            } else if (timer == 0) state = 0;
        } else if (state == 11) {
            if (client.currentScreen instanceof GenericContainerScreen container) {
                int slot = findInMenu(container);
                int invStart = container.getScreenHandler().slots.size() - 36;
                if (slot != -1 && slot < invStart && player.getInventory().getEmptySlot() != -1) {
                    manager.clickSlot(container.getScreenHandler().syncId, slot, 0, SlotActionType.QUICK_MOVE, player);
                    timer = 8;
                } else {
                    if (currentContainerPos != null) {
                        ignoredBlocks.add(currentContainerPos);
                        currentContainerPos = null;
                    }
                    player.closeHandledScreen();
                    state = 0; timer = 12;
                }
            } else if (timer == 0) state = 0;
        }
    }

    private int findTargetItemSlot(ClientPlayerEntity player) {
        for (int i = 0; i < 36; i++) {
            if (isTargetItem(player.getInventory().getStack(i))) return i;
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