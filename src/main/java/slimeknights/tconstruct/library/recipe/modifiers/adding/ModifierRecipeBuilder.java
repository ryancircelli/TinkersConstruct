package slimeknights.tconstruct.library.recipe.modifiers.adding;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.util.LazyModifier;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ModifierRecipeBuilder extends AbstractModifierRecipeBuilder<ModifierRecipeBuilder> {
  protected final List<SizedIngredient> inputs = new ArrayList<>();
  protected ModifierRecipeBuilder(ModifierId result) {
    super(result);
  }

  /**
   * Creates a new recipe for 1 level of a modifier
   * @param modifier  Modifier
   * @return  Recipe for 1 level of the modifier
   */
  public static ModifierRecipeBuilder modifier(ModifierId modifier) {
    return new ModifierRecipeBuilder(modifier);
  }

  /**
   * Creates a new recipe for 1 level of a modifier
   * @param modifier  Modifier
   * @return  Recipe for 1 level of the modifier
   */
  public static ModifierRecipeBuilder modifier(LazyModifier modifier) {
    return modifier(modifier.getId());
  }


  /* Inputs */

  /**
   * Adds an input to the recipe
   * @param ingredient  Input
   * @return  Builder instance
   */
  public ModifierRecipeBuilder addInput(SizedIngredient ingredient) {
    this.inputs.add(ingredient);
    return this;
  }

  /**
   * Adds an input to the recipe
   * @param ingredient  Input
   * @return  Builder instance
   */
  public ModifierRecipeBuilder addInput(Ingredient ingredient) {
    return addInput(new SizedIngredient(ingredient, 1));
  }

  /**
   * Adds an input with the given amount, does not affect the salvage builder
   * @param item    Item
   * @param amount  Amount
   * @return  Builder instance
   */
  public ModifierRecipeBuilder addInput(ItemLike item, int amount) {
    return addInput(SizedIngredient.of(item, amount));
  }

  /**
   * Adds an input with a size of 1, does not affect the salvage builder
   * @param item    Item
   * @return  Builder instance
   */
  public ModifierRecipeBuilder addInput(ItemLike item) {
    return addInput(item, 1);
  }

  /**
   * Adds an input to the recipe
   * @param tag     Tag input
   * @param amount  Amount required
   * @return  Builder instance
   */
  public ModifierRecipeBuilder addInput(TagKey<Item> tag, int amount) {
    return addInput(SizedIngredient.of(tag, amount));
  }

  /**
   * Adds an input to the recipe
   * @param tag     Tag input
   * @return  Builder instance
   */
  public ModifierRecipeBuilder addInput(TagKey<Item> tag) {
    return addInput(tag, 1);
  }


  /* Building */

  @Override
  public void save(RecipeOutput output, ResourceLocation id) {
    if (inputs.isEmpty() && !allowCrystal) {
      throw new IllegalStateException("Must have at least 1 input");
    }
    save(output, id, new ModifierRecipe(inputs, tools, maxToolSize, result, ModifierEntry.VALID_LEVEL.range(minLevel, maxLevel), slots, allowCrystal, checkTraitLevel), "modifiers");
  }
}
