package dev.kir.smartrecipes.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import org.jetbrains.annotations.Contract;

import java.util.Optional;

public class RecipeInfo {
    public static final Codec<RecipeInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("recipeId").forGetter(RecipeInfo::getRecipeId),
            Codecs.JSON_ELEMENT.xmap(JsonElement::getAsJsonObject, (object) -> object).fieldOf("recipeObject").forGetter(RecipeInfo::getRecipeAsJson)
    ).apply(instance, RecipeInfo::new));
    private final Identifier recipeId;
    private final JsonObject recipeObject;
    private RecipeEntry<?> recipeEntry;
    private RecipeType<?> recipeType;

    public RecipeInfo(Identifier recipeId, JsonObject recipeObject) {
        this.recipeId = recipeId;
        this.recipeObject = recipeObject;
    }

    public Identifier getRecipeId() {
        return this.recipeId;
    }

    public JsonObject getRecipeAsJson() {
        return this.recipeObject;
    }

    public Optional<RecipeType<?>> getRecipeType(RegistryWrapper.WrapperLookup lookup) {
        if (this.recipeEntry != null) {
            return Optional.of(this.recipeEntry.value().getType());
        }

        if (this.recipeType == null && this.recipeObject != null) {
            String type = this.recipeObject.get("type") instanceof JsonPrimitive typePrimitive && typePrimitive.isString() ? typePrimitive.getAsString() : null;
            Identifier id = type == null ? null : Identifier.tryParse(type);
            if (id != null) {
                this.recipeType = Registries.RECIPE_TYPE.getOrEmpty(id).or(() -> Registries.RECIPE_TYPE.getOrEmpty(Identifier.of(id.getNamespace(), id.getPath().split("_")[0]))).orElse(null);
            }
        }
        return Optional.ofNullable(this.recipeType);
    }

    public Optional<RecipeEntry<?>> getRecipeEntry(RegistryWrapper.WrapperLookup lookup) {
        if (this.recipeEntry == null && this.recipeId != null && this.recipeObject != null) {
            try {
                this.recipeEntry = RecipeManager.deserialize(this.recipeId, this.recipeObject, lookup);
            } catch (Throwable e) {
                this.recipeEntry = null;
            }
        }
        return Optional.ofNullable(this.recipeEntry);
    }

    public Optional<Recipe<?>> getRecipe(RegistryWrapper.WrapperLookup lookup) {
        return this.getRecipeEntry(lookup).map(RecipeEntry::value);
    }

    @Contract(value = "_, _ -> new", pure = true)
    public RecipeInfo with(Identifier recipeId, JsonObject recipeObject) {
        return new RecipeInfo(recipeId, recipeObject);
    }
}