package slimeknights.tconstruct.library.recipe.ingredient;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import slimeknights.mantle.data.loadable.common.IngredientLoadable;
import slimeknights.mantle.data.loadable.field.LoadableField;

import java.util.stream.Stream;

/**
 * Ingredient that contains another ingredient nested inside.
 * @apiNote  1.21 made {@link Ingredient} final; a custom ingredient implements {@link ICustomIngredient} and reaches an
 *           {@link Ingredient} through {@link #toVanilla()}. Forge's {@code AbstractIngredient} took most of this class
 *           with it: {@code getStackingIds} and {@code isEmpty} are derived by the vanilla wrapper from
 *           {@link #getItems()} and have no hook to override, and the {@code invalidate}/{@code checkInvalidation} pair
 *           is gone with no successor, as a custom ingredient's item list is resolved once by the wrapper and the
 *           wrapper lives exactly as long as the recipe that parsed it.
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class NestedIngredient implements ICustomIngredient {
  protected final Ingredient nested;

  /**
   * Field for the nested ingredient.
   * @apiNote  The nested ingredient always sits under {@code match} rather than being flattened into the parent as it
   *           was in 1.20. An ingredient dispatches on a {@code type} key in 1.21, and the parent object already
   *           carries one of its own, so a flattened vanilla ingredient would be read as an ingredient of the parent's
   *           own type. This is the same collision Mantle's fluid container ingredient hit (M8 section 4).
   */
  protected static <T extends NestedIngredient> LoadableField<Ingredient,T> nestedField() {
    return IngredientLoadable.DISALLOW_EMPTY.requiredField("match", i -> i.nested);
  }


  /* Defer to nested */

  @Override
  public boolean test(ItemStack stack) {
    return nested.test(stack);
  }

  @Override
  public Stream<ItemStack> getItems() {
    return Stream.of(nested.getItems());
  }

  @Override
  public boolean isSimple() {
    return nested.isSimple();
  }
}
