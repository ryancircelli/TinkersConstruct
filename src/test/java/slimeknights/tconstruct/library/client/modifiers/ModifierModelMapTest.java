package slimeknights.tconstruct.library.client.modifiers;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.modifiers.model.CompoundModifierModel;
import slimeknights.tconstruct.library.client.modifiers.model.ModifierModel;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parsing tests for the modifier model map: the resource pack format under
 * {@code tinkering/modifiers/sprites} that decides which overlay a modifier draws.
 * <p>
 * The loaders are registered here rather than through {@code TinkerClient#onConstruct}, which does far more and needs
 * a mod event bus. Only the types with no dependency on an unported package are registered, which is why trim, banner
 * and material are absent - they reach into {@code tools/} and would put this file behind the frontier.
 */
class ModifierModelMapTest extends BaseMcTest {
  @BeforeAll
  static void registerLoaders() {
    ModifierModel.LOADER.register(TConstruct.getResource("empty"), ModifierModel.EMPTY.getLoader());
    ModifierModel.LOADER.register(TConstruct.getResource("compound"), CompoundModifierModel.LOADER);
    ModifierModel.LOADER.register(TConstruct.getResource("basic"), NormalModifierModel.LOADER);
    ModifierModel.LOADER.register(TConstruct.getResource("dyed"), DyedModifierModel.LOADER);
    ModifierModel.LOADER.register(TConstruct.getResource("potion"), PotionModifierModel.LOADER);
  }

  /** Parses a model from a JSON string */
  private static ModifierModel parse(String json) {
    return ModifierModel.LOADER.convert(JsonHelper.DEFAULT_GSON.fromJson(json, JsonElement.class), "test", TypedMap.empty());
  }

  /** The material a texture path resolves to; every modifier model texture is on the block atlas */
  private static Material blockAtlas(String path) {
    return new Material(InventoryMenu.BLOCK_ATLAS, ResourceLocation.parse(path));
  }

  @Test
  void basic_readsBothTextures() {
    ModifierModel model = parse("""
      {"type": "tconstruct:basic", "texture": "tconstruct:item/modifiers/haste", "texture_large": "tconstruct:item/modifiers/haste_large"}""");
    assertThat(model).isInstanceOf(NormalModifierModel.class);
    NormalModifierModel normal = (NormalModifierModel)model;
    assertThat(normal.small()).isEqualTo(blockAtlas("tconstruct:item/modifiers/haste"));
    assertThat(normal.large()).isEqualTo(blockAtlas("tconstruct:item/modifiers/haste_large"));
    assertThat(normal.color()).isEqualTo(-1);
    assertThat(normal.luminosity()).isZero();
  }

  @Test
  void basic_largeTextureIsOptional() {
    NormalModifierModel model = (NormalModifierModel)parse("""
      {"type": "tconstruct:basic", "texture": "tconstruct:item/modifiers/haste", "color": "FF8040", "luminosity": 7}""");
    assertThat(model.large()).isNull();
    assertThat(model.color()).isEqualTo(0xFFFF8040);
    assertThat(model.luminosity()).isEqualTo(7);
  }

  @Test
  void model_roundTrips() {
    // the same loadable writes the pack format back out, so a serialize/parse pair pins the format rather than only
    // the reader. Datagen writes these files, so a one-way change would produce packs this cannot read.
    String json = """
      {"type": "tconstruct:dyed", "texture": "tconstruct:item/modifiers/dyed", "texture_large": "tconstruct:item/modifiers/dyed_large"}""";
    ModifierModel parsed = parse(json);
    JsonElement written = ModifierModel.LOADER.serialize(parsed);
    assertThat(parse(written.toString())).isEqualTo(parsed);
  }

  @Test
  void compound_keepsOrder() {
    ModifierModel model = parse("""
      {"type": "tconstruct:compound", "models": [
        {"type": "tconstruct:basic", "texture": "tconstruct:item/modifiers/first"},
        {"type": "tconstruct:basic", "texture": "tconstruct:item/modifiers/second"}
      ]}""");
    assertThat(model).isInstanceOf(CompoundModifierModel.class);
    assertThat(((CompoundModifierModel)model).models())
      .extracting(nested -> ((NormalModifierModel)nested).small())
      .containsExactly(blockAtlas("tconstruct:item/modifiers/first"), blockAtlas("tconstruct:item/modifiers/second"));
  }

  @Test
  void compound_dropsEmptyModelsFromJson() {
    // the loadable validates a JSON-parsed compound by discarding empty entries, so a pack that names a model type
    // whose textures are all missing collapses to the one that is left rather than drawing nothing
    ModifierModel model = parse("""
      {"type": "tconstruct:compound", "models": [
        {"type": "tconstruct:basic", "texture": "tconstruct:item/modifiers/only"},
        {"type": "tconstruct:empty"}
      ]}""");
    assertThat(((CompoundModifierModel)model).models()).hasSize(1);
  }

  @Test
  void unknownType_isAJsonError() {
    // an unknown loader has to be a JSON error rather than a silent empty model, or a typo in a pack is invisible
    assertThatThrownBy(() -> parse("""
      {"type": "tconstruct:not_a_model", "texture": "tconstruct:item/modifiers/haste"}"""))
      .isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void map_isEmptyWhenBothHalvesAre() {
    // ModifierModelMapManager drops a file whose map came back EMPTY, so identity here is what decides whether a
    // tool model looks the map up at all
    assertThat(ModifierModelMap.create(Map.of(), Map.of())).isSameAs(ModifierModelMap.EMPTY);
    assertThat(ModifierModelMap.EMPTY.isEmpty()).isTrue();

    ModifierId haste = new ModifierId(TConstruct.MOD_ID, "haste");
    ModifierModelMap map = ModifierModelMap.create(Map.of(), Map.of(haste, (IBakedModifierModel)parse("""
      {"type": "tconstruct:basic", "texture": "tconstruct:item/modifiers/haste"}""")));
    assertThat(map).isNotSameAs(ModifierModelMap.EMPTY);
    assertThat(map.isEmpty()).isFalse();
    assertThat(map.get(haste)).isNotNull();
    assertThat(map.get(new ModifierId(TConstruct.MOD_ID, "absent"))).isNull();
  }
}
