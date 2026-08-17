package slimeknights.tconstruct.smeltery.item;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import slimeknights.tconstruct.smeltery.block.entity.component.TankBlockEntity;

import javax.annotation.Nonnull;

/**
 * Handler that works with a tank item to adjust the fluid component on it.
 * <p>
 * 1.20 also implemented {@code ICapabilityProvider} and returned itself; the capability is granted per item from
 * {@code RegisterCapabilitiesEvent} now, so this is just the handler (M7 §5).
 */
@RequiredArgsConstructor
public class TankItemFluidHandler implements IFluidHandlerItem {
  private final TankItem tankItem;
  @Getter
  private final ItemStack container;

  /** Gets the tank on the stack */
  private ScaledFluidTank getTank() {
    // TODO: can we directly use the nested tank as our fluid handler instead of doing this wrapper?
    // might be more efficient, though it may require validating the stack size/NBT did not change externally
    return tankItem.getTank(container);
  }

  /** Updates the container from the given tank */
  private void updateContainer(ScaledFluidTank tank) {
    TankItem.setTank(container, tank.getStoredFluid());
  }

  @Override
  public int getTanks() {
    return 1;
  }

  @Nonnull
  @Override
  public FluidStack getFluidInTank(int tank) {
    return getTank().getFluidInTank(tank);
  }

  @Override
  public int getTankCapacity(int tank) {
    return TankBlockEntity.getCapacity(container.getItem()) * container.getCount();
  }

  @Override
  public boolean isFluidValid(int tank, FluidStack stack) {
    return true;
  }

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    ScaledFluidTank tank = getTank();
    int didFill = tank.fill(resource, action);
    if (didFill > 0 && action.execute()) {
      updateContainer(tank);
    }
    return didFill;
  }

  @Nonnull
  @Override
  public FluidStack drain(FluidStack resource, FluidAction action) {
    ScaledFluidTank tank = getTank();
    FluidStack didDrain = tank.drain(resource, action);
    if (!didDrain.isEmpty() && action.execute()) {
      updateContainer(tank);
    }
    return didDrain;
  }

  @Nonnull
  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    ScaledFluidTank tank = getTank();
    FluidStack didDrain = tank.drain(maxDrain, action);
    if (!didDrain.isEmpty() && action.execute()) {
      updateContainer(tank);
    }
    return didDrain;
  }
}
