package slimeknights.tconstruct.plugin.jei.material;

import com.google.common.collect.Streams;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import slimeknights.mantle.Mantle;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.plugin.jei.MantleJEIConstants;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.recipe.material.MaterialsCraftingTableRecipe;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Common logic for {@link ShapedMaterialsExtension} and {@link ShapelessMaterialsExtension}
 * @apiNote  JEI 19 registers one extension instance per recipe <i>class</i> rather than a factory per recipe, and every
 *           method takes the {@link RecipeHolder} it applies to (Mantle M12 SS5). This class is therefore a stateless
 *           singleton: what the 1.20 constructor computed once per recipe - the display outputs and the input slots
 *           carrying the material - is recomputed in {@link #setRecipe} instead. The factories that returned
 *           {@code null} for a recipe with an empty part are {@link #isHandled(RecipeHolder)} now, which is the API's
 *           own way to say the same thing.
 */
public class MaterialsCraftingExtension<T extends CraftingRecipe & MaterialsCraftingTableRecipe> implements ICraftingCategoryExtension<T> {
  /** The display stacks and focus-link slots for one recipe, recomputed per call. */
  private record Display(ItemStack plainResult, List<ItemStack> result, @Nullable int[] materialSlots) {}

  protected MaterialsCraftingExtension() {}

  /** Builds the display for the given recipe */
  private Display display(T recipe) {
    ItemStack plainResult = recipe.getResultItem(Objects.requireNonNull(SafeClientAccess.getRegistryAccess()));

    // if we have just the one part, set the output to match its material
    if (recipe.getPartCount() == 1) {
      Ingredient firstPart = recipe.getParts().get(0);
      List<ItemStack> result = Arrays.stream(firstPart.getItems()).map(variant -> {
        ItemStack stack = plainResult.copy();
        if (variant.getItem() instanceof IMaterialItem materialItem) {
          recipe.setMaterial(stack, materialItem.getMaterial(variant));
        } else {
          recipe.setMaterial(stack, MaterialRecipeCache.findRecipe(variant).getMaterial().getVariant());
        }
        return stack;
      }).toList();
      return new Display(plainResult, result, getMaterialSlots(recipe, firstPart));
      // otherwise, use a display material. allow display tool part if it has just 1 material
    } else if (recipe.getExtraMaterials().isEmpty() && plainResult.getItem() instanceof IMaterialItem materialItem) {
      return new Display(plainResult, List.of(materialItem.setMaterialForced(plainResult, ToolBuildHandler.getRenderMaterial(0))), null);
    } else {
      // display tool
      return new Display(plainResult, List.of(IModifiableDisplay.getDisplayStack(plainResult)), null);
    }
  }

  /** Gets the material slots for the given recipe */
  protected int[] getMaterialSlots(T recipe, Ingredient firstPart) {
    return new int[] {0};
  }

  @Override
  public boolean isHandled(RecipeHolder<T> recipeHolder) {
    T recipe = recipeHolder.value();
    List<Ingredient> parts = recipe.getIngredients();
    for (int i = 0; i < recipe.getPartCount(); i++) {
      if (parts.get(i).getItems().length == 0) {
        return false;
      }
    }
    return true;
  }

  /** Sets the recipe in the builder. Width and height come from the extension, which is the only thing that knows them. */
  public static void setRecipe(int width, int height, IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper,
                               RecipeHolder<? extends CraftingRecipe> recipeHolder, List<ItemStack> result, ItemStack plainResult, @Nullable int[] materialSlots) {
    builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(plainResult);

    // apply ingredient stacks
    List<List<ItemStack>> inputStacks = recipeHolder.value().getIngredients().stream().map(ingredient -> List.of(ingredient.getItems())).toList();
    // shapeless needs its width and height set, but we also want to recover those sizes, so calculate it locally
    if (width <= 0 || height <= 0) {
      width = height = getShapelessSize(inputStacks.size());
      builder.setShapeless();
    }
    List<IRecipeSlotBuilder> inputs = craftingGridHelper.createAndSetInputs(builder, inputStacks, width, height);
    IRecipeSlotBuilder output = craftingGridHelper.createAndSetOutputs(builder, result);
    if (inputs.size() != 9) {
      Mantle.logger.error("Failed to create focus link for {} as the layout {} is not 3x3", recipeHolder.id(), builder.getClass().getName());
    } else if (materialSlots != null) {
      // apply focus links
      int finalWidth = width;
      int finalHeight = height;
      builder.createFocusLink(Streams.concat(
        Stream.<IIngredientAcceptor<?>>of(output),
        Arrays.stream(materialSlots).mapToObj(i -> inputs.get(MantleJEIConstants.getCraftingIndex(i, finalWidth, finalHeight)))
      ).toArray(IIngredientAcceptor<?>[]::new));
    }
  }

  @Override
  public void setRecipe(RecipeHolder<T> recipeHolder, IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
    Display display = display(recipeHolder.value());
    setRecipe(getWidth(recipeHolder), getHeight(recipeHolder), builder, craftingGridHelper, recipeHolder, display.result(), display.plainResult(), display.materialSlots());
  }

  /** Gets the width and height of the grid for a shapeless recipe. */
  private static int getShapelessSize(int total) {
    if (total > 4) {
      return 3;
    } else if (total > 1) {
      return 2;
    } else {
      return 1;
    }
  }
}
