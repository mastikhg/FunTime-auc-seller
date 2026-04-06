package me.maxim.invisauc;

import me.maxim.invisauc.gui.TradeConfigScreen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
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
    private static KeyBinding startKey, guiKey, invisibilityKey;
    private static boolean tradingEnabled = false;
    private static boolean autoInvisibilityEnabled = false;

    private static int timer = 0, currentBatch = 0, state = 0;
    private static int drinkingTicks = 0;
    private static final Random random = new Random();

    private static int checkTimer = 0;
    private static final String PREFIX = "§8[§bIA§8]§r ";

    private static int currentPrice = 39000;
    private static int maxItems = 6;
    private static int sellAmount = 1;
    private static ItemStack targetStack = ItemStack.EMPTY;
    private static final Set<BlockPos> ignoredBlocks = new HashSet<>();
    private static BlockPos currentContainerPos = null;

    @Override
    public void onInitialize() {
        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "InvisAuc"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "InvisAuc"));
        invisibilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.invisibility", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "InvisAuc"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (startKey.wasPressed()) {
                tradingEnabled = !tradingEnabled;
                resetTrading();
                sendMessage(tradingEnabled ? "§aBot ENABLED" : "§cBot DISABLED");
            }
            while (invisibilityKey.wasPressed()) {
                autoInvisibilityEnabled = !autoInvisibilityEnabled;
                sendMessage(autoInvisibilityEnabled ? "§bAuto-Invis ON" : "§7Auto-Invis OFF");
            }
            if (guiKey.wasPressed()) client.setScreen(new TradeConfigScreen());
            onTick(client);
        });
    }

    private void resetTrading() { state = 0; timer = 0; currentBatch = 0; ignoredBlocks.clear(); }

    private void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null || client.world == null) return;

        if (autoInvisibilityEnabled && state < 50) {
            if (checkTimer <= 0) {
                if (shouldDrink(player)) { startDrinkingProcess(client); return; }
                checkTimer = 100;
            } else checkTimer--;
        }

        if (timer > 0) { timer--; return; }

        switch (state) {
            case 0 -> { if (tradingEnabled) processInventory(client); }
            case 1 -> sliceStack(client);
            case 2 -> returnItems(client);
            case 3 -> executeSale(client);
            case 4 -> mergeSmallStacks(client);
            case 10, 11 -> handleStorage(client);
            case 20 -> handleSearching(client);
            case 50, 51 -> handleDrinking(client);
        }
    }

    private void processInventory(MinecraftClient client) {
        var player = client.player;
        var manager = client.interactionManager;
        var network = client.getNetworkHandler();
        if (player == null || manager == null || network == null) return;

        if (currentBatch >= maxItems) {
            currentBatch = 0;
            network.sendChatCommand("ah");
            state = 10; timer = 15; return;
        }

        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isTargetItem(stack) && stack.getCount() >= sellAmount) { slot = i; break; }
        }

        if (slot != -1) {
            int syncId = player.currentScreenHandler.syncId;
            int serverSlot = slot < 9 ? slot + 36 : slot;

            // INSTANT FULL STACK (64)
            if (sellAmount == 64) {
                manager.clickSlot(syncId, serverSlot, 0, SlotActionType.QUICK_MOVE, player);
                state = 3; timer = 5;
            } else {
                manager.clickSlot(syncId, serverSlot, 0, SlotActionType.PICKUP, player);
                state = 1; timer = 3;
            }
        } else state = 4;
    }

    private void sliceStack(MinecraftClient client) {
        var manager = client.interactionManager;
        var player = client.player;
        if (manager == null || player == null) return;

        int syncId = player.currentScreenHandler.syncId;
        ItemStack inSlot = player.getInventory().getStack(0);
        int current = inSlot.getCount();

        if (current == sellAmount) { state = 2; timer = 3; return; }

        ItemStack inCursor = player.currentScreenHandler.getCursorStack();
        int inHand = inCursor.getCount();
        int needed = sellAmount - current;

        // SMART SLICE: If over 32, drop half (32) at once
        if (sellAmount > 32 && current == 0 && inHand >= 32) {
            manager.clickSlot(syncId, 36, 1, SlotActionType.PICKUP, player); // Right click puts half
            timer = 4;
        } else if (inHand > 0) {
            manager.clickSlot(syncId, 36, 1, SlotActionType.PICKUP, player);
            timer = 2;
        } else state = 0;
    }

    private void returnItems(MinecraftClient client) {
        var manager = client.interactionManager;
        var player = client.player;
        if (manager == null || player == null) return;

        if (!player.currentScreenHandler.getCursorStack().isEmpty()) {
            int empty = player.getInventory().getEmptySlot();
            if (empty != -1) {
                manager.clickSlot(player.currentScreenHandler.syncId, empty < 9 ? empty + 36 : empty, 0, SlotActionType.PICKUP, player);
                timer = 5;
            }
        }
        state = 3;
    }

    private void executeSale(MinecraftClient client) {
        var player = client.player;
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        if (player == null || network == null) return;

        ItemStack target = player.getInventory().getStack(0);
        if (isTargetItem(target) && target.getCount() == sellAmount) {
            player.getInventory().selectedSlot = 0;
            network.sendChatCommand("ah sell " + currentPrice);
            currentBatch++;
            sendMessage("§7Listed: §b" + currentBatch + "/" + maxItems + " §8(" + sellAmount + "x)");
            state = 0; timer = 30 + random.nextInt(10);
        } else state = 0;
    }

    private void mergeSmallStacks(MinecraftClient client) {
        var player = client.player;
        var manager = client.interactionManager;
        if (player == null || manager == null) return;

        int first = -1, second = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isTargetItem(stack) && stack.getCount() < 64) {
                if (first == -1) first = i; else { second = i; break; }
            }
        }
        if (first != -1 && second != -1) {
            int s1 = first < 9 ? first + 36 : first;
            int s2 = second < 9 ? second + 36 : second;
            manager.clickSlot(player.currentScreenHandler.syncId, s1, 0, SlotActionType.PICKUP, player);
            manager.clickSlot(player.currentScreenHandler.syncId, s2, 0, SlotActionType.PICKUP, player);
            manager.clickSlot(player.currentScreenHandler.syncId, s1, 0, SlotActionType.PICKUP, player);
            timer = 8; state = 0;
        } else state = 20;
    }

    private void handleSearching(MinecraftClient client) {
        var manager = client.interactionManager;
        var player = client.player;
        if (player == null || manager == null) return;
        BlockPos pos = findNearbyContainer(client);
        if (pos != null) {
            currentContainerPos = pos;
            manager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5), Direction.UP, pos, false));
            state = 11; timer = 15;
        } else {
            sendMessage("§c§l[!] All storages are empty. Stopping.");
            tradingEnabled = false; state = 0;
        }
    }

    private BlockPos findNearbyContainer(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;
        BlockPos p = client.player.getBlockPos();
        for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            BlockPos pos = p.add(x, y, z);
            if (!ignoredBlocks.contains(pos) && (client.world.getBlockState(pos).isOf(Blocks.BARREL) || client.world.getBlockState(pos).isOf(Blocks.CHEST))) return pos;
        }
        return null;
    }

    private void handleStorage(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        if (client.currentScreen instanceof GenericContainerScreen container) {
            var manager = client.interactionManager;
            int syncId = container.getScreenHandler().syncId;
            if (state == 10) {
                manager.clickSlot(syncId, 46, 0, SlotActionType.PICKUP, client.player);
                state = 11; timer = 10;
            } else {
                int slot = findInMenu(container);
                if (slot != -1 && client.player.getInventory().getEmptySlot() != -1) {
                    manager.clickSlot(syncId, slot, 0, SlotActionType.QUICK_MOVE, client.player);
                    timer = 8;
                } else {
                    if (currentContainerPos != null) ignoredBlocks.add(currentContainerPos);
                    client.player.closeHandledScreen();
                    state = 0; timer = 10;
                }
            }
        } else if (timer == 0) state = 0;
    }

    private int findInMenu(GenericContainerScreen screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size() - 36; i++) {
            if (isTargetItem(screen.getScreenHandler().getSlot(i).getStack())) return i;
        }
        return -1;
    }

    private boolean isTargetItem(ItemStack s) {
        if (targetStack.isEmpty() || s.isEmpty() || s.getItem() != targetStack.getItem()) return false;
        var c1 = s.get(DataComponentTypes.POTION_CONTENTS);
        var c2 = targetStack.get(DataComponentTypes.POTION_CONTENTS);
        if (c1 != null && c2 != null) return Objects.equals(c1.potion(), c2.potion());
        return s.getName().getString().equals(targetStack.getName().getString());
    }

    private void handleDrinking(MinecraftClient client) {
        if (client.player == null) return;
        if (state == 50) { client.options.useKey.setPressed(true); drinkingTicks = 45; state = 51; }
        else {
            if (drinkingTicks > 0) { drinkingTicks--; timer = 1; }
            else { client.options.useKey.setPressed(false); dropEmptyBottles(client); state = 0; timer = 10; }
        }
    }

    private boolean shouldDrink(ClientPlayerEntity p) {
        StatusEffectInstance e = p.getStatusEffect(StatusEffects.INVISIBILITY);
        return e == null || e.getDuration() < 4800;
    }

    private void startDrinkingProcess(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        int potSlot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.isOf(Items.POTION)) {
                var c = s.get(DataComponentTypes.POTION_CONTENTS);
                if (c != null && (c.matches(Potions.LONG_INVISIBILITY) || c.matches(Potions.INVISIBILITY))) { potSlot = i; break; }
            }
        }
        if (potSlot != -1) {
            int syncId = client.player.currentScreenHandler.syncId;
            if (potSlot < 9) client.player.getInventory().selectedSlot = potSlot;
            else {
                client.interactionManager.clickSlot(syncId, potSlot, 8, SlotActionType.SWAP, client.player);
                client.player.getInventory().selectedSlot = 8;
            }
            state = 50; timer = 3;
        }
    }

    private void dropEmptyBottles(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        for (int i = 0; i < 36; i++) if (client.player.getInventory().getStack(i).isOf(Items.GLASS_BOTTLE))
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i < 9 ? i + 36 : i, 1, SlotActionType.THROW, client.player);
    }

    public static void setCurrentPrice(int p) { currentPrice = p; }
    public static int getCurrentPrice() { return currentPrice; }
    public static void setMaxItems(int c) { maxItems = c; }
    public static int getMaxItems() { return maxItems; }
    public static void setSellAmount(int a) { sellAmount = a; }
    public static int getSellAmount() { return sellAmount; }
    public static ItemStack getTargetStack() { return targetStack; }
    public static void setTargetStack(ItemStack s) { if (s != null && !s.isEmpty()) targetStack = s.copy(); }
    private static void sendMessage(String m) { if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(Text.literal(PREFIX + m), false); }
}