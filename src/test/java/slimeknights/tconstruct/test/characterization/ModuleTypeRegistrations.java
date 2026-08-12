package slimeknights.tconstruct.test.characterization;

import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.registry.GenericLoaderRegistry;
import slimeknights.tconstruct.TConstruct;
import slimeknights.mantle.data.predicate.entity.LivingEntityPredicate;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.block.BreakBlockFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.block.MobEffectCloudFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.block.PlaceBlockFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.entity.AwardStatFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.entity.DamageFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.entity.FireFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.entity.MobEffectFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.entity.RemoveEffectFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.entity.RestoreHungerFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.general.ConditionalFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.general.DropItemFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.general.ScalingFluidEffect;
import slimeknights.tconstruct.library.modifiers.fluid.general.SequenceFluidEffect;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.armor.AdjustDamageModule;
import slimeknights.tconstruct.library.modifiers.modules.armor.MaxArmorAttributeModule;
import slimeknights.tconstruct.library.modifiers.modules.armor.ProtectionModule;
import slimeknights.tconstruct.library.modifiers.modules.behavior.AttributeModule;
import slimeknights.tconstruct.library.modifiers.modules.behavior.ConditionalStatModule;
import slimeknights.tconstruct.library.modifiers.modules.behavior.RepairModule;
import slimeknights.tconstruct.library.modifiers.modules.build.EnchantmentModule;
import slimeknights.tconstruct.library.modifiers.modules.build.ModifierSlotModule;
import slimeknights.tconstruct.library.modifiers.modules.build.StatBoostModule;
import slimeknights.tconstruct.library.modifiers.modules.combat.ConditionalMeleeDamageModule;
import slimeknights.tconstruct.library.modifiers.modules.combat.KnockbackModule;
import slimeknights.tconstruct.library.modifiers.modules.combat.LootingModule;
import slimeknights.tconstruct.library.modifiers.modules.combat.MobEffectModule;
import slimeknights.tconstruct.library.modifiers.modules.combat.SlingForceModule;
import slimeknights.tconstruct.library.modifiers.modules.display.ModifierVariantNameModule;
import slimeknights.tconstruct.library.modifiers.modules.mining.ConditionalMiningSpeedModule;
import slimeknights.tconstruct.library.json.predicate.modifier.ModifierPredicate;
import slimeknights.tconstruct.library.json.predicate.modifier.SingleModifierPredicate;
import slimeknights.tconstruct.library.json.predicate.modifier.SlotTypeModifierPredicate;
import slimeknights.tconstruct.library.json.predicate.modifier.TagModifierPredicate;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;
import slimeknights.tconstruct.library.tools.capability.inventory.InventoryMenuModule;
import slimeknights.tconstruct.tools.modules.DamageOnUnequipModule;
import slimeknights.tconstruct.tools.modules.HeadlightModule;
import slimeknights.tconstruct.tools.modules.SmeltingModule;
import slimeknights.tconstruct.tools.modules.TheOneProbeModule;
import slimeknights.tconstruct.tools.modules.combat.LifestealModule;
import slimeknights.tconstruct.tools.modules.durability.ShareDurabilityModule;
import slimeknights.tconstruct.tools.modules.ranged.TrickQuiverModule;
import slimeknights.tconstruct.tools.modules.ranged.bow.QuiverInventoryModule;
import slimeknights.tconstruct.tools.modules.ranged.common.ProjectileBounceModule;

/**
 * Test-only mirror of the module type registrations that production performs in
 * {@code TinkerModifiers#registerSerializers}, an event handler gated on a Forge {@code RegisterEvent} that
 * these headless unit tests never fire (see {@code BaseMcTest}). Registers exactly the module/effect types
 * exercised by the {@code characterization/modifiers} and {@code characterization/fluid_effects} fixtures,
 * mirroring the registered id and loader field 1:1 with {@code TinkerModifiers.java}.
 */
public final class ModuleTypeRegistrations {
  private ModuleTypeRegistrations() {}

  /**
   * Deliberately NOT guarded by a one-shot "already ran" flag - see the identical note on
   * {@code ToolModuleRegistrations#ensureRegistered}. Safe to call repeatedly from multiple test classes'
   * {@code @BeforeAll}.
   */
  public static synchronized void ensureRegistered() {
    registerModifierModules();
    registerFluidEffects();
    registerModifierPredicates();
    registerModifierLevelDisplays();
    registerEntityAndDamagePredicates();
    registerVariables();
  }

  private static void registerEntityAndDamagePredicates() {
    // mantle
    safeRegister(LivingEntityPredicate.LOADER, slimeknights.mantle.Mantle.getResource("on_fire"), LivingEntityPredicate.ON_FIRE.getLoader());
    safeRegister(LivingEntityPredicate.LOADER, slimeknights.mantle.Mantle.getResource("on_ground"), LivingEntityPredicate.ON_GROUND.getLoader());
    safeRegister(LivingEntityPredicate.LOADER, slimeknights.mantle.Mantle.getResource("has_effect"), slimeknights.mantle.data.predicate.entity.HasMobEffectPredicate.LOADER);
    safeRegister(slimeknights.mantle.data.predicate.damage.DamageSourcePredicate.LOADER, slimeknights.mantle.Mantle.getResource("can_protect"), slimeknights.mantle.data.predicate.damage.DamageSourcePredicate.CAN_PROTECT.getLoader());
    safeRegister(slimeknights.mantle.data.predicate.damage.DamageSourcePredicate.LOADER, slimeknights.mantle.Mantle.getResource("attacker"), slimeknights.mantle.data.predicate.damage.SourceAttackerPredicate.LOADER);
    safeRegister(slimeknights.mantle.data.predicate.damage.DamageSourcePredicate.LOADER, slimeknights.mantle.Mantle.getResource("is_indirect"), slimeknights.mantle.data.predicate.damage.DamageSourcePredicate.IS_INDIRECT.getLoader());
    safeRegister(LivingEntityPredicate.LOADER, slimeknights.mantle.Mantle.getResource("eyes_in_water"), LivingEntityPredicate.EYES_IN_WATER.getLoader());
    safeRegister(LivingEntityPredicate.LOADER, slimeknights.mantle.Mantle.getResource("feet_in_water"), LivingEntityPredicate.FEET_IN_WATER.getLoader());
    // tconstruct (registered in TinkerCommons#registerSerializers)
    safeRegister(slimeknights.mantle.data.predicate.damage.DamageSourcePredicate.LOADER, "direct", slimeknights.tconstruct.library.json.predicate.TinkerPredicate.DIRECT_DAMAGE.getLoader());
    safeRegister(LivingEntityPredicate.LOADER, "airborne", slimeknights.tconstruct.library.json.predicate.TinkerPredicate.AIRBORNE.getLoader());
    safeRegister(LivingEntityPredicate.LOADER, "targeting_block", slimeknights.tconstruct.library.json.predicate.TinkerPredicate.TARGETING_BLOCK.getLoader());
    safeRegister(LivingEntityPredicate.LOADER, "full_health", slimeknights.tconstruct.library.json.predicate.TinkerPredicate.FULL_HEALTH.getLoader());
    safeRegister(LivingEntityPredicate.LOADER, "variable_range", slimeknights.tconstruct.library.json.predicate.EntityVariableRangePredicate.LOADER);
    safeRegister(LivingEntityPredicate.LOADER, "block_at_feet", slimeknights.tconstruct.library.json.predicate.BlockAtFeetEntityPredicate.LOADER);
  }

  /** Mirrors the "variables" formula system registrations (block/entity/tool/stat/melee/power/mining/protection). */
  private static void registerVariables() {
    // block
    var blockVariable = slimeknights.tconstruct.library.json.variable.block.BlockVariable.LOADER;
    safeRegister(blockVariable, "constant", slimeknights.tconstruct.library.json.variable.block.BlockVariable.Constant.LOADER);
    safeRegister(blockVariable, "conditional", slimeknights.tconstruct.library.json.variable.block.ConditionalBlockVariable.LOADER);
    safeRegister(blockVariable, "blast_resistance", slimeknights.tconstruct.library.json.variable.block.BlockVariable.BLAST_RESISTANCE.getLoader());
    safeRegister(blockVariable, "hardness", slimeknights.tconstruct.library.json.variable.block.BlockVariable.HARDNESS.getLoader());
    safeRegister(blockVariable, "state_property", slimeknights.tconstruct.library.json.variable.block.StatePropertyVariable.LOADER);
    // entity
    var entityVariable = slimeknights.tconstruct.library.json.variable.entity.EntityVariable.LOADER;
    safeRegister(entityVariable, "constant", slimeknights.tconstruct.library.json.variable.entity.EntityVariable.Constant.LOADER);
    safeRegister(entityVariable, "conditional", slimeknights.tconstruct.library.json.variable.entity.ConditionalEntityVariable.LOADER);
    safeRegister(entityVariable, "health", slimeknights.tconstruct.library.json.variable.entity.EntityVariable.HEALTH.getLoader());
    safeRegister(entityVariable, "height", slimeknights.tconstruct.library.json.variable.entity.EntityVariable.HEIGHT.getLoader());
    safeRegister(entityVariable, "attribute", slimeknights.tconstruct.library.json.variable.entity.AttributeEntityVariable.LOADER);
    safeRegister(entityVariable, "effect_level", slimeknights.tconstruct.library.json.variable.entity.EntityEffectLevelVariable.LOADER);
    safeRegister(entityVariable, "light", slimeknights.tconstruct.library.json.variable.entity.EntityLightVariable.LOADER);
    safeRegister(entityVariable, "equipment_count", slimeknights.tconstruct.library.json.variable.entity.EquipmentCountEntityVariable.LOADER);
    safeRegister(entityVariable, "biome_temperature", slimeknights.tconstruct.library.json.variable.entity.EntityVariable.BIOME_TEMPERATURE.getLoader());
    safeRegister(entityVariable, "water", slimeknights.tconstruct.library.json.variable.entity.EntityVariable.WATER.getLoader());
    safeRegister(entityVariable, "armor_coverage", slimeknights.tconstruct.library.json.variable.entity.EntityVariable.ARMOR_COVERAGE.getLoader());
    safeRegister(entityVariable, "player_stat", slimeknights.tconstruct.library.json.variable.entity.PlayerStatVariable.LOADER);
    // tool - ToolVariable.register (NOT ToolVariable.LOADER.register) is a fan-out helper that also registers
    // into MeleeVariable, ConditionalStatVariable (which itself fans into MiningSpeedVariable), and
    // ProtectionVariable - using the plain .LOADER.register here would silently under-register 4 other
    // registries, exactly as it did for "tool_lost_durability" during development of this test suite.
    var toolVariable = slimeknights.tconstruct.library.json.variable.tool.ToolVariable.LOADER;
    safeRegister(toolVariable, "constant", slimeknights.tconstruct.library.json.variable.tool.ToolVariable.Constant.LOADER);
    safeRegisterToolVariable("tool_conditional", slimeknights.tconstruct.library.json.variable.tool.ConditionalToolVariable.LOADER);
    safeRegisterToolVariable("tool_durability", slimeknights.tconstruct.library.json.variable.tool.ToolVariable.CURRENT_DURABILITY.getLoader());
    safeRegisterToolVariable("tool_lost_durability", slimeknights.tconstruct.library.json.variable.tool.ToolVariable.CURRENT_DAMAGE.getLoader());
    safeRegisterToolVariable("tool_stat", slimeknights.tconstruct.library.json.variable.tool.ToolStatVariable.LOADER);
    safeRegisterToolVariable("stat_multiplier", slimeknights.tconstruct.library.json.variable.tool.StatMultiplierVariable.LOADER);
    safeRegisterToolVariable("mod_data", slimeknights.tconstruct.library.json.variable.tool.ModDataVariable.LOADER);
    safeRegisterToolVariable("modifier_level", slimeknights.tconstruct.library.json.variable.tool.ModifierLevelVariable.LOADER);
    // stat - ConditionalStatVariable.register similarly fans into MiningSpeedVariable too
    var statVariable = slimeknights.tconstruct.library.json.variable.stat.ConditionalStatVariable.LOADER;
    safeRegister(statVariable, "constant", slimeknights.tconstruct.library.json.variable.stat.ConditionalStatVariable.Constant.LOADER);
    safeRegisterStatVariable("entity", slimeknights.tconstruct.library.json.variable.stat.EntityConditionalStatVariable.LOADER);
    // melee
    var meleeVariable = slimeknights.tconstruct.library.json.variable.melee.MeleeVariable.LOADER;
    safeRegister(meleeVariable, "constant", slimeknights.tconstruct.library.json.variable.melee.MeleeVariable.Constant.LOADER);
    safeRegister(meleeVariable, "entity", slimeknights.tconstruct.library.json.variable.melee.EntityMeleeVariable.LOADER);
    // power
    var powerVariable = slimeknights.tconstruct.library.json.variable.power.PowerVariable.LOADER;
    safeRegister(powerVariable, "constant", slimeknights.tconstruct.library.json.variable.power.PowerVariable.Constant.LOADER);
    safeRegister(powerVariable, "entity", slimeknights.tconstruct.library.json.variable.power.EntityPowerVariable.LOADER);
    safeRegister(powerVariable, "persistent_data", slimeknights.tconstruct.library.json.variable.power.PersistentDataPowerVariable.LOADER);
    // mining speed
    var miningSpeedVariable = slimeknights.tconstruct.library.json.variable.mining.MiningSpeedVariable.LOADER;
    safeRegister(miningSpeedVariable, "constant", slimeknights.tconstruct.library.json.variable.mining.MiningSpeedVariable.Constant.LOADER);
    safeRegister(miningSpeedVariable, "block", slimeknights.tconstruct.library.json.variable.mining.BlockMiningSpeedVariable.LOADER);
    safeRegister(miningSpeedVariable, "block_light", slimeknights.tconstruct.library.json.variable.mining.BlockLightVariable.LOADER);
    safeRegister(miningSpeedVariable, "biome_temperature", slimeknights.tconstruct.library.json.variable.mining.BlockTemperatureVariable.LOADER);
    safeRegister(miningSpeedVariable, "effective", slimeknights.tconstruct.library.json.variable.mining.EffectiveMiningSpeedVariable.LOADER);
    // protection
    var protectionVariable = slimeknights.tconstruct.library.json.variable.protection.ProtectionVariable.LOADER;
    safeRegister(protectionVariable, "constant", slimeknights.tconstruct.library.json.variable.protection.ProtectionVariable.Constant.LOADER);
    safeRegister(protectionVariable, "entity", slimeknights.tconstruct.library.json.variable.protection.EntityProtectionVariable.LOADER);
  }

  private static void registerModifierLevelDisplays() {
    safeRegister(ModifierLevelDisplay.LOADER, "default", ModifierLevelDisplay.DEFAULT.getLoader());
    safeRegister(ModifierLevelDisplay.LOADER, "single_level", ModifierLevelDisplay.SINGLE_LEVEL.getLoader());
    safeRegister(ModifierLevelDisplay.LOADER, "no_levels", ModifierLevelDisplay.NO_LEVELS.getLoader());
    safeRegister(ModifierLevelDisplay.LOADER, "pluses", ModifierLevelDisplay.PLUSES.getLoader());
    safeRegister(ModifierLevelDisplay.LOADER, "unique", ModifierLevelDisplay.UniqueForLevels.LOADER);
    safeRegister(ModifierLevelDisplay.LOADER, "cap_level", ModifierLevelDisplay.LevelCap.LOADER);
  }

  private static void registerModifierPredicates() {
    safeRegister(ModifierPredicate.LOADER, "single", SingleModifierPredicate.LOADER);
    safeRegister(ModifierPredicate.LOADER, "tag", TagModifierPredicate.LOADER);
    safeRegister(ModifierPredicate.LOADER, "slot_type", SlotTypeModifierPredicate.LOADER);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void safeRegister(GenericLoaderRegistry registry, String name, Object loader) {
    safeRegister(registry, TConstruct.getResource(name), loader);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void safeRegisterToolVariable(String name, RecordLoadable loader) {
    try {
      slimeknights.tconstruct.library.json.variable.tool.ToolVariable.register(TConstruct.getResource(name), loader);
    } catch (Exception ignored) {
      // already registered - fine
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void safeRegisterStatVariable(String name, RecordLoadable loader) {
    try {
      slimeknights.tconstruct.library.json.variable.stat.ConditionalStatVariable.register(TConstruct.getResource(name), loader);
    } catch (Exception ignored) {
      // already registered - fine
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void safeRegister(GenericLoaderRegistry registry, net.minecraft.resources.ResourceLocation id, Object loader) {
    try {
      registry.register(id, (RecordLoadable) loader);
    } catch (IllegalArgumentException e) {
      // "Duplicate registration <id>" (harmless, we already did this) vs "value already present: <loader>"
      // (a different test registered this exact loader singleton under a different name first - see the
      // detailed explanation on ToolModuleRegistrations#register, the same fix applies here) both land here;
      // only the latter needs the reflective steal, but attempting it for either is harmless.
      forcePut(registry, id, loader);
    }
  }

  @SuppressWarnings("rawtypes")
  private static void forcePut(GenericLoaderRegistry registry, net.minecraft.resources.ResourceLocation id, Object loader) {
    try {
      java.lang.reflect.Field loadersField = GenericLoaderRegistry.class.getDeclaredField("loaders");
      loadersField.setAccessible(true);
      Object namedComponentRegistry = loadersField.get(registry);
      java.lang.reflect.Field valuesField = namedComponentRegistry.getClass().getDeclaredField("values");
      valuesField.setAccessible(true);
      com.google.common.collect.BiMap<net.minecraft.resources.ResourceLocation,Object> values =
        (com.google.common.collect.BiMap<net.minecraft.resources.ResourceLocation,Object>) valuesField.get(namedComponentRegistry);
      values.forcePut(id, loader);
    } catch (ReflectiveOperationException | ClassCastException ignored) {
      // registry internals may differ for this registry type (e.g. PredicateRegistry) - not worth failing over
    }
  }

  private static void registerModifierModules() {
    safeRegister(ModifierModule.LOADER, "max_armor_attribute", MaxArmorAttributeModule.LOADER);
    safeRegister(ModifierModule.LOADER, "protection", ProtectionModule.LOADER);
    safeRegister(ModifierModule.LOADER, "adjust_damage", AdjustDamageModule.LOADER);
    safeRegister(ModifierModule.LOADER, "attribute", AttributeModule.LOADER);
    safeRegister(ModifierModule.LOADER, "repair", RepairModule.LOADER);
    safeRegister(ModifierModule.LOADER, "conditional_stat", ConditionalStatModule.LOADER);
    safeRegister(ModifierModule.LOADER, "modifier_slot", ModifierSlotModule.LOADER);
    safeRegister(ModifierModule.LOADER, "stat_boost", StatBoostModule.LOADER);
    safeRegister(ModifierModule.LOADER, "conditional_melee_damage", ConditionalMeleeDamageModule.LOADER);
    safeRegister(ModifierModule.LOADER, "knockback", KnockbackModule.LOADER);
    safeRegister(ModifierModule.LOADER, "sling_force", SlingForceModule.LOADER);
    safeRegister(ModifierModule.LOADER, "mob_effect", MobEffectModule.LOADER);
    safeRegister(ModifierModule.LOADER, "variant_name", ModifierVariantNameModule.LOADER);
    safeRegister(ModifierModule.LOADER, "constant_enchantment", EnchantmentModule.Constant.LOADER);
    safeRegister(ModifierModule.LOADER, "armor_harvest_enchantment", EnchantmentModule.ArmorHarvest.LOADER);
    safeRegister(ModifierModule.LOADER, "enchantment_ignoring_protection", EnchantmentModule.Protection.LOADER);
    safeRegister(ModifierModule.LOADER, "weapon_looting", LootingModule.Weapon.LOADER);
    safeRegister(ModifierModule.LOADER, "armor_looting", LootingModule.Armor.LOADER);
    safeRegister(ModifierModule.LOADER, "conditional_mining_speed", ConditionalMiningSpeedModule.LOADER);
    safeRegister(ModifierModule.LOADER, "inventory_menu", InventoryMenuModule.LOADER);
    safeRegister(ModifierModule.LOADER, "smelting", SmeltingModule.LOADER);
    safeRegister(ModifierModule.LOADER, "damage_on_unequip", DamageOnUnequipModule.LOADER);
    safeRegister(ModifierModule.LOADER, "share_durability", ShareDurabilityModule.LOADER);
    safeRegister(ModifierModule.LOADER, "projectile_bounce", ProjectileBounceModule.LOADER);
    safeRegister(ModifierModule.LOADER, "lifesteal", LifestealModule.LOADER);
    safeRegister(ModifierModule.LOADER, "trick_quiver", TrickQuiverModule.LOADER);
    safeRegister(ModifierModule.LOADER, "quiver_inventory", QuiverInventoryModule.LOADER);
    safeRegister(ModifierModule.LOADER, "the_one_probe", TheOneProbeModule.INSTANCE.getLoader());
    safeRegister(ModifierModule.LOADER, "headlight", HeadlightModule.LOADER);
  }

  private static void registerFluidEffects() {
    safeRegister(FluidEffect.BLOCK_EFFECTS, "conditional", ConditionalFluidEffect.Block.LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "conditional", ConditionalFluidEffect.Entity.LOADER);
    safeRegister(FluidEffect.BLOCK_EFFECTS, "scaling", ScalingFluidEffect.BLOCK_LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "scaling", ScalingFluidEffect.ENTITY_LOADER);
    safeRegister(FluidEffect.BLOCK_EFFECTS, "sequence", SequenceFluidEffect.BLOCK_LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "sequence", SequenceFluidEffect.ENTITY_LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "remove_effect", RemoveEffectFluidEffect.LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "mob_effect", MobEffectFluidEffect.LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "damage", DamageFluidEffect.LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "restore_hunger", RestoreHungerFluidEffect.LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "fire", FireFluidEffect.LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "award_stat", AwardStatFluidEffect.LOADER);
    safeRegister(FluidEffect.ENTITY_EFFECTS, "teleport", slimeknights.tconstruct.library.modifiers.fluid.entity.RandomTeleportFluidEffect.LOADER);
    safeRegister(FluidEffect.BLOCK_EFFECTS, "place_block", PlaceBlockFluidEffect.LOADER);
    safeRegister(FluidEffect.BLOCK_EFFECTS, "mob_effect_cloud", MobEffectCloudFluidEffect.LOADER);
    safeRegister(FluidEffect.BLOCK_EFFECTS, "break_block", BreakBlockFluidEffect.LOADER);
    try {
      FluidEffect.registerGeneral(TConstruct.getResource("drop_item"), DropItemFluidEffect.LOADER);
    } catch (Exception ignored) {
      // already registered - fine
    }
    safeRegister(LivingEntityPredicate.LOADER, slimeknights.mantle.Mantle.getResource("fire_immune"), LivingEntityPredicate.FIRE_IMMUNE.getLoader());
    // defensive: SourceAttackerPredicate embeds a LivingEntityPredicate directly into a DamageSourcePredicate's
    // JSON object via a "direct field" (no nested wrapper), which appears to sometimes route compact string
    // lookups through the outer DamageSourcePredicate registry's own name table instead - register the same
    // entity-predicate singletons there too so real fixtures like "scorch_protection" resolve either way.
    safeRegister(slimeknights.mantle.data.predicate.damage.DamageSourcePredicate.LOADER, slimeknights.mantle.Mantle.getResource("fire_immune"), LivingEntityPredicate.FIRE_IMMUNE.getLoader());
  }
}
