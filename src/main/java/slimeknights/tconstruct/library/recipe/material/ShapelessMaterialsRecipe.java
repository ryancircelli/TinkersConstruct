package slimeknights.tconstruct.library.recipe.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.List;

/**
 * Shapeless recipe with a number of {@link slimeknights.tconstruct.library.recipe.ingredient.MaterialIngredient} and
 * {@link slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient} to set the materials of the result.
 */
public class ShapelessMaterialsRecipe extends ShapelessRecipe implements MaterialsCraftingTableRecipe {
  /** Number of parts to match */
  @Getter
  private final int partCount;
  /** List of additional materials to add beyond the parts */
  @Getter
  private final List<MaterialVariantId> extraMaterials;

  public ShapelessMaterialsRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients, int partCount, List<MaterialVariantId> extraMaterials) {
    super(group, category, result, ingredients);
    this.partCount = partCount;
    this.extraMaterials = extraMaterials;
  }

  public ShapelessMaterialsRecipe(ShapelessRecipe recipe, int partCount, List<MaterialVariantId> extraMaterials) {
    this(recipe.getGroup(), recipe.category(), recipe.result, recipe.getIngredients(), partCount, extraMaterials);
  }

  @Override
  public List<Ingredient> getParts() {
    return getIngredients();
  }

  /** Sets the material for the given stack */
  @Override
  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    ShapedMaterialsRecipe.setMaterial(stack, material, extraMaterials);
  }

  @Override
  public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
    return ShapedMaterialsRecipe.assemble(super.assemble(input, registries), input, getIngredients(), partCount, false, extraMaterials);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerTables.shapelessMaterialsRecipeSerializer.get();
  }

  public static class Serializer implements LoggingRecipeSerializer<ShapelessMaterialsRecipe> {
    static final Loadable<List<MaterialVariantId>> EXTRA_MATERIALS = ShapedMaterialsRecipe.Serializer.EXTRA_MATERIALS;
    static final LoadableField<List<MaterialVariantId>,ShapelessMaterialsRecipe> MATERIAL_FIELD = EXTRA_MATERIALS.defaultField("extra_materials", List.of(), r -> r.extraMaterials);
    private static final Codec<List<MaterialVariantId>> EXTRA_MATERIALS_CODEC = EXTRA_MATERIALS.codec();

    /**
     * Codec for this recipe.
     * @apiNote  The vanilla shapeless recipe is a whole field of this one rather than a copied set of fields, as 1.21
     *           lets a {@link MapCodec} stand in for a group entry and nothing here needs to see inside it.
     */
    public static final MapCodec<ShapelessMaterialsRecipe> CODEC = RecordCodecBuilder.<ShapelessMaterialsRecipe>mapCodec(instance -> instance.group(
      RecipeSerializer.SHAPELESS_RECIPE.codec().forGetter(recipe -> recipe),
      ExtraCodecs.POSITIVE_INT.fieldOf("parts").forGetter(ShapelessMaterialsRecipe::getPartCount),
      EXTRA_MATERIALS_CODEC.optionalFieldOf("extra_materials", List.of()).forGetter(ShapelessMaterialsRecipe::getExtraMaterials)
    ).apply(instance, ShapelessMaterialsRecipe::new)).flatXmap(Serializer::validate, DataResult::success);

    public static final StreamCodec<RegistryFriendlyByteBuf,ShapelessMaterialsRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        RecipeSerializer.SHAPELESS_RECIPE.streamCodec().encode(buffer, recipe);
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.partCount);
        MATERIAL_FIELD.encode(buffer, recipe);
      },
      buffer -> {
        ShapelessRecipe base = RecipeSerializer.SHAPELESS_RECIPE.streamCodec().decode(buffer);
        return new ShapelessMaterialsRecipe(base, ByteBufCodecs.VAR_INT.decode(buffer), MATERIAL_FIELD.decode(buffer));
      });

    /** Ensures the part count fits within the ingredient list */
    private static DataResult<ShapelessMaterialsRecipe> validate(ShapelessMaterialsRecipe recipe) {
      int ingredients = recipe.getIngredients().size();
      if (recipe.partCount > ingredients) {
        return DataResult.error(() -> "Parts must be between 1 and the number of ingredients " + ingredients);
      }
      return DataResult.success(recipe);
    }

    @Override
    public MapCodec<ShapelessMaterialsRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf,ShapelessMaterialsRecipe> streamCodecSafe() {
      return STREAM_CODEC;
    }
  }
}
