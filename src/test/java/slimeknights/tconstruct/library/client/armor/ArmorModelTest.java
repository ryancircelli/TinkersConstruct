package slimeknights.tconstruct.library.client.armor;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModel;
import slimeknights.tconstruct.library.client.armor.texture.ArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.FirstArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.FixedArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.MaterialArmorTextureSupplier;
import slimeknights.tconstruct.library.client.armor.texture.MaterialHasFallbackTextureSupplier;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round trip tests for the armor model data format: the resource pack files under
 * {@code tinkering/armor_models} that {@link ArmorModelManager} loads and {@link MultilayerArmorModel} draws.
 * <p>
 * Only the suppliers this class round trips are registered. {@code dyed} and {@code trim} both default their modifier
 * field to a {@code TinkerModifiers} constant, so registering them would tie the format test to the content module.
 */
class ArmorModelTest extends BaseMcTest {
  @BeforeAll
  static void registerLoaders() {
    ArmorTextureSupplier.LOADER.register(TConstruct.getResource("fixed"), FixedArmorTextureSupplier.LOADER);
    ArmorTextureSupplier.LOADER.register(TConstruct.getResource("first_present"), FirstArmorTextureSupplier.LOADER);
    ArmorTextureSupplier.LOADER.register(TConstruct.getResource("material"), MaterialArmorTextureSupplier.Material.LOADER);
    ArmorTextureSupplier.LOADER.register(TConstruct.getResource("persistent_data"), MaterialArmorTextureSupplier.PersistentData.LOADER);
    ArmorTextureSupplier.LOADER.register(TConstruct.getResource("material_has_fallback"), MaterialHasFallbackTextureSupplier.LOADER);
  }

  /** Parses an armor model from a JSON string */
  private static ArmorModel parse(String json) {
    return ArmorModel.LOADABLE.convert(JsonHelper.DEFAULT_GSON.fromJson(json, JsonElement.class), "test");
  }

  @Test
  void layers_parseInOrder() {
    ArmorModel model = parse("""
      {"layers": [
        {"type": "tconstruct:fixed", "prefix": "tconstruct:plate", "color": "FFFFFF"},
        {"type": "tconstruct:material", "prefix": "tconstruct:plate", "index": 0}
      ]}""");
    assertThat(model.layers()).hasSize(2);
    assertThat(model.layers().get(0)).isInstanceOf(FixedArmorTextureSupplier.class);
    assertThat(model.layers().get(1)).isInstanceOf(MaterialArmorTextureSupplier.Material.class);
  }

  @Test
  void model_roundTrips() {
    // datagen writes these files through the same loadable, so the format has to survive a write/read pair. Note
    // this compares the serialized form rather than the objects: a supplier caches TintedArmorTexture instances
    // built from the texture validator, which has nothing loaded in a headless test, and does not define equals.
    String json = """
      {"layers": [
        {"type": "tconstruct:fixed", "prefix": "tconstruct:plate", "suffix": "_gold", "color": "80FF40", "luminosity": 5},
        {"type": "tconstruct:persistent_data", "prefix": "tconstruct:plate", "material_key": "tconstruct:embellishment"}
      ]}""";
    ArmorModel parsed = parse(json);
    JsonElement written = ArmorModel.LOADABLE.serialize(parsed);
    assertThat(ArmorModel.LOADABLE.serialize(parse(written.toString()))).isEqualTo(written);
  }

  @Test
  void nestedSuppliers_roundTrip() {
    // first_present and material_has_fallback both nest another supplier, which is the part of the format most
    // likely to break silently: a nested loader that failed to write its type would read back as something else
    String json = """
      {"layers": [{"type": "tconstruct:first_present", "options": [
        {"type": "tconstruct:material_has_fallback", "index": 0, "fallback": "wood", "apply": {"type": "tconstruct:fixed", "prefix": "tconstruct:wood"}},
        {"type": "tconstruct:fixed", "prefix": "tconstruct:plate"}
      ]}]}""";
    ArmorModel parsed = parse(json);
    assertThat(parsed.layers()).hasSize(1);
    assertThat(parsed.layers().get(0)).isInstanceOf(FirstArmorTextureSupplier.class);
    assertThat(((FirstArmorTextureSupplier)parsed.layers().get(0)).options()).hasSize(2);
    JsonElement written = ArmorModel.LOADABLE.serialize(parsed);
    assertThat(ArmorModel.LOADABLE.serialize(parse(written.toString()))).isEqualTo(written);
  }

  @Test
  void emptyLayers_isAnError() {
    // the layers field asks for at least one entry: a model with none renders nothing, which as a silent success
    // would look exactly like a texture path typo
    assertThatThrownBy(() -> parse("""
      {"layers": []}""")).isInstanceOf(JsonSyntaxException.class);
  }

  @Test
  void emptyModel_hasNoLayers() {
    assertThat(ArmorModel.EMPTY.layers()).isEmpty();
  }
}
