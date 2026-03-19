package me.maxim.invisauc.logic;

import net.minecraft.item.ItemStack;

public class TradeSession {
    public ItemStack item;
    public double price;
    public int waitTime; // в секундах
    public double minPrice;
    public int currentTimer;

    public TradeSession(ItemStack item, double price, int waitTime, double minPrice) {
        this.item = item;
        this.price = price;
        this.waitTime = waitTime;
        this.minPrice = minPrice;
        this.currentTimer = waitTime;
    }

    public void tick() {
        if (currentTimer > 0) {
            currentTimer--;
        } else {
            // Знижуємо ціну на 5%, але не нижче мінімуму
            price = Math.max(minPrice, price * 0.95);
            currentTimer = waitTime; // Скидаємо таймер
        }
    }
}