package slimeknights.tconstruct.tools.recipe;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerModifiers;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/** Recipe for transforming tipped arrows into a tool */
public class TippedToolTransformRecipe extends ToolBuildingRecipe {
  /** Loader instance */
  public static final RecordLoadable<TippedToolTransformRecipe> LOADER = RecordLoadable.create(
    LoadableRecipeSerializer.RECIPE_GROUP, RESULT_FIELD, LAYOUT_FIELD,
    IngredientLoadable.DISALLOW_EMPTY.requiredField("input", r -> r.ingredients.get(0)),
    MaterialVariantId.LOADABLE.list(0).defaultField("materials", List.of(), false, r -> r.materials),
    ModifierId.PARSER.requiredField("modifier", r -> r.modifier),
    TippedToolTransformRecipe::new);

  protected final ModifierId modifier;
  public TippedToolTransformRecipe(String group, IModifiable output, @Nullable ResourceLocation layoutSlot, Ingredient ingredient, List<MaterialVariantId> materials, ModifierId modifier) {
    super(group, output, 1, layoutSlot, List.of(ingredient), List.of(), materials);
    this.modifier = modifier;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerModifiers.tippedToolTransformRecipeSerializer.get();
  }

  @Override
  public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, HolderLookup.Provider access) {
    RecipeResult<LazyToolStack> result = super.getValidatedResult(inv, access);
    if (result.isSuccess()) {
      // tool must have modifier, else we are adding bad data
      IToolStackView tool = result.getResult().getTool();
      if (tool.getModifierLevel(modifier) > 0) {
        // find the potion, should be first non-empty stack
        ItemStack stack = ItemStack.EMPTY;
        for (int i = 0; i < inv.getInputCount(); i++) {
          stack = inv.getInput(i);
          if (!stack.isEmpty()) {
            break;
          }
        }
        // if we found one, copy its potion into the result tool
        if (!stack.isEmpty()) {
          ResourceLocation potion = getPotion(stack);
          if (potion != null) {
            tool.getPersistentData().putString(modifier, potion.toString());
          }
        }
      }
    }
    return result;
  }

  /**
   * Gets the ID of the potion on the given stack, or null if it has none.
   * @apiNote  1.21 deleted the {@code Potion} string tag along with the rest of item NBT; a potion is the
   *           {@code minecraft:potion_contents} component holding a {@code Holder<Potion>}. The tool's persistent data
   *           still stores the potion ID as a string, so the ID is unwrapped back out of the holder here.
   */
  @Nullable
  private static ResourceLocation getPotion(ItemStack stack) {
    PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
    if (contents != null) {
      return contents.potion().flatMap(Holder::unwrapKey).map(ResourceKey::location).orElse(null);
    }
    return null;
  }

  @Override
  public List<ItemStack> getDisplayOutput() {
    if (displayOutput == null) {
      ItemStack result = super.getDisplayOutput().get(0);
      displayOutput = Arrays.stream(ingredients.get(0).getItems())
        .map(stack -> {
          ResourceLocation potion = getPotion(stack);
          if (potion != null) {
            ItemStack copy = result.copy();
            ToolStack.mutable(copy).getPersistentData().putString(modifier, potion.toString());
            return copy;
          }
          return result;
        }).toList();
    }
    return displayOutput;
  }
}
