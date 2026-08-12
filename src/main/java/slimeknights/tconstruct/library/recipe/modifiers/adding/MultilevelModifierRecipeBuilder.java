package slimeknights.tconstruct.library.recipe.modifiers.adding;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import java.util.ArrayList;
import java.util.List;

/**
 * Modifier recipe that changes max level and slot behavior each level. Used for a single input recipe that has multiple slot requirements
 */
public class MultilevelModifierRecipeBuilder extends AbstractMultilevelModifierRecipeBuilder<MultilevelModifierRecipeBuilder> {
  // inputs
  private final List<SizedIngredient> inputs = new ArrayList<>();

  protected MultilevelModifierRecipeBuilder(ModifierId result) {
    super(result);
  }

  /** Creates a new builder instance */
  public static MultilevelModifierRecipeBuilder modifier(ModifierId result) {
    return new MultilevelModifierRecipeBuilder(result);
  }


  /* Inputs */

  /**
   * Adds an input to the recipe
   * @param ingredient  Input
   * @return  Builder instance
   */
  public MultilevelModifierRecipeBuilder addInput(SizedIngredient ingredient) {
    this.inputs.add(ingredient);
    return this;
  }

  /**
   * Adds an input to the recipe
   * @param ingredient  Input
   * @return  Builder instance
   */
  public MultilevelModifierRecipeBuilder addInput(Ingredient ingredient) {
    return addInput(new SizedIngredient(ingredient, 1));
  }

  /**
   * Adds an input with the given amount, does not affect the salvage builder
   * @param item    Item
   * @param amount  Amount
   * @return  Builder instance
   */
  public MultilevelModifierRecipeBuilder addInput(ItemLike item, int amount) {
    return addInput(SizedIngredient.of(item, amount));
  }

  /**
   * Adds an input with a size of 1, does not affect the salvage builder
   * @param item    Item
   * @return  Builder instance
   */
  public MultilevelModifierRecipeBuilder addInput(ItemLike item) {
    return addInput(item, 1);
  }

  /**
   * Adds an input to the recipe
   * @param tag     Tag input
   * @param amount  Amount required
   * @return  Builder instance
   */
  public MultilevelModifierRecipeBuilder addInput(TagKey<Item> tag, int amount) {
    return addInput(SizedIngredient.of(tag, amount));
  }

  /**
   * Adds an input to the recipe
   * @param tag     Tag input
   * @return  Builder instance
   */
  public MultilevelModifierRecipeBuilder addInput(TagKey<Item> tag) {
    return addInput(tag, 1);
  }


  /* Saving */

  @Override
  public void save(RecipeOutput output, ResourceLocation id) {
    if (inputs.isEmpty() && !allowCrystal) {
      throw new IllegalStateException("Must either have at least 1 input or allow crystal");
    }
    if (levels.isEmpty()) {
      throw new IllegalStateException("Must have at least 1 level");
    }
    save(output, id, new MultilevelModifierRecipe(inputs, tools, maxToolSize, result, allowCrystal, levels, checkTraitLevel), "modifiers");
  }
}
