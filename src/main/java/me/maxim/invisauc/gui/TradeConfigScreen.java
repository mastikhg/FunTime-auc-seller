package me.maxim.invisauc.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import me.maxim.invisauc.InvisAuc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class TradeConfigScreen extends BaseOwoScreen<FlowLayout> {

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout window = Containers.verticalFlow(Sizing.fixed(220), Sizing.content());
        window.padding(Insets.of(10));
        window.surface(Surface.DARK_PANEL);
        window.alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        window.child(Components.label(Text.of("Bot Configuration")).shadow(true).margins(Insets.bottom(8)));

        FlowLayout tabToggleArea = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        tabToggleArea.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        tabToggleArea.margins(Insets.bottom(8));

        var tradeTabBtn = Components.button(Text.of("Trade"), b -> {});
        var systemTabBtn = Components.button(Text.of("System"), b -> {});

        tabToggleArea.child(tradeTabBtn.horizontalSizing(Sizing.fixed(95)).margins(Insets.horizontal(2)));
        tabToggleArea.child(systemTabBtn.horizontalSizing(Sizing.fixed(95)).margins(Insets.horizontal(2)));
        window.child(tabToggleArea);

        FlowLayout tradeTabContent = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        tradeTabContent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        var itemShowcase = Components.item(InvisAuc.getTargetStack());
        tradeTabContent.child(itemShowcase.margins(Insets.bottom(6)));

        tradeTabContent.child(Components.button(Text.of("Set from Hand"), button -> {
            var client = MinecraftClient.getInstance();
            if (client.player != null && !client.player.getMainHandStack().isEmpty()) {
                InvisAuc.setTradingEnabled(false);
                InvisAuc.setTargetStack(client.player.getMainHandStack().copy());
                itemShowcase.stack(InvisAuc.getTargetStack());
                client.player.sendMessage(Text.literal("§b[IA] §aTarget updated."), false);
            }
        }).margins(Insets.bottom(8)).horizontalSizing(Sizing.fill(85)));

        tradeTabContent.child(Components.label(Text.of("Sell Price:")));
        var priceField = Components.textBox(Sizing.fill(90));
        priceField.setText(String.valueOf(InvisAuc.getCurrentPrice()));
        priceField.onChanged().subscribe(val -> {
            try { if (val != null && !val.trim().isEmpty()) InvisAuc.setCurrentPrice(Integer.parseInt(val.trim())); } catch (NumberFormatException ignored) {}
        });
        tradeTabContent.child(priceField.margins(Insets.bottom(6)));

        tradeTabContent.child(Components.label(Text.of("Max Buy Price:")));
        var buyPriceField = Components.textBox(Sizing.fill(90));
        buyPriceField.setText(String.valueOf(InvisAuc.getMaxBuyPrice()));
        buyPriceField.onChanged().subscribe(val -> {
            try { if (val != null && !val.trim().isEmpty()) InvisAuc.setMaxBuyPrice(Long.parseLong(val.trim())); } catch (NumberFormatException ignored) {}
        });
        tradeTabContent.child(buyPriceField.margins(Insets.bottom(6)));

        tradeTabContent.child(Components.label(Text.of("Amount (1 or 64):")));
        var amountField = Components.textBox(Sizing.fill(90));
        amountField.setText(String.valueOf(InvisAuc.getSellAmount()));
        amountField.onChanged().subscribe(val -> {
            try { if (val != null && !val.trim().isEmpty()) InvisAuc.setSellAmount(Integer.parseInt(val.trim())); } catch (NumberFormatException ignored) {}
        });
        tradeTabContent.child(amountField.margins(Insets.bottom(6)));

        tradeTabContent.child(Components.label(Text.of("AH Slots Limit:")));
        var limitField = Components.textBox(Sizing.fill(90));
        limitField.setText(String.valueOf(InvisAuc.getMaxItems()));
        limitField.onChanged().subscribe(val -> {
            try { if (val != null && !val.trim().isEmpty()) InvisAuc.setMaxItems(Integer.parseInt(val.trim())); } catch (NumberFormatException ignored) {}
        });
        tradeTabContent.child(limitField.margins(Insets.bottom(6)));

        FlowLayout systemTabContent = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        systemTabContent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        systemTabContent.child(Components.label(Text.literal("§3─── Night Mode (Sleep) ───")).margins(Insets.vertical(4)));

        systemTabContent.child(Components.label(Text.of("Start Time (HH:MM):")));
        FlowLayout startTimerRow = Containers.horizontalFlow(Sizing.fill(90), Sizing.content());
        startTimerRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        var startHourField = Components.textBox(Sizing.fixed(40));
        startHourField.setText(String.valueOf(InvisAuc.getNightModeStartHour()));
        startHourField.onChanged().subscribe(val -> {
            try { if (val != null && !val.trim().isEmpty()) InvisAuc.setNightModeStartHour(Integer.parseInt(val.trim())); } catch (NumberFormatException ignored) {}
        });

        var startMinField = Components.textBox(Sizing.fixed(40));
        startMinField.setText(String.valueOf(InvisAuc.getNightModeStartMinute()));
        startMinField.onChanged().subscribe(val -> {
            try { if (val != null && !val.trim().isEmpty()) InvisAuc.setNightModeStartMinute(Integer.parseInt(val.trim())); } catch (NumberFormatException ignored) {}
        });

        startTimerRow.child(startHourField);
        startTimerRow.child(Components.label(Text.of(" : ")).margins(Insets.horizontal(4)));
        startTimerRow.child(startMinField);
        systemTabContent.child(startTimerRow.margins(Insets.bottom(6)));

        systemTabContent.child(Components.label(Text.of("Wake Up Hour (HH:00):")));
        var endHourField = Components.textBox(Sizing.fixed(40));
        endHourField.setText(String.valueOf(InvisAuc.getNightModeEndHour()));
        endHourField.onChanged().subscribe(val -> {
            try { if (val != null && !val.trim().isEmpty()) InvisAuc.setNightModeEndHour(Integer.parseInt(val.trim())); } catch (NumberFormatException ignored) {}
        });
        systemTabContent.child(endHourField.margins(Insets.bottom(8)));

        systemTabContent.child(Components.label(Text.literal("§6─── Auto Pay 95% (Key U) ───")).margins(Insets.vertical(4)));
        systemTabContent.child(Components.label(Text.of("Pay Target (Nickname):")));
        var payTargetField = Components.textBox(Sizing.fill(90));
        payTargetField.setText(InvisAuc.getPayTarget());
        payTargetField.onChanged().subscribe(val -> {
            if (val != null) InvisAuc.setPayTarget(val.trim());
        });
        systemTabContent.child(payTargetField.margins(Insets.bottom(6)));


        window.child(tradeTabContent);
        tradeTabBtn.active(false);

        tradeTabBtn.onPress(b -> {
            if (!window.children().contains(tradeTabContent)) {
                window.removeChild(systemTabContent);
                window.child(tradeTabContent);
                tradeTabBtn.active(false);
                systemTabBtn.active(true);
            }
        });

        systemTabBtn.onPress(b -> {
            if (!window.children().contains(systemTabContent)) {
                window.removeChild(tradeTabContent);
                window.child(systemTabContent);
                tradeTabBtn.active(true);
                systemTabBtn.active(false);
            }
        });

        rootComponent.child(window);
    }
}