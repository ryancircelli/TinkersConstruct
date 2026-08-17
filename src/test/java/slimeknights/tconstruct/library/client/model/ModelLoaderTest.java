package slimeknights.tconstruct.library.client.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidType;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.SimpleBlockModel;
import slimeknights.tconstruct.library.client.model.block.FluidTextureModel;
import slimeknights.tconstruct.library.client.model.block.IncrementalFluidCuboid;
import slimeknights.tconstruct.library.client.model.block.TankModel;
import slimeknights.tconstruct.library.client.model.tools.MaterialBlockModel;
import slimeknights.tconstruct.library.client.model.tools.MaterialModel;
import slimeknights.tconstruct.library.client.model.tools.ToolModel;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parse level coverage of Tinkers' geometry loaders: everything reachable from a model JSON without a baking context,
 * a sprite atlas or a GL context, which is the whole of {@link slimeknights.tconstruct.library.client.model} that can
 * be tested headless. The baking half - which is where {@link ToolModel}'s per-material texture generation actually
 * lives - is a {@code runClient} checklist item instead; see {@code harness/t18-screenshot-checklist.md}.
 * <p>
 * These loaders are {@link JsonDeserializer}s rather than codecs, so they need a live {@link Gson} to supply the
 * {@link JsonDeserializationContext} they read nested block elements through. {@link #GSON} is the vanilla element
 * adapters plus one adapter per loader, which is what
 * {@link net.neoforged.neoforge.client.model.ExtendedBlockModelDeserializer} hands a loader at runtime minus the
 * geometry loader registry only a loaded game owns. This mirrors Mantle's own {@code ModelLoaderTest} (M10 §10).
 * <p>
 * Most of these loaders keep their parsed fields private with no accessors, so assertions go through AssertJ's
 * {@code extracting(String)}. That reads the field reflectively rather than asking production code to grow getters
 * purely for a test.
 */
class ModelLoaderTest extends BaseMcTest {
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(BlockElement.class, new BlockElement.Deserializer())
    .registerTypeAdapter(BlockElementFace.class, new BlockElementFace.Deserializer())
    .registerTypeAdapter(BlockFaceUV.class, new BlockFaceUV.Deserializer())
    .registerTypeAdapter(SimpleBlockModel.class, (JsonDeserializer<SimpleBlockModel>)
      (json, type, context) -> SimpleBlockModel.deserialize(json.getAsJsonObject(), context))
    .registerTypeAdapter(ColoredBlockModel.class, (JsonDeserializer<ColoredBlockModel>)
      (json, type, context) -> ColoredBlockModel.deserialize(json.getAsJsonObject(), context))
    .registerTypeAdapter(ToolModel.class, (JsonDeserializer<ToolModel>)
      (json, type, context) -> ToolModel.deserialize(json.getAsJsonObject(), context))
    .registerTypeAdapter(MaterialModel.class, (JsonDeserializer<MaterialModel>)
      (json, type, context) -> MaterialModel.deserialize(json.getAsJsonObject(), context))
    .registerTypeAdapter(MaterialBlockModel.class, (JsonDeserializer<MaterialBlockModel>)
      (json, type, context) -> MaterialBlockModel.deserialize(json.getAsJsonObject(), context))
    .registerTypeAdapter(TankModel.class, (JsonDeserializer<TankModel>)
      (json, type, context) -> TankModel.deserialize(json.getAsJsonObject(), context))
    .registerTypeAdapter(FluidTextureModel.class, (JsonDeserializer<FluidTextureModel>)
      (json, type, context) -> FluidTextureModel.deserialize(json.getAsJsonObject(), context))
    .registerTypeAdapter(UniqueGuiModel.class, (JsonDeserializer<UniqueGuiModel>)
      (json, type, context) -> UniqueGuiModel.deserialize(json.getAsJsonObject(), context))
    .registerTypeAdapter(FluidContainerModel.class, (JsonDeserializer<FluidContainerModel>)
      (json, type, context) -> FluidContainerModel.deserialize(json.getAsJsonObject(), context))
    .create();

  /** Parses a model JSON string through the adapter registered for the given type */
  private static <T> T parse(Class<T> type, String json) {
    return GSON.fromJson(json, type);
  }

  /** A minimal block model body, for the loaders that wrap one */
  private static final String ELEMENTS = """
    "textures": {"particle": "tconstruct:block/test"},
    "elements": [{"from": [0,0,0], "to": [16,16,16], "faces": {"north": {"texture": "#particle"}}}]""";


  /* ToolModel */

  @Test
  void tool_defaultsToNoPartsAndSmall() {
    ToolModel model = parse(ToolModel.class, "{}");
    assertThat(model).extracting("toolParts").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    assertThat(model).extracting("isLarge").isEqualTo(false);
    assertThat(model).extracting("showTraits").isEqualTo(false);
    assertThat(model).extracting("ammoKey").isNull();
  }

  @Test
  void tool_readsPartsWithIndexes() {
    ToolModel model = parse(ToolModel.class, """
      {"parts": [{"name": "head", "index": 0}, {"name": "handle", "index": 1}, {"name": "binding"}]}""");
    assertThat(model).extracting("toolParts").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(3);
  }

  @Test
  void tool_readsLargeOffset() {
    ToolModel model = parse(ToolModel.class, """
      {"large": true, "large_offset": [-3.5, 2.0]}""");
    assertThat(model).extracting("isLarge").isEqualTo(true);
    assertThat(model).extracting("offset.x").isEqualTo(-3.5f);
    assertThat(model).extracting("offset.y").isEqualTo(2.0f);
  }

  /** A small tool's modifier roots are a bare array; a large tool's are an object splitting small from large */
  @Test
  void tool_smallModifierRootsAreAnArray() {
    ToolModel model = parse(ToolModel.class, """
      {"modifier_roots": ["tconstruct:tools/pickaxe"]}""");
    assertThat(model).extracting("smallModifierRoots").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(1);
    assertThat(model).extracting("largeModifierRoots").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
  }

  @Test
  void tool_largeModifierRootsSplitSmallAndLarge() {
    ToolModel model = parse(ToolModel.class, """
      {"large": true, "modifier_roots": {"small": ["tconstruct:tools/small"], "large": ["tconstruct:tools/large", "tconstruct:tools/extra"]}}""");
    assertThat(model).extracting("smallModifierRoots").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(1);
    assertThat(model).extracting("largeModifierRoots").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(2);
  }

  @Test
  void tool_readsModifierMaps() {
    ToolModel model = parse(ToolModel.class, """
      {"modifier_maps": ["tconstruct:tools/melee", "tconstruct:tools/harvest"]}""");
    assertThat(model).extracting("modifierModels").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(2);
  }

  /**
   * The ammo key is the field this slice changed the meaning of: it names either a data component
   * ({@code minecraft:charged_projectiles}, for crossbows) or a persistent data key
   * ({@code tconstruct:drawback_ammo}, for bows). Parsing treats both the same - the read tells them apart.
   */
  @Test
  void tool_readsComponentAmmoKey() {
    ToolModel model = parse(ToolModel.class, """
      {"ammo": {"key": "minecraft:charged_projectiles", "flip": true, "left": false, "offset": [3, 4]}}""");
    assertThat(model).extracting("ammoKey").hasToString("minecraft:charged_projectiles");
    assertThat(model).extracting("flipAmmo").isEqualTo(true);
    assertThat(model).extracting("leftAmmo").isEqualTo(false);
    assertThat(model).extracting("smallAmmoOffset.x").isEqualTo(3f);
  }

  @Test
  void tool_readsPersistentDataAmmoKey() {
    ToolModel model = parse(ToolModel.class, """
      {"ammo": {"key": "tconstruct:drawback_ammo", "flip": false, "left": true, "offset": [0, 0]}}""");
    assertThat(model).extracting("ammoKey").hasToString("tconstruct:drawback_ammo");
    assertThat(model).extracting("leftAmmo").isEqualTo(true);
  }

  /** A large tool's ammo takes a small and/or large offset rather than the single "offset" a small tool takes */
  @Test
  void tool_largeAmmoTakesBothOffsets() {
    ToolModel model = parse(ToolModel.class, """
      {"large": true, "ammo": {"key": "tconstruct:drawback_ammo", "flip": false, "left": false,
       "small_offset": [1, 2], "large_offset": [3, 4]}}""");
    assertThat(model).extracting("smallAmmoOffset.x").isEqualTo(1f);
    assertThat(model).extracting("largeAmmoOffset.y").isEqualTo(4f);
  }

  @Test
  void tool_largeAmmoRejectsMissingBothOffsets() {
    assertThatThrownBy(() -> parse(ToolModel.class, """
      {"large": true, "ammo": {"key": "tconstruct:drawback_ammo", "flip": false, "left": false}}"""))
      .isInstanceOf(JsonSyntaxException.class)
      .hasMessageContaining("Ammo must either have a small or large offset provided");
  }

  /** first_modifiers accepts a bare id or an object naming the forced flag */
  @Test
  void tool_firstModifiersAcceptsBothForms() {
    ToolModel model = parse(ToolModel.class, """
      {"first_modifiers": ["tconstruct:embellishment", {"name": "tconstruct:overslime", "forced": true}]}""");
    assertThat(model).extracting("firstModifiers").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(2);
  }


  /* MaterialModel */

  @Test
  void material_defaultsToDynamicMaterialAtIndexZero() {
    MaterialModel model = parse(MaterialModel.class, "{}");
    assertThat(model).extracting("material").isNull();
    assertThat(model).extracting("index").isEqualTo(0);
    assertThat(model).extracting("offset.x").isEqualTo(0f);
  }

  @Test
  void material_readsStaticMaterialAndOffset() {
    MaterialModel model = parse(MaterialModel.class, """
      {"index": 2, "material": "tconstruct:iron", "offset": [1.5, -2.5]}""");
    assertThat(model).extracting("material").hasToString("tconstruct:iron");
    assertThat(model).extracting("index").isEqualTo(2);
    assertThat(model).extracting("offset.x").isEqualTo(1.5f);
    assertThat(model).extracting("offset.y").isEqualTo(-2.5f);
  }

  /** The offset is a fixed length pair; a wrong length is a parse error rather than a silently dropped component */
  @Test
  void material_rejectsWrongLengthOffset() {
    assertThatThrownBy(() -> parse(MaterialModel.class, "{\"offset\": [1, 2, 3]}"))
      .hasMessageContaining("Expected 2 offset values, found: 3");
  }


  /* MaterialBlockModel - the type is chosen by which key is present, not by a "type" field */

  @Test
  void materialBlock_retexturedKeyMeansAnvil() {
    MaterialBlockModel model = parse(MaterialBlockModel.class, "{" + ELEMENTS + """
      , "retextured": "texture"}""");
    assertThat(model).extracting("type").hasToString("ANVIL");
  }

  @Test
  void materialBlock_materialKeyMeansPart() {
    MaterialBlockModel model = parse(MaterialBlockModel.class, "{" + ELEMENTS + """
      , "material": "texture"}""");
    assertThat(model).extracting("type").hasToString("PART");
  }

  @Test
  void materialBlock_partsKeyMeansTool() {
    MaterialBlockModel model = parse(MaterialBlockModel.class, "{" + ELEMENTS + """
      , "parts": [["head"], ["handle"]]}""");
    assertThat(model).extracting("type").hasToString("TOOL");
    assertThat(model).extracting("parts").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(2);
  }


  /* TankModel */

  @Test
  void tank_readsFluidAndDefaults() {
    TankModel model = parse(TankModel.class, "{" + ELEMENTS + """
      , "fluid": {"from": [1,1,1], "to": [15,15,15], "increments": 4}}""");
    assertThat(model).extracting("model").isNotNull();
    assertThat(model).extracting("gui").isNull();
    assertThat(model).extracting("forceModelFluid").isEqualTo(false);
    assertThat(model).extracting("fluid.increments").isEqualTo(4);
  }

  @Test
  void tank_readsGuiVariantAndForcedFluid() {
    TankModel model = parse(TankModel.class, "{" + ELEMENTS + """
      , "fluid": {"from": [1,1,1], "to": [15,15,15], "increments": 8},
        "render_fluid_in_model": true,
        "gui": {"textures": {"particle": "tconstruct:block/gui"},
                "elements": [{"from": [0,0,0], "to": [8,8,8], "faces": {"north": {"texture": "#particle"}}}]}}""");
    assertThat(model).extracting("gui").isNotNull();
    assertThat(model).extracting("forceModelFluid").isEqualTo(true);
    assertThat(model).extracting("fluid.increments").isEqualTo(8);
  }

  /** The fluid cuboid is required; a tank without one cannot decide how to scale its contents */
  @Test
  void tank_rejectsMissingFluid() {
    assertThatThrownBy(() -> parse(TankModel.class, "{" + ELEMENTS + "}"))
      .hasMessageContaining("fluid");
  }

  @Test
  void incrementalFluidCuboid_readsBoundsAndIncrements() {
    IncrementalFluidCuboid fluid = IncrementalFluidCuboid.fromJson(
      GSON.fromJson("{\"from\": [2,3,4], \"to\": [12,13,14], \"increments\": 6}", JsonObject.class));
    assertThat(fluid.getIncrements()).isEqualTo(6);
    assertThat(fluid.getFrom().x()).isEqualTo(2f);
    assertThat(fluid.getTo().z()).isEqualTo(14f);
  }


  /* FluidTextureModel */

  @Test
  void fluidTexture_readsBothTextureNameSets() {
    FluidTextureModel model = parse(FluidTextureModel.class, "{" + ELEMENTS + """
      , "fluids": ["fluid", "flowing_fluid"], "retextured": ["texture"]}""");
    assertThat(model).extracting("fluids").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION).hasSize(2);
    assertThat(model).extracting("retextured").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION).hasSize(1);
  }

  @Test
  void fluidTexture_bothSetsAreOptional() {
    FluidTextureModel model = parse(FluidTextureModel.class, "{" + ELEMENTS + "}");
    assertThat(model).extracting("fluids").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION).isEmpty();
    assertThat(model).extracting("retextured").asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION).isEmpty();
  }


  /* UniqueGuiModel */

  @Test
  void uniqueGui_readsBothModels() {
    UniqueGuiModel model = parse(UniqueGuiModel.class, "{" + ELEMENTS + """
      , "gui": {"textures": {"particle": "tconstruct:block/gui"},
                "elements": [{"from": [0,0,0], "to": [8,8,8], "faces": {"north": {"texture": "#particle"}}}]}}""");
    assertThat(model.model).isNotNull();
    assertThat(model.gui).isNotNull();
    assertThat(model.model).isNotSameAs(model.gui);
  }

  /** The whole point of this loader is the GUI variant, so it is required rather than defaulted */
  @Test
  void uniqueGui_rejectsMissingGui() {
    assertThatThrownBy(() -> parse(UniqueGuiModel.class, "{" + ELEMENTS + "}"))
      .hasMessageContaining("gui");
  }


  /* FluidContainerModel - the loader whose JSON this slice changed, so the format is pinned in both forms */

  @Test
  void fluidContainer_defaultsToNoFluidAndFlippedGas() {
    FluidContainerModel model = parse(FluidContainerModel.class, "{}");
    assertThat(model.fluid().isEmpty()).isTrue();
    assertThat(model.flipGas()).isTrue();
  }

  /** The bare id form is unchanged from 1.20 and fills a bucket's worth */
  @Test
  void fluidContainer_readsBareFluidId() {
    FluidContainerModel model = parse(FluidContainerModel.class, """
      {"fluid": "minecraft:water"}""");
    assertThat(model.fluid().getFluid()).isEqualTo(Fluids.WATER);
    assertThat(model.fluid().getAmount()).isEqualTo(FluidType.BUCKET_VOLUME);
  }

  /**
   * The object form replaces 1.20's {@code {"name": ..., "nbt": {...}}}. A FluidStack has no tag in 1.21, so the
   * object names the fluid under {@code "fluid"} and its non-default data under {@code "components"}, which is the
   * shape every other fluid Tinkers reads from JSON already uses.
   */
  @Test
  void fluidContainer_readsObjectFormWithComponents() {
    FluidContainerModel model = parse(FluidContainerModel.class, """
      {"fluid": {"fluid": "minecraft:lava", "components": {"minecraft:item_name": '"Molten Test"'}}}""");
    assertThat(model.fluid().getFluid()).isEqualTo(Fluids.LAVA);
    assertThat(model.fluid().getAmount()).isEqualTo(FluidType.BUCKET_VOLUME);
    assertThat(model.fluid().isComponentsPatchEmpty()).isFalse();
  }

  @Test
  void fluidContainer_objectFormWithoutComponents() {
    FluidContainerModel model = parse(FluidContainerModel.class, """
      {"fluid": {"fluid": "minecraft:water"}, "flip_gas": false}""");
    assertThat(model.fluid().getFluid()).isEqualTo(Fluids.WATER);
    assertThat(model.fluid().isComponentsPatchEmpty()).isTrue();
    assertThat(model.flipGas()).isFalse();
  }

  @Test
  void fluidContainer_rejectsUnknownFluid() {
    assertThatThrownBy(() -> parse(FluidContainerModel.class, """
      {"fluid": "tconstruct:not_a_real_fluid"}"""))
      .hasMessageContaining("not_a_real_fluid");
  }


  /* TinkerItemProperties - not a geometry loader, but the ammo property reads the same component the tool model does */

  @Test
  void itemProperties_registersWithoutTouchingTheClient() {
    // registerBrokenProperty is the narrowest entry point that exercises the class's static initialisers, which is
    // what would trip if a property function referenced something that no longer resolves
    TinkerItemProperties.registerBrokenProperty(Items.IRON_PICKAXE);
  }
}
