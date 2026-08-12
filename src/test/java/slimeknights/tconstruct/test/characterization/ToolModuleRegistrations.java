package slimeknights.tconstruct.test.characterization;

import net.minecraft.resources.ResourceLocation;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.registry.GenericLoaderRegistry;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.definition.module.ToolModule;
import slimeknights.tconstruct.library.tools.definition.module.aoe.AreaOfEffectIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.BoxAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.CircleAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.ConditionalAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.TreeAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.aoe.VeiningAOEIterator;
import slimeknights.tconstruct.library.tools.definition.module.build.MultiplyStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.SetStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolActionsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolSlotsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolTraitsModule;
import slimeknights.tconstruct.library.tools.definition.module.build.VolatileFlagModule;
import slimeknights.tconstruct.library.tools.definition.module.build.VolatileIntModule;
import slimeknights.tconstruct.library.tools.definition.module.display.CustomMaterialName;
import slimeknights.tconstruct.library.tools.definition.module.display.FixedMaterialToolName;
import slimeknights.tconstruct.library.tools.definition.module.display.MaterialToolNameModule;
import slimeknights.tconstruct.library.tools.definition.module.display.SimpleToolName;
import slimeknights.tconstruct.library.tools.definition.module.display.StatTypesToolNameModule;
import slimeknights.tconstruct.library.tools.definition.module.display.UniqueMaterialToolName;
import slimeknights.tconstruct.library.tools.definition.module.interaction.AttackInteraction;
import slimeknights.tconstruct.library.tools.definition.module.interaction.DualOptionInteraction;
import slimeknights.tconstruct.library.tools.definition.module.interaction.PreferenceSetInteraction;
import slimeknights.tconstruct.library.tools.definition.module.interaction.ToggleableSetInteraction;
import slimeknights.tconstruct.library.tools.definition.module.material.DefaultMaterialsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.MaterialRepairModule;
import slimeknights.tconstruct.library.tools.definition.module.material.MaterialStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.MaterialTraitsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.PartStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.PartsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.StatlessPartRepairModule;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveModule;
import slimeknights.tconstruct.library.tools.definition.module.mining.MaxTierModule;
import slimeknights.tconstruct.library.tools.definition.module.mining.MiningSpeedModifierModule;
import slimeknights.tconstruct.library.tools.definition.module.mining.OneClickBreakModule;
import slimeknights.tconstruct.library.tools.definition.module.weapon.CircleWeaponAttack;
import slimeknights.tconstruct.library.tools.definition.module.weapon.ParticleWeaponAttack;
import slimeknights.tconstruct.library.tools.definition.module.weapon.SweepWeaponAttack;
import slimeknights.tconstruct.tools.modules.MeltingFluidEffectiveModule;

/**
 * Test-only mirror of every {@code ToolModule.LOADER}/{@code AreaOfEffectIterator} registration performed by
 * {@code TinkerTools#registerRecipeSerializers}, an event handler these headless unit tests never fire (see
 * {@code BaseMcTest}). Registers all 38 real module type ids so real tool definition JSON parses without a
 * "test:" namespace substitution (unlike the existing synthetic-fixture {@code ToolDefinitionLoaderTest}).
 */
public final class ToolModuleRegistrations {
  private ToolModuleRegistrations() {}

  /**
   * Deliberately NOT guarded by a one-shot "already ran" flag: this suite observed real, hard-to-pin-down cases
   * where some other test in the same JVM left one or two of these registrations transiently missing despite no
   * exception being thrown here (see notes/T-A0-corpus.md - the same order-dependent shared registry state
   * documented for the predicate-inversion asymmetry). Each individual registration is already safe to repeat
   * (duplicate-registration exceptions are swallowed), so always attempting the full list on every call is cheap
   * insurance against that instead of trusting a single earlier caller to have completed successfully.
   */
  public static synchronized void ensureRegistered() {
    register(ToolModule.LOADER, TConstruct.getResource("empty"), ToolModule.EMPTY.getLoader());
    register(ToolModule.LOADER, TConstruct.getResource("base_stats"), SetStatsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("multiply_stats"), MultiplyStatsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("tool_actions"), ToolActionsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("traits"), ToolTraitsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("modifier_slots"), ToolSlotsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("volatile_flag"), VolatileFlagModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("volatile_int"), VolatileIntModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("is_effective"), IsEffectiveModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("mining_speed_modifier"), MiningSpeedModifierModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("max_tier"), MaxTierModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("one_click_break"), OneClickBreakModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("material_stats"), MaterialStatsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("part_stats"), PartStatsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("material_traits"), MaterialTraitsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("tool_parts"), PartsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("material_repair"), MaterialRepairModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("default_materials"), DefaultMaterialsModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("statless_part_repair"), StatlessPartRepairModule.LOADER);
    // AreaOfEffectIterator.register(id, loader) normally registers into BOTH ToolModule.LOADER and its own
    // AreaOfEffectIterator.LOADER; replicated as two direct register() calls here (rather than calling that
    // static helper) so both go through the collision-safe register() below, not just a swallowed safe(Runnable).
    registerAoe(TConstruct.getResource("box_aoe"), BoxAOEIterator.LOADER);
    registerAoe(TConstruct.getResource("circle_aoe"), CircleAOEIterator.LOADER);
    registerAoe(TConstruct.getResource("tree_aoe"), TreeAOEIterator.LOADER);
    registerAoe(TConstruct.getResource("vein_aoe"), VeiningAOEIterator.LOADER);
    registerAoe(TConstruct.getResource("conditional_aoe"), ConditionalAOEIterator.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("sweep_melee"), SweepWeaponAttack.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("circle_melee"), CircleWeaponAttack.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("melee_particle"), ParticleWeaponAttack.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("attack_interaction"), AttackInteraction.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("dual_option_interaction"), DualOptionInteraction.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("preference_set_interaction"), PreferenceSetInteraction.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("toggleable_set_interaction"), ToggleableSetInteraction.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("melting_fluid_effective"), MeltingFluidEffectiveModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("item_name"), SimpleToolName.ITEM.getLoader());
    register(ToolModule.LOADER, TConstruct.getResource("material_name"), MaterialToolNameModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("stat_types_name"), StatTypesToolNameModule.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("fixed_material_name"), FixedMaterialToolName.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("unique_material_name"), UniqueMaterialToolName.LOADER);
    register(ToolModule.LOADER, TConstruct.getResource("custom_material_name"), CustomMaterialName.LOADER);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void registerAoe(ResourceLocation id, RecordLoadable loader) {
    register(ToolModule.LOADER, id, loader);
    register(AreaOfEffectIterator.LOADER, id, loader);
  }

  /**
   * Registers {@code loader} under {@code id} in {@code registry}, tolerating this method having already run in
   * this JVM but nothing else.
   * <p>
   * {@code GenericLoaderRegistry} is backed by a Guava {@code BiMap} (name &lt;-&gt; loader, both directions
   * unique), so a loader object can only ever hold one name: any other test registering one of these production
   * singletons under a name of its own takes it away from its real {@code tconstruct:} id, and then whether this
   * suite works depends on which class JUnit ran first. That used to be worked around by reflectively
   * {@code forcePut}ing the id, which silently dropped the other test's name and only looked safe because JUnit
   * does not interleave test classes. Tests wanting a synthetic id now register an alias object instead (see
   * {@code RegistrationFixture#alias}), so a collision here means a test has reintroduced the hazard and should
   * fail loudly rather than be papered over.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void register(GenericLoaderRegistry registry, ResourceLocation id, RecordLoadable loader) {
    try {
      registry.register(id, loader);
    } catch (IllegalArgumentException alreadyRegistered) {
      // the same loader under the same id is just this method running again, which is expected
      if (!id.equals(registry.getName(loader))) {
        throw alreadyRegistered;
      }
    }
  }
}
