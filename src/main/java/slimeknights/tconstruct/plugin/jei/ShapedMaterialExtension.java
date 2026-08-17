package slimeknights.tconstruct.plugin.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.material.ShapedMaterialRecipe;
import slimeknights.tconstruct.plugin.jei.material.MaterialsCraftingExtension;
import slimeknights.tconstruct.plugin.jei.material.ShapedMaterialsExtension;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Logic to show {@link ShapedMaterialRecipe} in JEI
 * @apiNote  A singleton since JEI 19, which shares one extension instance across every recipe of a class and hands
 *           each method the {@link RecipeHolder} it applies to; see {@link MaterialsCraftingExtension}.
 * @deprecated use {@link ShapedMaterialsExtension}
 */
@Deprecated
public enum ShapedMaterialExtension implements ICraftingCategoryExtension<ShapedMaterialRecipe> {
  INSTANCE;

  @Override
  public int getWidth(RecipeHolder<ShapedMaterialRecipe> recipeHolder) {
    return recipeHolder.value().getWidth();
  }

  @Override
  public int getHeight(RecipeHolder<ShapedMaterialRecipe> recipeHolder) {
    return recipeHolder.value().getHeight();
  }

  @Override
  public void setRecipe(RecipeHolder<ShapedMaterialRecipe> recipeHolder, IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focusGroup) {
    ShapedMaterialRecipe recipe = recipeHolder.value();
    ItemStack plainResult = recipe.getResultItem(Objects.requireNonNull(SafeClientAccess.getRegistryAccess()));

    MaterialValueIngredient materials = recipe.getMaterial();
    List<ItemStack> result;
    if (materials != null) {
      result = MaterialRecipeCache.getAllRecipes().stream().filter(materials::test).flatMap(mat -> {
        ItemStack stack = plainResult.copy();
        recipe.setMaterial(stack, mat.getMaterial().getVariant());
        // add one copy of the stack per item in the nested ingredient, so the lengths match up
        return IntStream.range(0, mat.getIngredient().getItems().length).mapToObj(i -> stack);
      }).toList();
    } else {
      result = List.of(plainResult);
    }
    List<Ingredient> inputs = recipe.getIngredients();
    // 1.21: Ingredient is final and a custom ingredient rides inside it, so the instanceof moves onto getCustomIngredient
    int[] materialSlots = IntStream.range(0, inputs.size()).filter(i -> inputs.get(i).getCustomIngredient() instanceof MaterialValueIngredient).toArray();

    MaterialsCraftingExtension.setRecipe(getWidth(recipeHolder), getHeight(recipeHolder), builder, craftingGridHelper, recipeHolder, result, plainResult, materialSlots);
  }
}
