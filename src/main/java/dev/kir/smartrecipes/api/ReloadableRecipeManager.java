package dev.kir.smartrecipes.api;

import com.mojang.serialization.Codec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.StringIdentifiable;

import java.util.Collection;

public interface ReloadableRecipeManager {
    void reload(MinecraftServer server, Identifier cause);

    void apply(Collection<Pair<RecipeState, RecipeInfo>> diff);

    enum RecipeState implements StringIdentifiable {
        KEEP("keep"),
        REMOVE("remove");

        @SuppressWarnings("deprecation") public static final Codec<RecipeState> CODEC = new EnumCodec<>(values(), RecipeState::valueOf);
        private final String id;
        @Override
        public String asString() {
            return id;
        }

        RecipeState(String id) {
            this.id = id;
        }
    }
}