package slimeknights.tconstruct.test.characterization;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.mantle.recipe.helper.TypeAwareRecipeSerializer;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.casting.CastDuplicationRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.PotionCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.RetexturedCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.TipClearingCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.TippingCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.container.ContainerFillingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.CompositeCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.PartSwapCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.ToolCastingRecipe;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipe;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.melting.DamageableMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MaterialMeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.melting.OreMeltingRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierSalvage;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IncrementalModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.MultilevelIncrementalModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.MultilevelModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.OverslimeModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.SwappableModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.severing.AgeableSeveringRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.severing.SeveringRecipe;
import slimeknights.tconstruct.library.recipe.molding.MoldingRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.ItemPartRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.PartRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.recycle.PartBuilderRecycle;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.FixedMaterialSwappingRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.PartSwappingOverrideRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolMaterialSwappingRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.repairing.ModifierRepairTinkerStationRecipe;
import slimeknights.tconstruct.library.recipe.worktable.ModifierSetWorktableRecipe;
import slimeknights.tconstruct.tables.recipe.PartBuilderToolRecycle;
import slimeknights.tconstruct.tables.recipe.TinkerStationDamagingRecipe;
import slimeknights.tconstruct.tables.recipe.TinkerStationPartSwapping;
import slimeknights.tconstruct.tools.recipe.EnchantmentConvertingRecipe;
import slimeknights.tconstruct.tools.recipe.ExtractModifierRecipe;
import slimeknights.tconstruct.tools.recipe.ModifierRemovalRecipe;
import slimeknights.tconstruct.tools.recipe.ModifierSortingRecipe;
import slimeknights.tconstruct.tools.recipe.ToggleInteractionWorktableRecipe;
import slimeknights.tconstruct.tools.recipe.TippedToolTransformRecipe;
import slimeknights.tconstruct.tools.recipe.severing.MooshroomDemushroomingRecipe;
import slimeknights.tconstruct.tools.recipe.severing.PlayerBeheadingRecipe;
import slimeknights.tconstruct.tools.recipe.severing.SheepShearingRecipe;
import slimeknights.tconstruct.tools.recipe.severing.SnowGolemBeheadingRecipe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Test-only mirror of the recipe serializer registrations found in {@code TinkerTables}, {@code TinkerSmeltery}
 * and {@code TinkerModifiers}. Real recipe serializer instances are only reachable via Forge's
 * {@code DeferredRegister}/{@code RegistryObject}, which requires firing the mod's registry events - something
 * these headless unit tests deliberately avoid (see {@code BaseMcTest}). Since every one of these recipe types
 * exposes its {@code RecordLoadable} directly as a public static {@code LOADER} field (the same field the real
 * serializer delegates to, and the same field TConstruct's own datagen providers call directly), tests can drive
 * the JSON/network round trip through the loadable without needing the serializer/registry at all.
 * <p>
 * The JSON {@code "type"} field of a recipe is the *serializer* registry name (matching vanilla convention), so
 * this map is keyed by that name (without the {@code tconstruct:} prefix).
 */
public final class RecipeLoaderRegistry {
  private RecipeLoaderRegistry() {}

  /** Serializer name (unprefixed) -> loadable to use for direct round trips. */
  public static final Map<String,RecordLoadable<? extends Recipe<?>>> LOADERS = new HashMap<>();
  /** Serializer names whose loadable requires a {@link LoadableRecipeSerializer#TYPED_SERIALIZER}/{@link LoadableRecipeSerializer#TYPE} context (the casting/molding family). */
  public static final Set<String> NEEDS_TYPED_SERIALIZER = new HashSet<>();

  private static void plain(String name, RecordLoadable<? extends Recipe<?>> loader) {
    LOADERS.put(name, loader);
  }

  private static void typed(String name, RecordLoadable<? extends Recipe<?>> loader) {
    LOADERS.put(name, loader);
    NEEDS_TYPED_SERIALIZER.add(name);
  }

  static {
    // part_builder
    plain("part_builder", PartRecipe.LOADER);
    plain("item_part_builder", ItemPartRecipe.LOADER);
    plain("part_builder_tool_recycling", PartBuilderToolRecycle.LOADER);
    plain("part_builder_recycling", PartBuilderRecycle.LOADER);
    // material
    plain("material", MaterialRecipe.LOADER);
    // tinker_station
    plain("tool_building", ToolBuildingRecipe.LOADER);
    plain("tinker_station_part_swapping", TinkerStationPartSwapping.LOADER);
    plain("tinker_station_damaging", TinkerStationDamagingRecipe.LOADER);
    plain("fixed_material_swapping", FixedMaterialSwappingRecipe.LOADER);
    plain("part_swapping_override", PartSwappingOverrideRecipe.LOADER);
    plain("tool_material_swapping", ToolMaterialSwappingRecipe.LOADER);
    plain("modifier_repair", ModifierRepairTinkerStationRecipe.LOADER);
    plain("tipped_tool_transform", TippedToolTransformRecipe.LOADER);
    // modifier_worktable
    plain("modifier", ModifierRecipe.LOADER);
    plain("incremental_modifier", IncrementalModifierRecipe.LOADER);
    plain("swappable_modifier", SwappableModifierRecipe.LOADER);
    plain("multilevel_modifier", MultilevelModifierRecipe.LOADER);
    plain("multilevel_incremental_modifier", MultilevelIncrementalModifierRecipe.LOADER);
    plain("overslime_modifier", OverslimeModifierRecipe.LOADER);
    plain("modifier_set_worktable", ModifierSetWorktableRecipe.LOADER);
    plain("toggle_interaction", ToggleInteractionWorktableRecipe.LOADER);
    plain("enchantment_converting", EnchantmentConvertingRecipe.LOADER);
    plain("remove_modifier", ModifierRemovalRecipe.LOADER);
    plain("extract_modifier", ExtractModifierRecipe.LOADER);
    plain("modifier_sorting", ModifierSortingRecipe.LOADER);
    // casting basin/table
    typed("casting_basin", ItemCastingRecipe.LOADER);
    typed("casting_table", ItemCastingRecipe.LOADER);
    typed("basin_filling", ContainerFillingRecipe.LOADER);
    typed("table_filling", ContainerFillingRecipe.LOADER);
    typed("basin_duplication", CastDuplicationRecipe.LOADER);
    typed("table_duplication", CastDuplicationRecipe.LOADER);
    typed("casting_basin_potion", PotionCastingRecipe.LOADER);
    typed("casting_table_potion", PotionCastingRecipe.LOADER);
    typed("casting_basin_tipping", TippingCastingRecipe.LOADER);
    typed("casting_table_tipping", TippingCastingRecipe.LOADER);
    typed("casting_basin_tipped_clearing", TipClearingCastingRecipe.LOADER);
    typed("casting_table_tipped_clearing", TipClearingCastingRecipe.LOADER);
    typed("retextured_casting_basin", RetexturedCastingRecipe.LOADER);
    typed("retextured_casting_table", RetexturedCastingRecipe.LOADER);
    typed("basin_casting_material", MaterialCastingRecipe.LOADER);
    typed("table_casting_material", MaterialCastingRecipe.LOADER);
    typed("basin_casting_composite", CompositeCastingRecipe.LOADER);
    typed("table_casting_composite", CompositeCastingRecipe.LOADER);
    typed("basin_tool_casting", ToolCastingRecipe.LOADER);
    typed("table_tool_casting", ToolCastingRecipe.LOADER);
    typed("basin_casting_part_swapping", PartSwapCastingRecipe.LOADER);
    typed("table_casting_part_swapping", PartSwapCastingRecipe.LOADER);
    plain("material_fluid", MaterialFluidRecipe.LOADER);
    // molding
    typed("molding_basin", MoldingRecipe.LOADER);
    typed("molding_table", MoldingRecipe.LOADER);
    // melting
    plain("melting", MeltingRecipe.LOADER);
    plain("ore_melting", OreMeltingRecipe.LOADER);
    plain("damagable_melting", DamageableMeltingRecipe.LOADER);
    plain("material_melting", MaterialMeltingRecipe.LOADER);
    plain("melting_fuel", MeltingFuel.LOADER);
    plain("entity_melting", EntityMeltingRecipe.LOADER);
    // alloying
    plain("alloy", AlloyRecipe.LOADER);
    // severing
    plain("severing", SeveringRecipe.LOADER);
    plain("ageable_severing", AgeableSeveringRecipe.LOADER);
    plain("player_beheading", PlayerBeheadingRecipe.LOADER);
    plain("snow_golem_beheading", SnowGolemBeheadingRecipe.LOADER);
    plain("mooshroom_demushrooming", MooshroomDemushroomingRecipe.LOADER);
    plain("sheep_shearing", SheepShearingRecipe.LOADER);
    // data
    plain("modifier_salvage", ModifierSalvage.LOADER);
  }

  /** Serializer names with no JSON/network representation at all - {@code SimpleRecipeSerializer} discards the body and only keeps the ID. */
  public static final Set<String> NO_DATA_TYPES = Set.of(
    "tinker_station_repair", "crafting_table_repair", "armor_dyeing_modifier", "banner_modifier", "armor_trim_modifier"
  );

  /** Builds a context suitable for the given serializer name. */
  public static TypedMap contextFor(String typeName, ResourceLocation id) {
    TypedMapBuilder builder = TypedMapBuilder.builder().put(ContextKey.ID, id).put(ContextKey.DEBUG, "characterization test " + id);
    if (NEEDS_TYPED_SERIALIZER.contains(typeName)) {
      RecipeType<?> dummyType = new RecipeType<>() {
        @Override
        public String toString() {
          return "tconstruct:" + typeName;
        }
      };
      TypeAwareRecipeSerializer dummySerializer = mock(TypeAwareRecipeSerializer.class);
      doReturn(dummyType).when(dummySerializer).getType();
      builder.put(LoadableRecipeSerializer.TYPE, dummyType);
      builder.put(LoadableRecipeSerializer.TYPED_SERIALIZER, dummySerializer);
      builder.put(LoadableRecipeSerializer.SERIALIZER, dummySerializer);
    } else {
      // some plain loadables still store the owning serializer (e.g. for getSerializer()); a mock is fine since it's never inspected here
      builder.put(LoadableRecipeSerializer.SERIALIZER, mock(net.minecraft.world.item.crafting.RecipeSerializer.class));
    }
    return builder.build();
  }
}
