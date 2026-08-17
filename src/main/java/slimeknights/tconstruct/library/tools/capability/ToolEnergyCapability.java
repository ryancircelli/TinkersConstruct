package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.energy.IEnergyStorage;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.build.ModifierTraitModule;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider.IToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.CapacityStat;
import slimeknights.tconstruct.library.tools.stat.ToolStatId;
import slimeknights.tconstruct.tools.TinkerModifiers;


/**
 * Standard implementation of energy capability on a tool. Not currently used in the mod directly, but should help addons have more unity.
 * <p>
 * The tool is the bound, mutable one the capability query created (see {@link slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider}),
 * and the two methods that move energy commit it. In 1.20 a tool shared the stack's tag so a write landed by itself;
 * since T5 it does not, and an uncommitted transfer would read as energy vanishing into the tool.
 */
public record ToolEnergyCapability(ToolStack tool) implements IEnergyStorage {
  /** Format string to display energy amounts, used internally by the stat */
  public static final String ENERGY_FORMAT = TConstruct.makeDescriptionId("tool_stat", "energy");
  /** Stat marking the max capacity */
  public static final CapacityStat MAX_STAT = new CapacityStat(new ToolStatId(TConstruct.MOD_ID, "max_energy"), 0xa00000, ENERGY_FORMAT);
  /** Persistent data key for fetching the current energy */
  public static final ResourceLocation ENERGY_KEY = TConstruct.getResource("energy");
  /** Include this module in a modifier adding energy capacity or functionality to ensure capacity changes are properly cleaned up */
  public static final ModifierModule ENERGY_HANDLER = new ModifierTraitModule(TinkerModifiers.energyHandler.getId(), 1, true);

  /** Gets the energy capacity for the given tool */
  public static int getMaxEnergy(IToolStackView tool) {
    return tool.getStats().getInt(MAX_STAT);
  }

  /** Gets the current energy on a tool */
  public static int getEnergy(IToolStackView tool) {
    return tool.getPersistentData().getInt(ENERGY_KEY);
  }

  /** Internal method to set energy, skips a capacity check */
  private static void setEnergyRaw(IToolStackView tool, int energy) {
    if (energy == 0) {
      tool.getPersistentData().remove(ENERGY_KEY);
    } else {
      tool.getPersistentData().putInt(ENERGY_KEY, energy);
    }
  }

  /** Sets the energy on the tool */
  public static void setEnergy(IToolStackView tool, int energy) {
    setEnergyRaw(tool, Mth.clamp(energy, 0, getMaxEnergy(tool)));
  }

  /** Adds the given amount of energy to the tool. Can use negative to subtract energy. */
  public static void addEnergy(IToolStackView tool, int energy) {
    if (energy != 0) {
      setEnergy(tool, getEnergy(tool) + energy);
    }
  }

  /** Ensures the tool's energy is within the cap. Generally not necessary to call this directly as it's called by {@link #ENERGY_HANDLER} on tool change. */
  public static void checkEnergy(IToolStackView tool) {
    int energy = ToolEnergyCapability.getEnergy(tool);
    if (energy < 0) {
      ToolEnergyCapability.setEnergyRaw(tool, 0);
    } else {
      int capacity = ToolEnergyCapability.getMaxEnergy(tool);
      if (energy > capacity) {
        ToolEnergyCapability.setEnergyRaw(tool, capacity);
      }
    }
  }

  @Override
  public int receiveEnergy(int maxReceive, boolean simulate) {
    if (maxReceive <= 0) {
      return 0;
    }
    int current = getEnergy(tool);
    int filled = Math.min(getMaxEnergy(tool) - current, maxReceive);
    if (!simulate) {
      setEnergyRaw(tool, current + filled);
      tool.updateStack();
    }
    return filled;
  }

  @Override
  public int extractEnergy(int maxExtract, boolean simulate) {
    if (maxExtract <= 0) {
      return 0;
    }
    int current = getEnergy(tool);
    if (current <= 0) {
      return 0;
    }
    int drained = maxExtract;
    if (current < drained) {
      drained = current;
    }
    if (!simulate) {
      setEnergyRaw(tool, current - drained);
      tool.updateStack();
    }
    return drained;
  }

  @Override
  public int getEnergyStored() {
    return getEnergy(tool);
  }

  @Override
  public int getMaxEnergyStored() {
    return getMaxEnergy(tool);
  }

  @Override
  public boolean canExtract() {
    return true;
  }

  @Override
  public boolean canReceive() {
    return true;
  }

  /** Provider instance for the energy cap, offered only by a tool with energy capacity */
  public static final IToolCapabilityProvider<IEnergyStorage> PROVIDER =
    (stack, tool) -> tool.getStats().getInt(MAX_STAT) > 0 ? new ToolEnergyCapability(tool) : null;
}
