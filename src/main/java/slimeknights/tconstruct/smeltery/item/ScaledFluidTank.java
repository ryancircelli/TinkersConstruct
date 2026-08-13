package slimeknights.tconstruct.smeltery.item;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;

/**
 * Fluid tank representing a stack of multiple fluid tanks. All operations must affect every tack in the stack at the same time, so must in increments of the scale.
 * Internally works the same as a fluid tank with {@code capacity * scale}, except operations are truncated to the nearest scale (e.g. if scale is 4, we must fill in 4mb increments).
 */
public class ScaledFluidTank extends FluidTank {
  private final int scale;
  private ScaledFluidTank(int capacity, int scale) {
    super(capacity * scale);
    this.scale = scale;
  }

  /**
   * Creates a new instance.
   * 1.20 returned a plain {@link FluidTank} for a scale of 1 to skip the modulo work; the scaling now lives in
   * {@link #setStoredFluid}/{@link #getStoredFluid} rather than in NBT methods a plain tank also has, so every caller
   * needs this type and a scale of 1 is simply a no-op through the same code.
   */
  public static ScaledFluidTank create(int capacity, int scale) {
    return new ScaledFluidTank(capacity, scale);
  }

  /* Helpers */

  /** enforces the amount matches the scale */
  private int enforceScale(int amount) {
    // no working with fluids of partial amounts
    int remainder = amount % scale;
    if (remainder != 0) {
      amount -= remainder;
    }
    return amount;
  }

  /** enforces the fluid matches the scale */
  private FluidStack enforceScale(FluidStack stack, boolean copy) {
    // no working with fluids of partial amounts
    int remainder = stack.getAmount() % scale;
    if (remainder != 0) {
      if (copy) {
        stack = stack.copy();
      }
      stack.shrink(remainder);
    }
    return stack;
  }


  /* Fluid tank methods */

  @Override
  public FluidTank setCapacity(int capacity) {
    return super.setCapacity(enforceScale(capacity));
  }

  @Override
  public void setFluid(FluidStack stack) {
    super.setFluid(enforceScale(stack, false));
  }

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    return super.fill(enforceScale(resource, true), action);
  }

  @Nonnull
  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    return super.drain(enforceScale(maxDrain), action);
  }

  @Nonnull
  @Override
  public FluidStack drain(FluidStack resource, FluidAction action) {
    return super.drain(enforceScale(resource, true), action);
  }


  /* Stack storage */

  /**
   * Fills this tank from the fluid stored on one item of the stack.
   * Successor to {@code readFromNBT}: each item stores the fluid relative to stack size 1, so it scales up here.
   * @param stored  Fluid stored per item
   */
  public void setStoredFluid(FluidStack stored) {
    setFluid(stored.isEmpty() ? FluidStack.EMPTY : stored.copyWithAmount(stored.getAmount() * scale));
  }

  /**
   * Gets the fluid to store on one item of the stack.
   * Successor to {@code writeToNBT}, scaling back down for the same reason.
   * @return  Fluid to store per item
   */
  public FluidStack getStoredFluid() {
    FluidStack fluid = getFluid();
    return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(fluid.getAmount() / scale);
  }
}
