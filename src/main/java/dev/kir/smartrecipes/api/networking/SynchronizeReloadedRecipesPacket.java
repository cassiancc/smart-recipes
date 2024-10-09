package dev.kir.smartrecipes.api.networking;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import dev.kir.smartrecipes.SmartRecipes;
import dev.kir.smartrecipes.api.RecipeInfo;
import dev.kir.smartrecipes.api.ReloadableRecipeManager;
import dev.kir.smartrecipes.util.recipe.RecipeBookUtil;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SynchronizeReloadedRecipesPacket implements CustomPayload {
    private final Collection<Pair<ReloadableRecipeManager.RecipeState, RecipeInfo>> reloadedRecipes;
    public static final CustomPayload.Id<SynchronizeReloadedRecipesPacket> ID = new CustomPayload.Id<>(SmartRecipes.locate("packet.sync.recipes"));
    public static final PacketCodec<ByteBuf, SynchronizeReloadedRecipesPacket> CODEC = PacketCodecs.codec(Codec.pair(ReloadableRecipeManager.RecipeState.CODEC, RecipeInfo.CODEC)
            .listOf()
            .xmap(SynchronizeReloadedRecipesPacket::new, SynchronizeReloadedRecipesPacket::getReloadedRecipesPairs));

    private List<com.mojang.datafixers.util.Pair<ReloadableRecipeManager.RecipeState, RecipeInfo>> getReloadedRecipesPairs() {
        return reloadedRecipes.stream().map(pair -> new com.mojang.datafixers.util.Pair<>(pair.getLeft(), pair.getRight())).collect(Collectors.toCollection(ArrayList::new));
    }

    private RegistryWrapper.WrapperLookup registryLookup = null;


    public SynchronizeReloadedRecipesPacket(Collection<Pair<ReloadableRecipeManager.RecipeState, RecipeInfo>> reloadedRecipes, RegistryWrapper.WrapperLookup registryLookup) {
        this.reloadedRecipes = reloadedRecipes;
        this.registryLookup = registryLookup;
    }

    @ApiStatus.Internal // For the codec only
    public SynchronizeReloadedRecipesPacket(List<com.mojang.datafixers.util.Pair<ReloadableRecipeManager.RecipeState, RecipeInfo>> reloadedRecipes) {
        this(reloadedRecipes.stream().map(pair -> new Pair<>(pair.getFirst(), pair.getSecond())).collect(Collectors.toCollection(ArrayList::new)), BuiltinRegistries.createWrapperLookup());
    }

    @Override
    public Id<SynchronizeReloadedRecipesPacket> getId() {
        return ID;
    }

    @Environment(EnvType.CLIENT)
    public void execute(MinecraftClient client) {
        var handler = client.getNetworkHandler();
        RecipeManager recipeManager = handler.getRecipeManager();
        ((ReloadableRecipeManager)recipeManager).apply(this.reloadedRecipes);
        RecipeBook recipeBook = client.player == null ? null : client.player.getRecipeBook();
        if (recipeBook != null) {
            RecipeBookUtil.apply(recipeBook, handler.getRegistryManager(), this.reloadedRecipes);
        }
    }


    private static class SerializableRecipeInfo extends RecipeInfo {
        private final Recipe<?> recipe;
        private final RecipeType<?> recipeType;

        public SerializableRecipeInfo(Identifier recipeId, RecipeType<?> recipeType) {
            super(recipeId, null);
            this.recipe = null;
            this.recipeType = recipeType;
        }

        public SerializableRecipeInfo(Identifier recipeId, Recipe<?> recipe) {
            super(recipeId, null);
            this.recipe = recipe;
            this.recipeType = null;
        }

        @Override
        public Optional<RecipeEntry<?>> getRecipeEntry(RegistryWrapper.WrapperLookup lookup) {
            return this.getRecipe(lookup).map(recipe -> new RecipeEntry<>(this.getRecipeId(), recipe));
        }

        @Override
        public Optional<Recipe<?>> getRecipe(RegistryWrapper.WrapperLookup lookup) {
            return Optional.ofNullable(this.recipe);
        }

        @Override
        public Optional<RecipeType<?>> getRecipeType(RegistryWrapper.WrapperLookup lookup) {
            return this.recipeType == null ? this.getRecipe(lookup).map(Recipe::getType) : Optional.of(this.recipeType);
        }

        @Override
        public JsonObject getRecipeAsJson() {
            throw new IllegalStateException("JSON is not available on the client side");
        }
    }
}