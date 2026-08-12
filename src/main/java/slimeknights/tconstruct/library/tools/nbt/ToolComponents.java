package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import slimeknights.tconstruct.TConstruct;

/**
 * The two data component types a tool is stored in.
 * <p>
 * They live here rather than on a {@code Tinker*} module class because they are what {@link ToolStack} is, and every
 * user of this package needs them; a module class that also registers items and blocks would make this package depend
 * on the mod's registration order for no gain. Whoever wires the mod up calls {@link #init(IEventBus)}.
 */
public class ToolComponents {
  private ToolComponents() {}

  /** Registries is named explicitly: the single argument overload is deprecated for removal, as 1.21 has a second data component registry for enchantment effects */
  private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TConstruct.MOD_ID);

  /**
   * Everything about a tool that is saved: materials, upgrades, persistent modifier data and the broken flag.
   * Persistent and network synchronized, and it takes part in stacking, which is the point.
   */
  public static final DeferredHolder<DataComponentType<?>,DataComponentType<ToolDataComponent>> TOOL =
    COMPONENTS.registerComponentType("tool", builder -> builder
      .persistent(ToolDataComponent.CODEC)
      .networkSynchronized(ToolDataComponent.STREAM_CODEC));

  /**
   * Everything a tool computes: stats, multipliers, the merged modifier list and volatile modifier data.
   * <p>
   * Network synchronized only, with no {@code persistent} call, so this never reaches disk. See
   * {@link ToolStatsComponent} for why that is the whole point of splitting the tool in two.
   */
  public static final DeferredHolder<DataComponentType<?>,DataComponentType<ToolStatsComponent>> TOOL_STATS =
    COMPONENTS.registerComponentType("tool_stats", builder -> builder
      .networkSynchronized(ToolStatsComponent.STREAM_CODEC));

  /** Registers the component types with the mod event bus */
  public static void init(IEventBus bus) {
    COMPONENTS.register(bus);
  }
}
