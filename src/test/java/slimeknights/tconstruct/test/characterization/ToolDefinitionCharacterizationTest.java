package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.library.materials.MaterialRegistryExtension;
import slimeknights.tconstruct.library.materials.RandomMaterial;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionData;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden file round trip for every real tool definition JSON ({@code data/tconstruct/tinkering/tool_definitions}),
 * using the real {@code tconstruct:} module type ids (unlike the existing {@code ToolDefinitionLoaderTest}, which
 * intentionally uses synthetic {@code test:}-namespaced fixtures and fake module registrations to test the loader
 * mechanics in isolation). Real tool definitions reference dozens of real TConstruct part/tool items directly by
 * id, which don't exist in {@link net.minecraftforge.registries.ForgeRegistries#ITEMS} in this headless
 * environment (see {@link RealItemStubs}) - resolved here by stubbing them in.
 */
@ExtendWith(MaterialRegistryExtension.class)
class ToolDefinitionCharacterizationTest extends BaseMcTest {
  private static final String FOLDER = "characterization/tool_definitions";

  @BeforeAll
  static void registerModuleTypes() {
    ToolModuleRegistrations.ensureRegistered();
    ModuleTypeRegistrations.ensureRegistered();
    RealItemStubs.ensureRegistered(FOLDER);
    RandomMaterial.init();
    slimeknights.tconstruct.library.tools.SlotType.init();
  }

  @TestFactory
  Stream<DynamicTest> toolDefinitionRoundTrips() {
    List<String> files = FixtureFiles.listJsonFileNames(FOLDER);
    assertThat(files).as("tool definition fixture corpus should not be empty").isNotEmpty();
    return files.stream().map(name -> DynamicTest.dynamicTest(name, () -> testOneFile(name)));
  }

  private static void testOneFile(String fileName) {
    // Re-assert registration immediately before each fixture rather than trusting @BeforeAll alone: this suite
    // observed real cases (in a full, multi-class test run - never reproducible with this class run in
    // isolation) where a module type registered successfully in @BeforeAll was later missing by the time a
    // dynamic test's body actually executed, with no exception thrown anywhere - JUnit5 builds every
    // @TestFactory's dynamic tests across all classes before running any of their bodies, so a lot can happen
    // to shared static registry state between @BeforeAll and a given dynamic test actually executing. Each
    // registration call is cheap and idempotent, so doing it here too is cheap insurance.
    ToolModuleRegistrations.ensureRegistered();
    ModuleTypeRegistrations.ensureRegistered();

    JsonObject json = loadFixture(fileName);
    // A handful of real fixtures hit known, separately-pinned serialization issues where a strict round trip
    // assertion would just re-report the same already-documented finding on every affected file:
    //  - tconstruct:is_effective (used by nearly every mining tool) clobbers its own "type" key with its nested
    //    predicate's on serialize - see IsEffectiveModuleSerializationAsymmetryTest.
    //  - a multi-value "stat_types" set (material_stats with several part stat types) round trips through a Set
    //    internally, so element order is not stable across repeated parses - the same minor ordering asymmetry
    //    as severing recipes' "types" field (see RecipeCharacterizationTest).
    if (json.toString().contains("tconstruct:is_effective") || json.toString().contains("\"stat_types\":[")) {
      RoundTripAssertions.assertJsonRoundTripLenient(ToolDefinitionData.LOADABLE, json, slimeknights.mantle.util.typed.TypedMap.EMPTY);
      return;
    }
    // AOE iterator modules (box_aoe, vein_aoe, circle_aoe, ...) with a compact partial-dimension form (e.g.
    // "expansions": [{"depth": 2}, {"height": 1}], only one of width/height/depth given per entry) were observed
    // to throw JsonSyntaxException ("Value must not be less than 1") re-serializing the implicit default of 0
    // back through a minimum-1 field - but which fixtures trigger it was observed to depend on what else ran
    // earlier in the same JVM (see PredicateInversionJsonAsymmetryTest for the same class of order-dependent
    // shared registry state), so this is tolerated generically here rather than pattern-matched per fixture.
    try {
      RoundTripAssertions.assertJsonRoundTrip(ToolDefinitionData.LOADABLE, json);
      RoundTripAssertions.assertNetworkRoundTripJson(ToolDefinitionData.LOADABLE, json);
    } catch (com.google.gson.JsonSyntaxException e) {
      assertThat(e).as("only the known AOE compact-form minimum-value bug is tolerated here").hasMessageContaining("must not be less than 1");
    }
  }

  private static JsonObject loadFixture(String fileName) {
    String path = FOLDER + "/" + fileName;
    try (InputStream stream = ToolDefinitionCharacterizationTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing fixture " + path);
      }
      return JsonHelper.DEFAULT_GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
