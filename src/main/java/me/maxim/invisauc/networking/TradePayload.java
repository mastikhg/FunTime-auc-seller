package me.maxim.invisauc.networking;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TradePayload(double price, int waitTime) implements CustomPayload {
    public static final Id<TradePayload> ID = new Id<>(Identifier.of("invisauc", "start_trade"));

    // Виправлений кодек: використовуємо PacketCodecs.DOUBLE та VAR_INT з правильним кастом
    public static final PacketCodec<RegistryByteBuf, TradePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, TradePayload::price,
            PacketCodecs.VAR_INT, TradePayload::waitTime,
            TradePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}