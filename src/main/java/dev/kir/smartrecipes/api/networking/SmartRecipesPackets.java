package dev.kir.smartrecipes.api.networking;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.Objects;

public class SmartRecipesPackets {
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        PayloadTypeRegistry.playC2S().register(SynchronizeReloadedRecipesPacket.ID, SynchronizeReloadedRecipesPacket.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(SynchronizeReloadedRecipesPacket.ID, (payload, context) -> payload.execute(context.client()));

    }
}