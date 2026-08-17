package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.loadable.common.NBTLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

import javax.annotation.Nullable;

/**
 * Value of the {@code tconstruct:tool_stats} data component: everything a tool computes from
 * {@link ToolDataComponent}, its tool definition and the loaded datapacks.
 * <p>
 * <b>This component is network synchronized and is never saved.</b> It is registered without a persistent codec, so
 * {@code DataComponentPatch}'s codec drops it on write and rejects it on read, and it never reaches disk. That is a
 * decision rather than an accident: derived data in a saved component is a second copy of a value the game can already
 * compute, and a stale copy makes two tools that are the same tool compare unequal forever, which
 * {@link ItemStack#isSameItemSameComponents} would then propagate into stacking, recipe matching and every
 * {@code ItemStack.matches} caller.
 * <p>
 * It is still part of the runtime component map, so it does take part in equality within a session. That is sound
 * because it is a pure function of the persistent component and the loaded data: two stacks with equal
 * {@code tconstruct:tool} that have both been through {@link ToolStack#rebuildStats()} hold equal values here. The one
 * window where they can differ is between a stack arriving with no derived component and the rebuild that fills it in,
 * which is exactly what {@link ToolStack#isInitialized(ItemStack)} tests and what
 * {@link ToolStack#verifyComponents(ItemStack)} and {@code ensureHasData} close.
 *
 * @param stats         Final tool stats, after modifiers
 * @param multipliers   Global multipliers per stat
 * @param modifiers     Every effective modifier, from upgrades plus tool and material traits
 * @param volatileData  Modifier data rebuilt from scratch on every stat rebuild. Treat as immutable, see
 *                      {@link #mutableVolatileData()}
 */
public record ToolStatsComponent(StatsNBT stats, MultiplierNBT multipliers, ModifierNBT modifiers, CompoundTag volatileData) {
  /** Value for a tool that computes to nothing. Distinct from having no component at all, which means "not computed yet". */
  public static final ToolStatsComponent EMPTY = new ToolStatsComponent(StatsNBT.EMPTY, MultiplierNBT.EMPTY, ModifierNBT.EMPTY, new CompoundTag());

  /**
   * Loadable for the component. Only its {@link slimeknights.mantle.data.loadable.Streamable} half is wired into the
   * component type; the json half exists because a loadable cannot offer one without the other, and because the
   * tinker station and the modifier worktable display computed stats for a tool they have not built yet.
   * <p>
   * Do not hand {@link RecordLoadable#codec()} to {@code DataComponentType.Builder#persistent}. See the class javadoc.
   */
  public static final RecordLoadable<ToolStatsComponent> LOADABLE = RecordLoadable.create(
    StatsNBT.LOADABLE.defaultField("stats", StatsNBT.EMPTY, ToolStatsComponent::stats),
    MultiplierNBT.LOADABLE.defaultField("multipliers", MultiplierNBT.EMPTY, ToolStatsComponent::multipliers),
    ModifierNBT.LOADABLE.defaultField("modifiers", ModifierNBT.EMPTY, ToolStatsComponent::modifiers),
    NBTLoadable.DISALLOW_STRING.defaultField("volatile_data", EMPTY.volatileData(), ToolStatsComponent::volatileData),
    ToolStatsComponent::new);
  /** Stream codec, the only half of the loadable the component type uses */
  public static final StreamCodec<RegistryFriendlyByteBuf,ToolStatsComponent> STREAM_CODEC = LOADABLE;

  /** Reads the component off a stack, or null when the tool has not been computed yet */
  @Nullable
  public static ToolStatsComponent get(ItemStack stack) {
    return stack.get(ToolComponents.TOOL_STATS);
  }

  /**
   * Writes this component onto a stack.
   * Unlike {@link ToolDataComponent#set(ItemStack)} an empty value is stored rather than removed, because absence
   * means "not computed" here and a tool really can compute to nothing.
   */
  public void set(ItemStack stack) {
    stack.set(ToolComponents.TOOL_STATS, this);
  }

  /** Gets an editable copy of the volatile modifier data, for the same reason {@link ToolDataComponent#mutableData()} exists */
  public ToolDataNBT mutableVolatileData() {
    return ToolDataNBT.readFromNBT(volatileData.copy());
  }

  /** Gets a read only view of the volatile modifier data */
  public IModDataView volatileView() {
    return volatileData.isEmpty() ? IModDataView.EMPTY : ToolDataNBT.readFromNBT(volatileData);
  }
}
