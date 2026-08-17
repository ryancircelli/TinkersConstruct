package slimeknights.tconstruct.library.recipe.ingredient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicate;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicateField;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Ingredient matching material items with the given value. Typically, matches ingots or blocks.
 * @apiNote  {@link #of(IJsonPredicate, float, float)} returns an {@link Ingredient} rather than this class, as 1.21's
 *           {@link Ingredient} is final and every caller of it wants one; {@link Ingredient#getCustomIngredient()}
 *           hands the ingredient itself back where the shaped material recipes need it.
 */
@Getter
@RequiredArgsConstructor
public class MaterialValueIngredient implements ICustomIngredient {
  /** Loadable for this ingredient */
  public static final RecordLoadable<MaterialValueIngredient> LOADABLE = Loader.INSTANCE;

  private final IJsonPredicate<MaterialVariantId> material;
  private final float minValue;
  private final float maxValue;

  /** Creates an ingredient matching a range of values */
  public static Ingredient of(IJsonPredicate<MaterialVariantId> materials, float minValue, float maxValue) {
    return new MaterialValueIngredient(materials, minValue, maxValue).toVanilla();
  }

  /** Creates an ingredient matching an exact value */
  public static Ingredient of(IJsonPredicate<MaterialVariantId> materials, float value) {
    return of(materials, value, value);
  }

  /** Checks the given material recipe against our filters */
  public boolean test(MaterialRecipe material) {
    float value = material.getValue() / (float) material.getNeeded();
    return minValue <= value && value <= maxValue && this.material.matches(material.getMaterial().getVariant());
  }

  @Override
  public boolean test(ItemStack stack) {
    MaterialRecipe recipe = MaterialRecipeCache.findRecipe(stack);
    return recipe != MaterialRecipe.EMPTY && test(recipe);
  }

  @Override
  public Stream<ItemStack> getItems() {
    return MaterialRecipeCache.getAllRecipes().stream()
                              .filter(this::test)
                              .flatMap(material -> Arrays.stream(material.getIngredient().getItems()));
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public IngredientType<?> getType() {
    return TinkerIngredients.MATERIAL_VALUE.get();
  }


  /* Helpers for ShapedMaterialRecipe */

  /** Checks if this ingredient fully contains the range of the other */
  private boolean contains(MaterialValueIngredient other) {
    return this.minValue <= other.minValue && other.maxValue <= this.maxValue;
  }

  /** Creates an ingredient that matches anything either of the two ingredients matches */
  public MaterialValueIngredient merge(MaterialValueIngredient other) {
    if (this == other) return this;

    // if we have the same predicate, we can possibly skip creating a new instance
    IJsonPredicate<MaterialVariantId> predicate = this.material;
    if (this.material.equals(other.material)) {
      if (this.contains(other)) {
        return this;
      }
      if (other.contains(this)) {
        return other;
      }
    } else {
      predicate = MaterialPredicate.or(this.material, other.material);
    }
    return new MaterialValueIngredient(predicate, Math.min(this.minValue, other.minValue), Math.max(this.maxValue, other.maxValue));
  }

  /** Gets the material matching this recipe */
  @Nullable
  public MaterialVariantId getMaterial(ItemStack stack) {
    MaterialRecipe recipe = MaterialRecipeCache.findRecipe(stack);
    return recipe != MaterialRecipe.EMPTY && test(recipe) ? recipe.getMaterial().getVariant() : null;
  }


  /**
   * Loadable instance, written by hand as the value range is one key holding either a number or a min/max pair.
   */
  private enum Loader implements RecordLoadable<MaterialValueIngredient> {
    INSTANCE;

    private static final LoadableField<IJsonPredicate<MaterialVariantId>,MaterialValueIngredient> MATERIAL_FIELD = new MaterialPredicateField<>("material", i -> i.material);

    @Override
    public MaterialValueIngredient deserialize(JsonObject json, TypedMap context) {
      float minValue, maxValue;
      JsonElement value = json.get("value");
      if (value.isJsonPrimitive()) {
        minValue = maxValue = value.getAsJsonPrimitive().getAsFloat();
      } else {
        JsonObject object = GsonHelper.convertToJsonObject(value, "value");
        minValue = GsonHelper.getAsFloat(object, "min", 0);
        maxValue = GsonHelper.getAsFloat(object, "max", Float.POSITIVE_INFINITY);
      }
      return new MaterialValueIngredient(MATERIAL_FIELD.get(json, context), minValue, maxValue);
    }

    @Override
    public void serialize(MaterialValueIngredient ingredient, JsonObject json) {
      MATERIAL_FIELD.serialize(ingredient, json);
      if (ingredient.minValue == ingredient.maxValue) {
        json.addProperty("value", ingredient.minValue);
      } else {
        JsonObject value = new JsonObject();
        if (ingredient.minValue > 0) {
          value.addProperty("min", ingredient.minValue);
        }
        if (Float.isFinite(ingredient.maxValue)) {
          value.addProperty("max", ingredient.maxValue);
        }
        json.add("value", value);
      }
    }

    @Override
    public MaterialValueIngredient decode(RegistryFriendlyByteBuf buffer, TypedMap context) {
      return new MaterialValueIngredient(MATERIAL_FIELD.decode(buffer, context), buffer.readFloat(), buffer.readFloat());
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer, MaterialValueIngredient ingredient) {
      MATERIAL_FIELD.encode(buffer, ingredient);
      buffer.writeFloat(ingredient.minValue);
      buffer.writeFloat(ingredient.maxValue);
    }
  }
}
