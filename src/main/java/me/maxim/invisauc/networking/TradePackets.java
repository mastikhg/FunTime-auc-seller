package me.maxim.invisauc.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class TradePackets {

    public static void registerServer() {
        // 1. Реєструємо тип пакету (це обов'язково для 1.21.1)
        PayloadTypeRegistry.playC2S().register(TradePayload.ID, TradePayload.CODEC);

        // 2. Реєструємо обробник на сервері
        ServerPlayNetworking.registerGlobalReceiver(TradePayload.ID, (payload, context) -> {
            double price = payload.price();
            int time = payload.waitTime();

            // Використовуємо context.server().execute для безпечного виконання коду в основному потоці
            context.server().execute(() -> {
                context.player().sendMessage(net.minecraft.text.Text.of("Продаж активовано: " + price + " за " + time + "с"), false);
                // Тут додавай логіку створення торгової сесії
            });
        });
    }
}