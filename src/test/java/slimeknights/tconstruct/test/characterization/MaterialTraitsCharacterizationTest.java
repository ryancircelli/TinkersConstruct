package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import slimeknights.tconstruct.library.materials.json.MaterialTraitsJson;
import slimeknights.tconstruct.library.materials.traits.MaterialTraitsManager;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden file round trip for material traits JSON ({@code data/tconstruct/tinkering/materials/traits}), covering
 * files with only {@code default} traits, only {@code perStat} traits, and both. Like material definitions,
 * {@link MaterialTraitsJson} is a plain reflective-Gson POJO round tripped through {@link MaterialTraitsManager#GSON}
 * rather than a Mantle {@code Loadable} - {@code MaterialTraits} itself only exposes NBT/network read+write, not a
 * JSON serializer (see {@code notes/T-A0-corpus.md} for this asymmetry).
 */
class MaterialTraitsCharacterizationTest extends BaseMcTest {
  private static final String FOLDER = "characterization/materials/traits";

  @TestFactory
  Stream<DynamicTest> traitsRoundTrips() {
    List<String> files = FixtureFiles.listJsonFileNames(FOLDER);
    assertThat(files).as("material traits fixture corpus should not be empty").isNotEmpty();
    return files.stream().map(name -> DynamicTest.dynamicTest(name, () -> testOneFile(name)));
  }

  private static void testOneFile(String fileName) {
    JsonObject raw = loadFixture(fileName);
    MaterialTraitsJson first = MaterialTraitsManager.GSON.fromJson(raw, MaterialTraitsJson.class);
    JsonElement firstJson = MaterialTraitsManager.GSON.toJsonTree(first);
    MaterialTraitsJson second = MaterialTraitsManager.GSON.fromJson(firstJson, MaterialTraitsJson.class);
    JsonElement secondJson = MaterialTraitsManager.GSON.toJsonTree(second);
    assertThat(secondJson).as("re-parsing the canonical serialized form should be idempotent").isEqualTo(firstJson);
  }

  private static JsonObject loadFixture(String fileName) {
    String path = FOLDER + "/" + fileName;
    try (InputStream stream = MaterialTraitsCharacterizationTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing fixture " + path);
      }
      return MaterialTraitsManager.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
