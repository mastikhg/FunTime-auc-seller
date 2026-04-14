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

        FlowLayout window = Containers.verticalFlow(Sizing.fixed(200), Sizing.content());
        window.padding(Insets.of(12));
        window.surface(Surface.DARK_PANEL);
        window.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        // Title
        window.child(Components.label(Text.of("Bot Configuration")).shadow(true).margins(Insets.bottom(10)));

        // Item Showcase
        var itemShowcase = Components.item(InvisAuc.getTargetStack());
        window.child(itemShowcase.margins(Insets.bottom(8)));

        // Grab from hand button
        window.child(Components.button(Text.of("Set from Hand"), button -> {
            var client = MinecraftClient.getInstance();
            if (client.player != null && !client.player.getMainHandStack().isEmpty()) {
                InvisAuc.setTargetStack(client.player.getMainHandStack().copy());
                itemShowcase.stack(InvisAuc.getTargetStack());
                client.player.sendMessage(Text.literal("§b[IA] §aTarget item updated!"), false);
            }
        }).margins(Insets.bottom(12)).horizontalSizing(Sizing.fill(85)));

        // Price Field
        window.child(Components.label(Text.of("Sell Price:")));
        var priceField = Components.textBox(Sizing.fill(90));
        priceField.setText(String.valueOf(InvisAuc.getCurrentPrice()));
        priceField.onChanged().subscribe(val -> {
            try {
                if (val != null && !val.trim().isEmpty()) {
                    InvisAuc.setCurrentPrice(Integer.parseInt(val.trim()));
                }
            } catch (NumberFormatException ignored) {}
        });
        window.child(priceField.margins(Insets.bottom(10)));

        // Amount Field
        window.child(Components.label(Text.of("Amount (1 or 64):")));
        var amountField = Components.textBox(Sizing.fill(90));
        amountField.setText(String.valueOf(InvisAuc.getSellAmount()));
        amountField.onChanged().subscribe(val -> {
            try {
                if (val != null && !val.trim().isEmpty()) {
                    InvisAuc.setSellAmount(Integer.parseInt(val.trim()));
                }
            } catch (NumberFormatException ignored) {}
        });
        window.child(amountField.margins(Insets.bottom(10)));

        // AH Limit Field
        window.child(Components.label(Text.of("AH Slots Limit:")));
        var limitField = Components.textBox(Sizing.fill(90));
        limitField.setText(String.valueOf(InvisAuc.getMaxItems()));
        limitField.onChanged().subscribe(val -> {
            try {
                if (val != null && !val.trim().isEmpty()) {
                    InvisAuc.setMaxItems(Integer.parseInt(val.trim()));
                }
            } catch (NumberFormatException ignored) {}
        });
        window.child(limitField.margins(Insets.bottom(10)));

        rootComponent.child(window);
    }
}