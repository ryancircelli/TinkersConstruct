package slimeknights.tconstruct.library.recipe.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient;
import slimeknights.tconstruct.tables.TinkerTables;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Shaped recipe with a number of {@link slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient} to set the material of the result.
 * @deprecated use {@link ShapedMaterialsRecipe}, which requires specifying the ingredients for each part.
 */
@Deprecated
public class ShapedMaterialRecipe extends ShapedRecipe {
  private MaterialValueIngredient material;
  private final List<MaterialVariantId> extraMaterials;

  public ShapedMaterialRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification, List<MaterialVariantId> extraMaterials) {
    super(group, category, pattern, result, showNotification);
    this.extraMaterials = extraMaterials;
  }

  public ShapedMaterialRecipe(ShapedRecipe recipe, List<MaterialVariantId> extraMaterials) {
    this(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.result, recipe.showNotification(), extraMaterials);
  }

  /** Gets the material to match */
  @Nullable
  public MaterialValueIngredient getMaterial() {
    if (material == null) {
      // assume all material ingredients match the same stat type
      for (Ingredient ingredient : getIngredients()) {
        // collect all ingredients that match
        if (ingredient.getCustomIngredient() instanceof MaterialValueIngredient materialValue) {
          if (material == null) {
            material = materialValue;
          } else {
            // ensure the stat type matches, and expand the range
            material = material.merge(materialValue);
          }
        }
      }
      // if we found no materials, that is also an issue
      // the recipe cannot name itself in 1.21; the result is the most identifying thing it can reach
      if (material == null) {
        TConstruct.LOG.error("No material ingredient found for material shaped recipe producing {}, this indicates a broken recipe", result);
      }
    }
    return material;
  }

  @Nullable
  private MaterialVariantId findMaterial(CraftingInput input) {
    MaterialValueIngredient material = getMaterial();
    if (material == null) {
      return null;
    }
    // ensure same material in all slots
    MaterialVariantId firstMaterial = null;
    for (int i = 0; i < input.size(); i++) {
      ItemStack stack = input.getItem(i);
      if (!stack.isEmpty()) {
        // ignore anything that does not meet our requirements
        MaterialVariantId matchedMaterial = material.getMaterial(stack);
        if (matchedMaterial != null) {
          // first match is set
          if (firstMaterial == null) {
            firstMaterial = matchedMaterial;
          } else if (!firstMaterial.matchesVariant(matchedMaterial)) {
            // if same material but different variants, just discard the variant
            if (firstMaterial.getId().equals(matchedMaterial.getId())) {
              firstMaterial = firstMaterial.getId();
            } else {
              // if different materials, no match
              return null;
            }
          }
        }
      }
    }
    return firstMaterial;
  }

  @Override
  public boolean matches(CraftingInput input, Level level) {
    if (!super.matches(input, level)) {
      return false;
    }

    // must have a material to match, no mixing
    return findMaterial(input) != null;
  }

  /** Sets the material for the given stack */
  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    ShapedMaterialsRecipe.setMaterial(stack, material, extraMaterials);
  }

  @Override
  public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
    ItemStack stack = super.assemble(input, registries);
    MaterialVariantId material = findMaterial(input);
    if (material != null) {
      setMaterial(stack, material);
    }
    return stack;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerTables.shapedMaterialRecipeSerializer.get();
  }

  public static class Serializer implements LoggingRecipeSerializer<ShapedMaterialRecipe> {
    static final Loadable<List<MaterialVariantId>> EXTRA_MATERIALS = ShapedMaterialsRecipe.Serializer.EXTRA_MATERIALS;
    static final LoadableField<List<MaterialVariantId>,ShapedMaterialRecipe> MATERIAL_FIELD = EXTRA_MATERIALS.defaultField("extra_materials", List.of(), r -> r.extraMaterials);
    private static final Codec<List<MaterialVariantId>> EXTRA_MATERIALS_CODEC = EXTRA_MATERIALS.codec();

    /**
     * Codec for this recipe.
     * @apiNote  Unlike {@link ShapedMaterialsRecipe}, this one never looks at the recipe's key, so the vanilla shaped
     *           recipe can be a plain field rather than needing the unpacked {@link ShapedRecipePattern.Data} form.
     */
    public static final MapCodec<ShapedMaterialRecipe> CODEC = RecordCodecBuilder.<ShapedMaterialRecipe>mapCodec(instance -> instance.group(
      RecipeSerializer.SHAPED_RECIPE.codec().forGetter(recipe -> recipe),
      EXTRA_MATERIALS_CODEC.optionalFieldOf("extra_materials", List.of()).forGetter(recipe -> recipe.extraMaterials)
    ).apply(instance, ShapedMaterialRecipe::new)).flatXmap(Serializer::validate, DataResult::success);

    public static final StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        RecipeSerializer.SHAPED_RECIPE.streamCodec().encode(buffer, recipe);
        MATERIAL_FIELD.encode(buffer, recipe);
      },
      buffer -> new ShapedMaterialRecipe(RecipeSerializer.SHAPED_RECIPE.streamCodec().decode(buffer), MATERIAL_FIELD.decode(buffer)));

    /** Ensures the material is valid; better to find out now than at runtime, as we have all the information needed */
    private static DataResult<ShapedMaterialRecipe> validate(ShapedMaterialRecipe recipe) {
      if (recipe.getMaterial() == null) {
        return DataResult.error(() -> "Invalid material ingredients for shaped material recipe");
      }
      return DataResult.success(recipe);
    }

    @Override
    public MapCodec<ShapedMaterialRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialRecipe> streamCodecSafe() {
      return STREAM_CODEC;
    }
  }
}
