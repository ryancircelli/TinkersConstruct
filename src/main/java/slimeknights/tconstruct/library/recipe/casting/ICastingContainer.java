package slimeknights.tconstruct.library.recipe.casting;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import slimeknights.mantle.recipe.container.ISingleStackContainer;

/**
 * Inventory containing a single item and a fluid
 */
public interface ICastingContainer extends ISingleStackContainer {
  /**
   * Gets the contained fluid in this inventory
   * @return  Contained fluid
   */
  Fluid getFluid();

  /**
   * Gets the data components of the contained fluid.
   * @return  Fluid's components
   * @apiNote  Successor to {@code getFluidTag}. A 1.21 {@link net.neoforged.neoforge.fluids.FluidStack} stores a
   *           {@link DataComponentPatch} rather than a {@code CompoundTag}, and the recipes reading this copy it
   *           straight onto the item they produce, which stores one too.
   */
  default DataComponentPatch getFluidComponents() {
    return DataComponentPatch.EMPTY;
  }

  /**
   * {@inheritDoc}
   * @apiNote A casting recipe's real input is the fluid; the item is an optional cast, and a basin recipe usually
   * has none. {@code RecipeManager#getRecipeFor} returns empty without testing a single recipe when
   * {@link net.minecraft.world.item.crafting.RecipeInput#isEmpty()} is true, and the interface's default
   * implementation of that only looks at item slots - so every castless casting recipe became unfindable, while
   * calling {@code matches} on the same recipe and container still returned true. 1.20's {@code getRecipeFor} took
   * a {@code Container} and had no such short circuit.
   */
  @Override
  default boolean isEmpty() {
    return getFluid() == Fluids.EMPTY && getStack().isEmpty();
  }
}
