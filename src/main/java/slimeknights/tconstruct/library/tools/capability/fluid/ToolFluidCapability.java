package slimeknights.tconstruct.library.tools.capability.fluid;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider.IToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;

/**
 * Logic to make a tool a fluid handler.
 * <p>
 * The tool is the bound, mutable one the capability query created (see {@link slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider}),
 * and {@link #fill} and both {@link #drain} overloads commit it. In 1.20 the modifier hooks wrote through a tool that
 * shared the stack's tag, so a transfer landed by itself; since T5 it does not, and a fill that is not committed reads
 * as a bucket emptying into nothing.
 */
@RequiredArgsConstructor
public class ToolFluidCapability extends FluidModifierHookIterator<ModifierEntry> implements IFluidHandlerItem {
  /** Boolean key to set in volatile mod data to enable the fluid capability */
  public static final ResourceLocation TOTAL_TANKS = TConstruct.getResource("total_tanks");

  /** Modifier hook instance to make an inventory modifier */
  public static final ModuleHook<FluidModifierHook> HOOK = ModifierHooks.register(TConstruct.getResource("fluid"), FluidModifierHook.class, FluidModifierHookMerger::new, new FluidModifierHook() {
    @Override
    public int getTanks(IModDataView volatileData, ModifierEntry modifier) {
      return 0;
    }

    @Override
    public boolean isFluidValid(IToolStackView tool, ModifierEntry modifier, int tank, FluidStack fluid) {
      return false;
    }

    @Override
    public int fill(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action) {
      return 0;
    }

    @Override
    public FluidStack drain(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action) {
      return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(IToolStackView tool, ModifierEntry modifier, int maxDrain, FluidAction action) {
      return FluidStack.EMPTY;
    }
  });

  @Getter
  private final ItemStack container;
  private final ToolStack tool;

  /* Basic inventory */

  @Override
  public int getTanks() {
    return tool.getVolatileData().getInt(TOTAL_TANKS);
  }

  @Override
  protected Iterator<ModifierEntry> getIterator(IToolStackView tool) {
    return tool.getModifierList().iterator();
  }

  @Override
  protected FluidModifierHook getHook(ModifierEntry entry) {
    indexEntry = entry;
    return entry.getHook(HOOK);
  }

  @Nonnull
  @Override
  public FluidStack getFluidInTank(int tank) {
    FluidModifierHook hook = findHook(tool, tank);
    if (hook != null) {
      return hook.getFluidInTank(tool, indexEntry, tank - startIndex);
    }
    return FluidStack.EMPTY;
  }

  @Override
  public int getTankCapacity(int tank) {
    FluidModifierHook hook = findHook(tool, tank);
    if (hook != null) {
      return hook.getTankCapacity(tool, indexEntry, tank - startIndex);
    }
    return 0;
  }

  @Override
  public boolean isFluidValid(int tank, FluidStack stack) {
    FluidModifierHook hook = findHook(tool, tank);
    if (hook != null) {
      return hook.isFluidValid(tool, indexEntry, tank - startIndex, stack);
    }
    return false;
  }

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    int filled = fill(tool, resource, action);
    if (filled > 0 && action.execute()) {
      tool.updateStack();
    }
    return filled;
  }

  /** Scales the result for the given stack size */
  private static FluidStack scaleResult(FluidStack stack, int size) {
    if (size > 1 && !stack.isEmpty()) {
      stack.setAmount(stack.getAmount() * size);
    }
    return stack;
  }

  @Nonnull
  @Override
  public FluidStack drain(FluidStack resource, FluidAction action) {
    if (resource.isEmpty()) {
      return FluidStack.EMPTY;
    }
    int size = container.getCount();
    if (size > 1) {
      resource = resource.copyWithAmount(resource.getAmount() / size);
    }
    return commit(scaleResult(drain(tool, resource, action), size), action);
  }

  @Nonnull
  @Override
  public FluidStack drain(int maxDrain, FluidAction action) {
    if (maxDrain < 0) {
      return FluidStack.EMPTY;
    }
    int size = container.getCount();
    return commit(scaleResult(drain(tool, maxDrain / size, action), size), action);
  }

  /** Writes the tool back to its stack if a drain actually took fluid */
  private FluidStack commit(FluidStack drained, FluidAction action) {
    if (!drained.isEmpty() && action.execute()) {
      tool.updateStack();
    }
    return drained;
  }

  /** Adds the tanks from the fluid modifier to the tool */
  public static void addTanks(ModifierEntry modifier, ModDataNBT volatileData, FluidModifierHook hook) {
    volatileData.putInt(TOTAL_TANKS, hook.getTanks(volatileData, modifier) + volatileData.getInt(TOTAL_TANKS));
  }

  /**
   * Interface for modifiers with fluid capabilities to return.
   * @deprecated We are considering removing this interface in favor of a much simpler tool tank implementation.
   * For most use-cases {@link ToolTankHelper} is sufficient. If it does not cover your usecase, please leave a comment on <a href="https://github.com/SlimeKnights/TinkersConstruct/issues/5353">GitHub #5353</a>.
   */
  @SuppressWarnings("unused")
  @Deprecated
  public interface FluidModifierHook {
    /**
     * Determines how many fluid tanks are used by this modifier
     * @param volatileData  Tool data to check
     * @param modifier      Modifier to consider
     * @return  Number of tanks used
     */
    default int getTanks(IModDataView volatileData, ModifierEntry modifier) {
      return 1;
    }

    /**
     * Gets the fluid in the given tank
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param tank      Tank index
     * @return  Fluid in the given tank
     */
    default FluidStack getFluidInTank(IToolStackView tool, ModifierEntry modifier, int tank) {
      return FluidStack.EMPTY;
    }

    /**
     * Gets the max capacity for the given tank
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param tank      Tank index
     * @return  Fluid in the given tank
     */
    default int getTankCapacity(IToolStackView tool, ModifierEntry modifier, int tank) {
      return 0;
    }

    /**
     * Checks if the fluid is valid for the given tank
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param tank      Tank index
     * @param fluid  Fluid to insert
     * @return  True if the fluid is valid
     */
    default boolean isFluidValid(IToolStackView tool, ModifierEntry modifier, int tank, FluidStack fluid) {
      return true;
    }

    /**
     * Fills fluid into tanks
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param resource  FluidStack representing the Fluid and maximum amount of fluid to be filled. If you want to store this stack, make a copy
     * @param action   If SIMULATE, fill will only be simulated.
     * @return Amount of resource that was (or would have been, if simulated) filled.
     */
    int fill(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action);

    /**
     * Drains fluid out of tanks, distribution is left entirely to the IFluidHandler.
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param resource  FluidStack representing the Fluid and maximum amount of fluid to be drained.
     * @param action    If SIMULATE, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    FluidStack drain(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action);

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
     * @param tool      Tool instance
     * @param modifier  Entry instance
     * @param maxDrain  Maximum amount of fluid to drain.
     * @param action    If SIMULATE, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    FluidStack drain(IToolStackView tool, ModifierEntry modifier, int maxDrain, FluidAction action);
  }

  /** Logic to merge multiple fluid hooks */
  @RequiredArgsConstructor
  private static class FluidModifierHookMerger extends FluidModifierHookIterator<FluidModifierHook> implements FluidModifierHook {
    private final Collection<FluidModifierHook> modules;

    @Override
    protected Iterator<FluidModifierHook> getIterator(IToolStackView tool) {
      return modules.iterator();
    }

    @Override
    protected FluidModifierHook getHook(FluidModifierHook entry) {
      return entry;
    }

    /** Gets the given hook */
    @Nullable
    private FluidModifierHook findHook(IToolStackView tool, ModifierEntry modifier, int tank) {
      indexEntry = modifier;
      return this.findHook(tool, tank);
    }

    @Override
    public int getTanks(IModDataView volatileData, ModifierEntry modifier) {
      int sum = 0;
      for (FluidModifierHook module : modules) {
        sum += module.getTanks(volatileData, modifier);
      }
      return sum;
    }

    @Override
    public FluidStack getFluidInTank(IToolStackView tool, ModifierEntry modifier, int tank) {
      FluidModifierHook hook = findHook(tool, modifier, tank);
      if (hook != null) {
        return hook.getFluidInTank(tool, modifier, tank - startIndex);
      }
      return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(IToolStackView tool, ModifierEntry modifier, int tank) {
      FluidModifierHook hook = findHook(tool, modifier, tank);
      if (hook != null) {
        return hook.getTankCapacity(tool, modifier, tank - startIndex);
      }
      return 0;
    }

    @Override
    public boolean isFluidValid(IToolStackView tool, ModifierEntry modifier, int tank, FluidStack fluid) {
      FluidModifierHook hook = findHook(tool, modifier, tank);
      if (hook != null) {
        return hook.isFluidValid(tool, modifier, tank - startIndex, fluid);
      }
      return false;
    }

    @Override
    public int fill(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action) {
      indexEntry = modifier;
      return fill(tool, resource, action);
    }

    @Override
    public FluidStack drain(IToolStackView tool, ModifierEntry modifier, FluidStack resource, FluidAction action) {
      indexEntry = modifier;
      return drain(tool, resource, action);
    }

    @Override
    public FluidStack drain(IToolStackView tool, ModifierEntry modifier, int maxDrain, FluidAction action) {
      indexEntry = modifier;
      return drain(tool, maxDrain, action);
    }
  }

  /** Provider instance for the fluid cap, offered only by a tool that has tanks */
  public static final IToolCapabilityProvider<IFluidHandlerItem> PROVIDER =
    (stack, tool) -> tool.getVolatileData().getInt(TOTAL_TANKS) > 0 ? new ToolFluidCapability(stack, tool) : null;
}
