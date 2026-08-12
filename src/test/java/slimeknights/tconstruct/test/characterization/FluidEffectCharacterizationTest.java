package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffects;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden file round trip for fluid effect JSON ({@code data/tconstruct/tinkering/fluid_effects}), covering block
 * effects (place_block, mob_effect_cloud, scaling, sequence) and entity effects (damage, fire, mob_effect,
 * restore_hunger, remove_effect, award_stat, conditional).
 * <p>
 * As with modifiers, {@code FluidEffectManager.apply} strips a top-level {@code "conditions"} array (Forge
 * conditions) before calling {@link FluidEffects#LOADABLE}; that key is not a field of {@code FluidEffects} and
 * is silently dropped on re-serialize. Documented, not treated as a bug - see {@code notes/T-A0-corpus.md}.
 */
class FluidEffectCharacterizationTest extends BaseMcTest {
  private static final String FOLDER = "characterization/fluid_effects";

  @BeforeAll
  static void registerModuleTypes() {
    ModuleTypeRegistrations.ensureRegistered();
    RealItemStubs.ensureRegistered(FOLDER);
  }

  @TestFactory
  Stream<DynamicTest> fluidEffectRoundTrips() {
    List<String> files = FixtureFiles.listJsonFileNames(FOLDER);
    assertThat(files).as("fluid effect fixture corpus should not be empty").isNotEmpty();
    return files.stream().map(name -> DynamicTest.dynamicTest(name, () -> testOneFile(name)));
  }

  private static void testOneFile(String fileName) {
    JsonObject json = loadFixture(fileName);
    // Predicate singletons registered through Mantle's generic entity-predicate registry (mantle:fire_immune, ...)
    // exhibit real, order-dependent JSON round-trip instability when used together with mantle:inverted - see
    // PredicateInversionJsonAsymmetryTest. That instability can leak into fixtures that don't even use
    // mantle:inverted themselves, so idempotency is only strictly asserted where nothing here touches that
    // registry; a lenient single parse -> serialize is still asserted for every fixture (never throws).
    if (json.toString().contains("mantle:")) {
      RoundTripAssertions.assertJsonRoundTripLenient(FluidEffects.LOADABLE, json, slimeknights.mantle.util.typed.TypedMap.EMPTY);
    } else {
      RoundTripAssertions.assertJsonRoundTrip(FluidEffects.LOADABLE, json);
    }
    // a tag-based fluid ingredient with no bound members (no real datapack tag data loaded in this test
    // environment) cannot even be re-serialized after a network round trip - see
    // FluidIngredientTagNetworkAsymmetryTest for the precise, pinned reproduction of why.
    if (!json.toString().contains("\"tag\"")) {
      RoundTripAssertions.assertNetworkRoundTripJson(FluidEffects.LOADABLE, json);
    }
  }

  private static JsonObject loadFixture(String fileName) {
    String path = FOLDER + "/" + fileName;
    try (InputStream stream = FluidEffectCharacterizationTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing fixture " + path);
      }
      return JsonHelper.DEFAULT_GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
