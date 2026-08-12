package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.library.materials.MaterialRegistryExtension;
import slimeknights.tconstruct.library.materials.RandomMaterial;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionData;
import slimeknights.tconstruct.library.tools.definition.module.ToolModule;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization of a real, high-impact bug affecting most mining tools' definitions: {@code IsEffectiveModule}
 * declares its {@code predicate} field with {@code BlockPredicate.LOADER.directField("predicate_type", ...)} -
 * a "direct field" merges the nested {@link slimeknights.mantle.data.predicate.block.BlockPredicate}'s own JSON
 * fields straight into the SAME object as {@code IsEffectiveModule}'s other fields, with no wrapping key. When
 * parsed from a real tool definition (not hand-constructed - reproducing this needs the real parse path, see
 * below), serializing the resulting module back out produces a {@code "type"} of the nested predicate's
 * ({@code "mantle:tag"}) rather than the module's own ({@code "tconstruct:is_effective"}), because both write
 * into the same JSON object and the predicate's write happens to win.
 * <p>
 * Reproduced directly against the real fixture {@code pickaxe.json}: {@code is_effective} is used by nearly
 * every mining tool ({@code pickaxe}, {@code sword}, {@code mattock}, {@code excavator}, ...; 17 of 45 real
 * fixtures in this suite's tool_definitions corpus reference it). Not fixed here per this PR's
 * characterization-only scope - the bug is in TConstruct's own {@code IsEffectiveModule} field declaration
 * (using {@code directField} instead of {@code tryDirectField}, which exists specifically to detect/avoid this
 * kind of key collision), not in Mantle. {@code ToolDefinitionCharacterizationTest} points here for any fixture
 * using {@code tconstruct:is_effective}.
 */
@ExtendWith(MaterialRegistryExtension.class)
class IsEffectiveModuleSerializationAsymmetryTest extends BaseMcTest {
  private static final String FIXTURE = "characterization/tool_definitions/pickaxe.json";

  @BeforeAll
  static void registerModuleTypes() {
    ToolModuleRegistrations.ensureRegistered();
    ModuleTypeRegistrations.ensureRegistered();
    RealItemStubs.ensureRegistered("characterization/tool_definitions");
    RandomMaterial.init();
    slimeknights.tconstruct.library.tools.SlotType.init();
  }

  @Test
  void isEffectiveModule_parsedFromRealJson_loses_itsOwnTypeKeyOnSerialize() throws Exception {
    JsonObject raw;
    try (InputStream stream = IsEffectiveModuleSerializationAsymmetryTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
      assertThat(stream).as("fixture must exist: " + FIXTURE).isNotNull();
      raw = JsonHelper.DEFAULT_GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    }

    ToolDefinitionData parsed = ToolDefinitionData.LOADABLE.deserialize(raw);
    JsonElement serialized = ToolDefinitionData.LOADABLE.serialize(parsed);

    JsonElement isEffectiveEntry = null;
    for (JsonElement module : serialized.getAsJsonObject().getAsJsonArray("modules")) {
      String type = module.getAsJsonObject().get("type").getAsString();
      if (!type.equals("tconstruct:is_effective")) {
        // BUG (pinned, not fixed here): the is_effective module's own type key is gone - a *different* type
        // string appears instead (the nested block predicate's, e.g. "mantle:tag"), for what was originally
        // an is_effective module
        isEffectiveEntry = module;
      }
    }
    assertThat(isEffectiveEntry)
      .as("re-serializing pickaxe.json's is_effective module should have produced an entry no longer " +
          "identifiable as tconstruct:is_effective - if this fails, the bug may have been fixed upstream")
      .isNotNull();

    // that malformed entry can't be re-parsed as a ToolModule at all
    JsonObject asModuleEntry = isEffectiveEntry.getAsJsonObject();
    assertThat(asModuleEntry.get("type").getAsString()).isNotEqualTo("tconstruct:is_effective");
  }
}
