package slimeknights.tconstruct.plugin.jei.material;

import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.tconstruct.library.recipe.material.ShapelessMaterialsRecipe;

/**
 * Logic to show {@link ShapelessMaterialsRecipe} in JEI.
 * @apiNote  A singleton since JEI 19, which shares one extension instance across every recipe of a class; see
 *           {@link MaterialsCraftingExtension}. It is also what the plugin registers now, where 1.20 registered the
 *           base class through a {@code MaterialsCraftingExtension::shapeless} factory - the two did the same thing,
 *           and the factory only existed to return null for a recipe with an empty part, which {@code isHandled} says
 *           directly now.
 */
public class ShapelessMaterialsExtension extends MaterialsCraftingExtension<ShapelessMaterialsRecipe> {
  public static final ShapelessMaterialsExtension INSTANCE = new ShapelessMaterialsExtension();

  private ShapelessMaterialsExtension() {}

  @Override
  protected int[] getMaterialSlots(ShapelessMaterialsRecipe recipe, Ingredient firstPart) {
    return new int[] {0};
  }
}
