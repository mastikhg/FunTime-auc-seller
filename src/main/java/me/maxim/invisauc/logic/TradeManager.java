package me.maxim.invisauc.logic;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public class TradeManager {
    // Список усіх активних сесій
    private static final List<TradeSession> activeSessions = new ArrayList<>();

    public static void startTrade(ServerPlayerEntity player, double price, int interval) {
        // Мінімальна ціна, наприклад, 10% від початкової
        double minPrice = price * 0.1;
        TradeSession session = new TradeSession(player.getMainHandStack().copy(), price, interval, minPrice);
        activeSessions.add(session);

        player.sendMessage(Text.of("§aТоргівлю розпочато! Початкова ціна: " + price), false);
    }

    public static void tickAll() {
        for (TradeSession session : activeSessions) {
            double oldPrice = session.price;
            session.tick();

            // Якщо ціна змінилася, можеш вивести повідомлення в чат (опціонально)
            if (oldPrice != session.price) {
                // Тут можна додати розсилку всім гравцям про знижку
            }
        }
    }
}