package slimeknights.tconstruct.library.recipe.casting;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;
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
}
