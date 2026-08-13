package slimeknights.tconstruct.library.tools.nbt;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

/**
 * The data component types Tinkers stores a tool, its derived stats and a single material in.
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

  /**
   * The single material of a material item - a tool part, a part cast's target, a material block.
   * <p>
   * Successor to the {@code Material} string key in the item's tag, and the reason
   * {@code library/tools/part} could not be ported before {@code library/materials} was. Persistent and network
   * synchronized, and it takes part in stacking, which is what the tag key did too: two iron pickaxe heads are the
   * same item and stack, an iron and a copper head are not.
   * <p>
   * The codec is {@link MaterialVariantId#LENIENT_LOADABLE} rather than the strict one, because a component codec that
   * throws takes the whole stack with it.
   */
  public static final DeferredHolder<DataComponentType<?>,DataComponentType<MaterialVariantId>> MATERIAL =
    COMPONENTS.registerComponentType("material", builder -> builder
      .persistent(MaterialVariantId.LENIENT_LOADABLE.codec())
      .networkSynchronized(MaterialVariantId.LENIENT_LOADABLE));

  /**
   * Marks a stack as a display prop rather than a real item: the tools a recipe viewer shows, the previews a station
   * paints into its result slot, the part a recycling recipe offers back. Such a stack is deliberately half built, so
   * {@link ToolStack#verifyComponents} leaves it alone and the tooltip code skips the lines that would lie about it.
   * <p>
   * This is the home 1.20's {@code tic_display} tag key never had after T5 deleted the item tag, and T7 and T9 both
   * declined to pick. Three shapes were possible and this is the reason for the one chosen:
   * <ul>
   *   <li><b>A field on {@link ToolDataComponent}</b> is wrong because two of the four readers are
   *       {@code MaterialItem} and {@code ToolPartItem}. A tool part is not a tool and carries no
   *       {@code tconstruct:tool} component at all, so the flag has to be able to sit on a stack that has none.</li>
   *   <li><b>An entry in {@code minecraft:custom_data}</b> would keep the 1.20 bytes, but T5 deleted the allowlist that
   *       used to fence Tinkers' keys off from other mods' - {@code RawDataNBT} hands a modifier the whole compound
   *       with plain string keys by design. An internal flag living there is one {@code RawDataModifierHook} away from
   *       being cleared by a modifier that never knew it existed.</li>
   *   <li><b>Its own component</b> is what a boolean flag on a stack is in 1.21. {@link Unit} rather than a boolean
   *       because presence is the whole state, which is how vanilla spells {@code minecraft:fire_resistant} and
   *       {@code minecraft:intangible_projectile}.</li>
   * </ul>
   * Nothing is owed to the old format: every writer of {@code tic_display} builds its stack at runtime for a UI, so
   * no display stack has ever reached a save file.
   */
  public static final DeferredHolder<DataComponentType<?>,DataComponentType<Unit>> DISPLAY =
    COMPONENTS.registerComponentType("display", builder -> builder
      .persistent(Unit.CODEC)
      .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

  /** Registers the component types with the mod event bus */
  public static void init(IEventBus bus) {
    COMPONENTS.register(bus);
  }
}
