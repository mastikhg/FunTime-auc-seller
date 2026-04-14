package me.maxim.invisauc;

import me.maxim.invisauc.gui.TradeConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
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

public class InvisAuc implements ClientModInitializer {
    private static KeyBinding startKey, guiKey, invisibilityKey;
    private static boolean tradingEnabled = false;
    private static boolean autoInvisibilityEnabled = false;

    private static int timer = 0, currentBatch = 0, state = 0;
    private static int drinkingTicks = 0;
    private static int watchdogTimer = 0;
    private static final Random random = new Random();

    private static int checkTimer = 0;
    private static final String PREFIX = "§8[§bIA§8]§r ";
    private static final String ANARCHY_COMMAND = "an223";

    private static int currentPrice = 39000;
    private static int maxItems = 6;
    private static int sellAmount = 1;
    private static ItemStack targetStack = ItemStack.EMPTY;
    private static final Set<BlockPos> ignoredBlocks = new HashSet<>();
    private static BlockPos currentContainerPos = null;

    @Override
    public void onInitializeClient() {
        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "InvisAuc"));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "InvisAuc"));
        invisibilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.invisauc.invisibility", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "InvisAuc"));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString().toLowerCase();
            if (text.contains("restart") || text.contains("reboot") || text.contains("lobby")) {
                new Thread(() -> {
                    try {
                        Thread.sleep(8000);
                        var client = MinecraftClient.getInstance();
                        if (client.getNetworkHandler() != null) {
                            client.getNetworkHandler().sendChatCommand(ANARCHY_COMMAND);
                        }
                    } catch (Exception ignored) {}
                }).start();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null || client.world == null) return;

        if (tradingEnabled && state != 0) {
            watchdogTimer++;
            if (watchdogTimer > 140) {
                sendMessage("§eResetting state");
                resetTrading();
                client.player.closeHandledScreen();
                return;
            }
        } else {
            watchdogTimer = 0;
        }

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

        boolean isAllowedToDrink = (client.currentScreen == null || client.currentScreen instanceof GameMenuScreen);

        if (autoInvisibilityEnabled && state < 50 && isAllowedToDrink) {
            if (checkTimer <= 0) {
                if (shouldDrink(client.player)) { startDrinkingProcess(client); return; }
                checkTimer = 60;
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

    private void safeClick(MinecraftClient client, int slot, int button, SlotActionType actionType) {
        if (client.interactionManager != null && client.player != null && client.player.currentScreenHandler != null) {
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, button, actionType, client.player);
        }
    }

    private void processInventory(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        if (currentBatch >= maxItems) {
            currentBatch = 0;
            client.getNetworkHandler().sendChatCommand("ah");
            state = 10; timer = 15;
            return;
        }

        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (isTargetItem(stack) && stack.getCount() >= sellAmount) { slot = i; break; }
        }

        if (slot != -1) {
            int serverSlot = slot < 9 ? slot + 36 : slot;
            safeClick(client, serverSlot, 0, sellAmount == 64 ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP);
            state = sellAmount == 64 ? 3 : 1;
            timer = 4;
        } else state = 4;
    }

    private void sliceStack(MinecraftClient client) {
        if (client.player == null || client.player.currentScreenHandler == null) return;

        ItemStack inSlot = client.player.getInventory().getStack(0);
        if (inSlot.getCount() == sellAmount) { state = 2; timer = 3; return; }

        ItemStack inCursor = client.player.currentScreenHandler.getCursorStack();
        if ((sellAmount > 32 && inSlot.isEmpty() && inCursor.getCount() >= 32) || !inCursor.isEmpty()) {
            safeClick(client, 36, 1, SlotActionType.PICKUP);
            timer = inCursor.isEmpty() ? 2 : 1;
        } else state = 0;
    }

    private void returnItems(MinecraftClient client) {
        if (client.player == null || client.player.currentScreenHandler == null) return;
        if (!client.player.currentScreenHandler.getCursorStack().isEmpty()) {
            int empty = client.player.getInventory().getEmptySlot();
            if (empty != -1) {
                safeClick(client, empty < 9 ? empty + 36 : empty, 0, SlotActionType.PICKUP);
                timer = 4;
            }
        }
        state = 3;
    }

    private void executeSale(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        ItemStack target = client.player.getInventory().getStack(0);
        if (isTargetItem(target) && target.getCount() == sellAmount) {
            client.player.getInventory().selectedSlot = 0;
            client.getNetworkHandler().sendChatCommand("ah sell " + currentPrice);
            currentBatch++;
            sendMessage("§7Sale: §b" + currentBatch + "/" + maxItems);
            state = 0; timer = 25 + random.nextInt(10);
        } else state = 0;
    }

    private void mergeSmallStacks(MinecraftClient client) {
        if (client.player == null) return;
        int f = -1, s = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (isTargetItem(stack) && stack.getCount() < 64) {
                if (f == -1) f = i; else { s = i; break; }
            }
        }
        if (f != -1 && s != -1) {
            int s1 = f < 9 ? f + 36 : f;
            int s2 = s < 9 ? s + 36 : s;
            safeClick(client, s1, 0, SlotActionType.PICKUP);
            safeClick(client, s2, 0, SlotActionType.PICKUP);
            safeClick(client, s1, 0, SlotActionType.PICKUP);
            timer = 10; state = 0;
        } else state = 20;
    }

    private void handleSearching(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        BlockPos pos = findNearbyContainer(client);
        if (pos != null) {
            currentContainerPos = pos;
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5), Direction.UP, pos, false));
            state = 11; timer = 12;
        } else {
            sendMessage("§cStorage is empty.");
            tradingEnabled = false; state = 0;
        }
    }

    private BlockPos findNearbyContainer(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
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
            if (state == 10) {
                safeClick(client, 46, 0, SlotActionType.PICKUP);
                state = 11; timer = 12;
            } else {
                int slot = -1;
                for (int i = 0; i < container.getScreenHandler().slots.size() - 36; i++) {
                    if (isTargetItem(container.getScreenHandler().getSlot(i).getStack())) { slot = i; break; }
                }
                if (slot != -1 && client.player.getInventory().getEmptySlot() != -1) {
                    safeClick(client, slot, 0, SlotActionType.QUICK_MOVE);
                    timer = 6;
                } else {
                    if (currentContainerPos != null) ignoredBlocks.add(currentContainerPos);
                    client.player.closeHandledScreen();
                    state = 0; timer = 10;
                }
            }
        } else if (timer == 0) state = 0;
    }

    private boolean isTargetItem(ItemStack s) {
        if (targetStack.isEmpty() || s.isEmpty() || s.getItem() != targetStack.getItem()) return false;
        PotionContentsComponent c1 = s.get(DataComponentTypes.POTION_CONTENTS);
        PotionContentsComponent c2 = targetStack.get(DataComponentTypes.POTION_CONTENTS);
        if (c1 != null && c2 != null) return Objects.equals(c1.potion(), c2.potion());
        return s.getName().getString().equals(targetStack.getName().getString());
    }

    private void startDrinkingProcess(MinecraftClient client) {
        if (client.player == null) return;
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
            else {
                safeClick(client, potSlot, 8, SlotActionType.SWAP);
                client.player.getInventory().selectedSlot = 8;
            }
            state = 50; timer = 5;
        }
    }

    private void handleDrinking(MinecraftClient client) {
        if (client.player == null) return;
        if (state == 50) {
            client.options.useKey.setPressed(true);
            drinkingTicks = 42; state = 51;
        } else {
            if (drinkingTicks > 0) { drinkingTicks--; timer = 1; }
            else {
                client.options.useKey.setPressed(false);
                dropEmptyBottles(client);
                state = 0; timer = 8;
            }
        }
    }

    private void dropEmptyBottles(MinecraftClient client) {
        if (client.player == null) return;
        for (int i = 0; i < 36; i++) {
            if (client.player.getInventory().getStack(i).isOf(Items.GLASS_BOTTLE))
                safeClick(client, i < 9 ? i + 36 : i, 1, SlotActionType.THROW);
        }
    }

    private boolean shouldDrink(ClientPlayerEntity p) {
        StatusEffectInstance e = p.getStatusEffect(StatusEffects.INVISIBILITY);
        return e == null || e.getDuration() < 1200;
    }

    private void resetTrading() {
        state = 0;
        timer = 0;
        currentBatch = 0;
        watchdogTimer = 0;
        ignoredBlocks.clear();
    }

    private static void sendMessage(String m) {
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().player.sendMessage(Text.literal(PREFIX + m), false);
    }

    public static void setCurrentPrice(int p) { currentPrice = p; }
    public static int getCurrentPrice() { return currentPrice; }
    public static void setMaxItems(int c) { maxItems = c; }
    public static int getMaxItems() { return maxItems; }
    public static void setSellAmount(int a) { sellAmount = a; }
    public static int getSellAmount() { return sellAmount; }
    public static ItemStack getTargetStack() { return targetStack; }
    public static void setTargetStack(ItemStack s) { if (s != null && !s.isEmpty()) targetStack = s.copy(); }
}