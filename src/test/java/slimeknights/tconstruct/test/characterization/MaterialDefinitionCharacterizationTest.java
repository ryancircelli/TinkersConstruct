package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import slimeknights.tconstruct.library.materials.definition.MaterialManager;
import slimeknights.tconstruct.library.materials.json.MaterialJson;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden file round trip for material definition JSON ({@code data/tconstruct/tinkering/materials/definition}).
 * {@link MaterialJson} is a plain reflective-Gson POJO (not a Mantle {@link slimeknights.mantle.data.loadable.Loadable}),
 * parsed and serialized with {@link MaterialManager#GSON} - the same Gson instance production uses. The round
 * trip is: raw JSON -> {@link MaterialJson} -> re-serialize -> re-parse -> re-serialize, asserting the last two
 * serialized forms are identical.
 */
class MaterialDefinitionCharacterizationTest extends BaseMcTest {
  private static final String FOLDER = "characterization/materials/definition";

  // Nothing to register. A condition is a MapCodec in the neoforge:condition_codecs registry in 1.21 (T4 3.2), and
  // ICondition.CODEC dispatches on the same "type" key through it, so every vanilla and NeoForge condition these
  // fixtures name is present as soon as NeoForge is on the classpath. Tinkers' and Mantle's own conditions are
  // registered by their owning slices.

  @TestFactory
  Stream<DynamicTest> definitionRoundTrips() {
    List<String> files = FixtureFiles.listJsonFileNames(FOLDER);
    assertThat(files).as("material definition fixture corpus should not be empty").isNotEmpty();
    return files.stream().map(name -> DynamicTest.dynamicTest(name, () -> testOneFile(name)));
  }

  private static void testOneFile(String fileName) {
    JsonObject raw = loadFixture(fileName);
    MaterialJson first = MaterialManager.GSON.fromJson(raw, MaterialJson.class);
    JsonElement firstJson = MaterialManager.GSON.toJsonTree(first);
    MaterialJson second = MaterialManager.GSON.fromJson(firstJson, MaterialJson.class);
    JsonElement secondJson = MaterialManager.GSON.toJsonTree(second);
    assertThat(secondJson).as("re-parsing the canonical serialized form should be idempotent").isEqualTo(firstJson);
  }

  private static JsonObject loadFixture(String fileName) {
    String path = FOLDER + "/" + fileName;
    try (InputStream stream = MaterialDefinitionCharacterizationTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing fixture " + path);
      }
      return MaterialManager.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
