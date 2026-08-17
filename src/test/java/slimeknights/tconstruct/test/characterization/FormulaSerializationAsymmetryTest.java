package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization of a real, reproducible bug in the postfix math "formula" system shared by several modifier
 * modules ({@code tconstruct:adjust_damage}, {@code tconstruct:conditional_melee_damage},
 * {@code tconstruct:conditional_mining_speed}, ...): a formula referencing built-in variables by name (e.g.
 * {@code "$max_health"}) parses successfully, but re-serializing the parsed module throws
 * {@code ArrayIndexOutOfBoundsException} from {@code PushVariableOperation.serialize} - the formula can be read
 * but not written back out. This is not an artifact of this test's headless environment (no registry/tag data
 * involved at all, purely postfix-token round tripping) - it reproduces with real shipped modifier fixtures:
 * {@code dragonheart}, {@code jagged}, and {@code knockback}.
 * <p>
 * Not fixed here per this PR's characterization-only scope; the bug lives in
 * {@code slimeknights.tconstruct.library.json.math.PushVariableOperation}, not in any single modifier or module.
 * {@code ModifierCharacterizationTest} skips these three fixtures and points here instead.
 */
class FormulaSerializationAsymmetryTest extends BaseMcTest {
  @BeforeAll
  static void registerModuleTypes() {
    ModuleTypeRegistrations.ensureRegistered();
    RealItemStubs.ensureRegistered("characterization/modifiers");
  }

  @ParameterizedTest
  @ValueSource(strings = {"dragonheart.json", "jagged.json", "knockback.json"})
  void formulaReferencingBuiltInVariablesByName_parsesButCannotBeReSerialized(String fileName) throws Exception {
    String path = "characterization/modifiers/" + fileName;
    JsonObject raw;
    try (InputStream stream = FormulaSerializationAsymmetryTest.class.getClassLoader().getResourceAsStream(path)) {
      assertThat(stream).as("fixture must exist: " + path).isNotNull();
      raw = JsonHelper.DEFAULT_GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    }
    ResourceLocation id = TConstruct.getResource("characterization_test/formula_asymmetry/" + fileName.replace(".json", ""));
    TypedMap context = TypedMapBuilder.builder().put(ContextKey.ID, id).build();

    // parsing succeeds without issue
    var parsed = ComposableModifier.LOADER.deserialize(raw, context);

    // BUG (pinned, not fixed here): re-serializing the very same parsed object throws
    assertThatThrownBy(() -> ComposableModifier.LOADER.serialize(parsed))
      .isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }
}
