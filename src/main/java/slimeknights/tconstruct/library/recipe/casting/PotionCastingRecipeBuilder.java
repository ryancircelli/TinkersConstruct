package slimeknights.tconstruct.library.recipe.casting;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import javax.annotation.Nullable;

/**
 * Builder for a potion bottle filling recipe. Takes a fluid and optional cast to create an item that copies the fluid NBT
 */
@SuppressWarnings({"WeakerAccess", "unused", "UnusedReturnValue"})
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PotionCastingRecipeBuilder extends AbstractRecipeBuilder<PotionCastingRecipeBuilder> {
  private final Item result;
  @Nullable
  private final ModifierId modifier;
  private final TypeAwareRecipeSerializer<? extends PotionCastingRecipe> recipeSerializer;
  /**
   * Builds the recipe for a modifier variant, null when {@link #modifier} is null.
   * <p>
   * The tipping and tip-clearing recipes share this builder and every field of it, and differ only in which class is
   * constructed. 1.20 did not have to say which: it handed the loader to {@code LoadableFinishedRecipe} beside the
   * recipe, so the pair was chosen at the call site. 1.21's {@link RecipeOutput} takes only the recipe and serializes
   * it through its own serializer's loader, so building a {@link TippingCastingRecipe} for a tip-clearing serializer
   * throws {@code ClassCastException} during datagen instead of writing the wrong file.
   */
  @Nullable
  private final PotionCastingRecipeFactory factory;
  private Ingredient bottle = Ingredient.EMPTY;
  private FluidIngredient fluid = FluidIngredient.EMPTY;
  @Setter @Accessors(chain = true)
  private int coolingTime = 5;

  /* Bottle filling */

  /** Creates a new casting recipe for a bottle */
  public static PotionCastingRecipeBuilder castingRecipe(ItemLike result, TypeAwareRecipeSerializer<PotionCastingRecipe> serializer) {
    return new PotionCastingRecipeBuilder(result.asItem(), null, serializer, null);
  }

  /**
   * Creates a new casting basin recipe
   * @param result  Recipe result
   * @return  Builder instance
   */
  public static PotionCastingRecipeBuilder basinRecipe(ItemLike result) {
    return castingRecipe(result, TinkerSmeltery.basinPotionRecipeSerializer.get());
  }

  /**
   * Creates a new casting table recipe
   * @param result  Recipe result
   * @return  Builder instance
   */
  public static PotionCastingRecipeBuilder tableRecipe(ItemLike result) {
    return castingRecipe(result, TinkerSmeltery.tablePotionRecipeSerializer.get());
  }


  /* Modifier casting */

  /** Creates a new casting recipe for a bottle */
  public static PotionCastingRecipeBuilder tippingRecipe(ModifierId modifier, TypeAwareRecipeSerializer<? extends PotionCastingRecipe> serializer, PotionCastingRecipeFactory factory) {
    return new PotionCastingRecipeBuilder(Items.AIR, modifier, serializer, factory);
  }

  /** @deprecated use {@link #tippingRecipe(ModifierId, TypeAwareRecipeSerializer, PotionCastingRecipeFactory)}, which names the recipe class the serializer expects */
  @Deprecated(forRemoval = true)
  public static PotionCastingRecipeBuilder tippingRecipe(ModifierId modifier, TypeAwareRecipeSerializer<? extends PotionCastingRecipe> serializer) {
    return tippingRecipe(modifier, serializer, TippingCastingRecipe::new);
  }

  /**
   * Creates a new tool potion casting basin recipe
   * @param modifier  Modifier required to cast
   * @return  Builder instance
   */
  public static PotionCastingRecipeBuilder basinTipping(ModifierId modifier) {
    return tippingRecipe(modifier, TinkerSmeltery.basinTippingRecipeSerializer.get(), TippingCastingRecipe::new);
  }

  /**
   * Creates a new tool potion casting table recipe
   * @param modifier  Recipe result
   * @return  Builder instance
   */
  public static PotionCastingRecipeBuilder tableTipping(ModifierId modifier) {
    return tippingRecipe(modifier, TinkerSmeltery.tableTippingRecipeSerializer.get(), TippingCastingRecipe::new);
  }

  /**
   * Creates a new tool potion casting basin recipe
   * @param modifier  Modifier required to cast
   * @return  Builder instance
   */
  public static PotionCastingRecipeBuilder basinClearing(ModifierId modifier) {
    return tippingRecipe(modifier, TinkerSmeltery.basinTipClearingRecipeSerializer.get(), TipClearingCastingRecipe::new);
  }

  /**
   * Creates a new tool potion casting table recipe
   * @param modifier  Recipe result
   * @return  Builder instance
   */
  public static PotionCastingRecipeBuilder tableClearing(ModifierId modifier) {
    return tippingRecipe(modifier, TinkerSmeltery.tableTipClearingRecipeSerializer.get(), TipClearingCastingRecipe::new);
  }


  /* Fluids */

  /**
   * Sets the fluid for this recipe
   * @param tagIn   Tag<Fluid> instance
   * @param amount  amount of fluid
   * @return  Builder instance
   */
  public PotionCastingRecipeBuilder setFluid(TagKey<Fluid> tagIn, int amount) {
    return this.setFluid(FluidIngredient.of(tagIn, amount));
  }

  /**
   * Sets the fluid ingredient
   * @param fluid  Fluid ingredient instance
   * @return  Builder instance
   */
  public PotionCastingRecipeBuilder setFluid(FluidIngredient fluid) {
    this.fluid = fluid;
    return this;
  }


  /* Cast */

  /**
   * Sets the cast from a tag, bottles are always consumed
   * @param tagIn     Cast tag
   * @return  Builder instance
   */
  public PotionCastingRecipeBuilder setBottle(TagKey<Item> tagIn) {
    return this.setBottle(Ingredient.of(tagIn));
  }

  /**
   * Sets the bottle from an item, bottles are always consumed
   * @param itemIn    Cast item
   * @return  Builder instance
   */
  public PotionCastingRecipeBuilder setBottle(ItemLike itemIn) {
    return this.setBottle(Ingredient.of(itemIn));
  }

  /**
   * Sets the bottle from an ingredient, bottles are always consumed
   * @param ingredient  Cast ingredient
   * @return  Builder instance
   */
  public PotionCastingRecipeBuilder setBottle(Ingredient ingredient) {
    this.bottle = ingredient;
    return this;
  }

  /**
   * Builds a recipe using the registry name as the recipe name
   * @param output  Recipe output
   */
  @Override
  public void save(RecipeOutput output) {
    this.save(output, BuiltInRegistries.ITEM.getKey(this.result));
  }

  @Override
  public void save(RecipeOutput output, ResourceLocation id) {
    if (this.fluid == FluidIngredient.EMPTY) {
      throw new IllegalStateException("Casting recipes require a fluid input");
    }
    if (this.coolingTime < 0) {
      throw new IllegalStateException("Cooling time is too low, must be at least 0");
    }
    if (modifier != null) {
      save(output, id, factory.create(recipeSerializer, group, bottle, fluid, coolingTime, modifier), "casting");
    } else {
      save(output, id, new PotionCastingRecipe(recipeSerializer, group, bottle, fluid, result, coolingTime), "casting");
    }
  }

  /** Builds the recipe object for a modifier variant of this builder */
  @FunctionalInterface
  public interface PotionCastingRecipeFactory {
    PotionCastingRecipe create(TypeAwareRecipeSerializer<?> serializer, String group, Ingredient tool, FluidIngredient fluid, int coolingTime, ModifierId modifier);
  }
}
