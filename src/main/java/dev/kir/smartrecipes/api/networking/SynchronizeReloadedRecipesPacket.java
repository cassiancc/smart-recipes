package dev.kir.smartrecipes.api.networking;

import com.google.gson.JsonObject;
import dev.kir.smartrecipes.SmartRecipes;
import dev.kir.smartrecipes.api.RecipeInfo;
import dev.kir.smartrecipes.api.ReloadableRecipeManager;
import dev.kir.smartrecipes.util.recipe.RecipeBookUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

public class SynchronizeReloadedRecipesPacket {
    public static final Identifier ID = SmartRecipes.locate("packet.sync.recipes");

    private final Collection<Pair<ReloadableRecipeManager.RecipeState, RecipeInfo>> reloadedRecipes;

    public SynchronizeReloadedRecipesPacket(Collection<Pair<ReloadableRecipeManager.RecipeState, RecipeInfo>> reloadedRecipes) {
        this.reloadedRecipes = reloadedRecipes;
    }

    public SynchronizeReloadedRecipesPacket(PacketByteBuf buf) {
        this.reloadedRecipes = buf.readList(SynchronizeReloadedRecipesPacket::readRecipeEntry);
    }

    public void write(PacketByteBuf buf) {
        buf.writeCollection(this.reloadedRecipes, SynchronizeReloadedRecipesPacket::writeRecipeEntry);
    }

    @Environment(EnvType.CLIENT)
    public void apply(MinecraftClient client, ClientPlayNetworkHandler handler) {
        RecipeManager recipeManager = handler.getRecipeManager();
        ((ReloadableRecipeManager)recipeManager).apply(this.reloadedRecipes);
        RecipeBook recipeBook = client.player == null ? null : client.player.getRecipeBook();
        if (recipeBook != null) {
            RecipeBookUtil.apply(recipeBook, handler.getRegistryManager(), this.reloadedRecipes);
        }
    }

    public void send(Stream<ServerPlayerEntity> players) {
        PacketByteBuf buffer = PacketByteBufs.create();
        this.write(buffer);
        players.forEach(player -> ServerPlayNetworking.send(player, ID, buffer));
    }

    @SuppressWarnings("unchecked")
    private static <T extends RecipeEntry<U>, U extends Recipe<?>> void writeRecipeEntry(PacketByteBuf buf, Pair<ReloadableRecipeManager.RecipeState, RecipeInfo> recipeEntry, RegistryWrapper.WrapperLookup lookup) {
        ReloadableRecipeManager.RecipeState state = recipeEntry.getLeft();
        RecipeInfo recipeInfo = recipeEntry.getRight();
        if (state == ReloadableRecipeManager.RecipeState.KEEP) {
            T recipe = (T)recipeInfo.getRecipeEntry(lookup).orElseThrow(() -> new IllegalArgumentException("Unable to parse recipe '" + recipeInfo.getRecipeId() + "'"));

            buf.writeBoolean(true);
            buf.writeIdentifier(Registries.RECIPE_SERIALIZER.getId(recipe.value().getSerializer()));
            buf.writeIdentifier(recipe.id());
            ((RecipeSerializer<U>)recipe.value().getSerializer()).write(buf, recipe.value());
        } else {
            buf.writeBoolean(false);
            buf.writeIdentifier(recipeInfo.getRecipeId());
            buf.writeIdentifier(Registries.RECIPE_TYPE.getId(recipeInfo.getRecipeType(lookup).orElseThrow(() -> new IllegalArgumentException("Recipe '" + recipeInfo.getRecipeId() + "' uses invalid or unsupported recipe type"))));
        }
    }

    private static Pair<ReloadableRecipeManager.RecipeState, RecipeInfo> readRecipeEntry(PacketByteBuf buf) {
        if (buf.readBoolean()) {
            Identifier serializerId = buf.readIdentifier();
            RecipeSerializer<?> serializer = Registries.RECIPE_SERIALIZER.getOrEmpty(serializerId).orElseThrow(() -> new IllegalArgumentException("Unknown recipe serializer " + serializerId));
            Identifier recipeId = buf.readIdentifier();
            Recipe<?> recipe = serializer.read(buf);
            return new Pair<>(ReloadableRecipeManager.RecipeState.KEEP, new SerializableRecipeInfo(recipeId, recipe));
        } else {
            Identifier recipeId = buf.readIdentifier();
            Identifier recipeTypeId = buf.readIdentifier();
            RecipeType<?> recipeType = Registries.RECIPE_TYPE.getOrEmpty(recipeTypeId).orElseThrow(() -> new IllegalArgumentException("Invalid or unsupported recipe type '" + recipeTypeId + "'"));
            return new Pair<>(ReloadableRecipeManager.RecipeState.REMOVE, new SerializableRecipeInfo(recipeId, recipeType));
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