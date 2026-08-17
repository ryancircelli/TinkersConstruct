package slimeknights.tconstruct.library.recipe.casting.container;

import lombok.AllArgsConstructor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

/**
 * Builder for a container filling recipe. Takes an arbitrary fluid for a specific amount to fill a Forge {@link net.neoforged.neoforge.fluids.capability.IFluidHandlerItem}
 */
@AllArgsConstructor(staticName = "castingRecipe")
@SuppressWarnings({"WeakerAccess", "unused"})
public class ContainerFillingRecipeBuilder extends AbstractRecipeBuilder<ContainerFillingRecipeBuilder> {
  private final ResourceLocation result;
  private final int fluidAmount;
  private final TypeAwareRecipeSerializer<? extends ContainerFillingRecipe> recipeSerializer;

  /**
   * Creates a new builder instance using the given result, amount, and serializer
   * @param result            Recipe result
   * @param fluidAmount       Container size
   * @param recipeSerializer  Serializer
   * @return  Builder instance
   */
  public static ContainerFillingRecipeBuilder castingRecipe(ItemLike result, int fluidAmount, TypeAwareRecipeSerializer<? extends ContainerFillingRecipe> recipeSerializer) {
    return new ContainerFillingRecipeBuilder(BuiltInRegistries.ITEM.getKey(result.asItem()), fluidAmount, recipeSerializer);
  }

  /**
   * Creates a new basin recipe builder using the given result, amount, and serializer
   * @param result            Recipe result
   * @param fluidAmount       Container size
   * @return  Builder instance
   */
  public static ContainerFillingRecipeBuilder basinRecipe(ResourceLocation result, int fluidAmount) {
    return castingRecipe(result, fluidAmount, TinkerSmeltery.basinFillingRecipeSerializer.get());
  }

  /**
   * Creates a new basin recipe builder using the given result, amount, and serializer
   * @param result            Recipe result
   * @param fluidAmount       Container size
   * @return  Builder instance
   */
  public static ContainerFillingRecipeBuilder basinRecipe(ItemLike result, int fluidAmount) {
    return castingRecipe(result, fluidAmount, TinkerSmeltery.basinFillingRecipeSerializer.get());
  }

  /**
   * Creates a new table recipe builder using the given result, amount, and serializer
   * @param result            Recipe result
   * @param fluidAmount       Container size
   * @return  Builder instance
   */
  public static ContainerFillingRecipeBuilder tableRecipe(ResourceLocation result, int fluidAmount) {
    return castingRecipe(result, fluidAmount, TinkerSmeltery.tableFillingRecipeSerializer.get());
  }

  /**
   * Creates a new table recipe builder using the given result, amount, and serializer
   * @param result            Recipe result
   * @param fluidAmount       Container size
   * @return  Builder instance
   */
  public static ContainerFillingRecipeBuilder tableRecipe(ItemLike result, int fluidAmount) {
    return castingRecipe(result, fluidAmount, TinkerSmeltery.tableFillingRecipeSerializer.get());
  }

  @Override
  public void save(RecipeOutput output) {
    this.save(output, this.result);
  }

  /**
   * {@inheritDoc}
   * @implNote  1.20 wrote the container's registry name straight into the JSON without resolving it, so a recipe could
   *            name an item belonging to a mod that is not installed. 1.21 serializes through the recipe's own codec,
   *            which resolves the item as it writes, so the item has to exist here (Mantle M8 section 9 hit the same
   *            wall for ingredients). A recipe for an absent mod's container has to be written outside the recipe
   *            pipeline now, the same answer M8 section 6.2 reached for a foreign recipe type.
   */
  @Override
  public void save(RecipeOutput output, ResourceLocation id) {
    Item container = BuiltInRegistries.ITEM.getOptional(this.result)
      .orElseThrow(() -> new IllegalStateException("Container filling recipe " + id + " names unknown item " + this.result));
    save(output, id, new ContainerFillingRecipe(recipeSerializer, group, fluidAmount, container), "casting");
  }
}
