package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.mantle.util.typed.TypedMapBuilder;
import slimeknights.tconstruct.library.materials.stats.MaterialStatType;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden file round trip for material stats JSON (one file per material, containing one entry per stat type
 * under a {@code "stats"} object). Drives each entry through the real stat type's own loadable
 * ({@link MaterialStatTypeRegistry}), covering every real stat type at least once (head, handle, binding, limb,
 * grip, bowstring, all five armor plating pieces, skull, slime, and the three repair variants).
 */
class MaterialStatsCharacterizationTest extends BaseMcTest {
  private static final String FOLDER = "characterization/materials/stats";

  @TestFactory
  Stream<DynamicTest> statsRoundTrips() {
    List<String> files = FixtureFiles.listJsonFileNames(FOLDER);
    assertThat(files).as("material stats fixture corpus should not be empty").isNotEmpty();
    return files.stream().map(name -> DynamicTest.dynamicTest(name, () -> testOneFile(name)));
  }

  /** Confirms every real stat type is exercised by at least one fixture. */
  @org.junit.jupiter.api.Test
  void everyRealStatTypeIsCovered() {
    List<String> files = FixtureFiles.listJsonFileNames(FOLDER);
    java.util.Set<String> covered = new java.util.HashSet<>();
    for (String name : files) {
      JsonObject json = loadFixture(name);
      JsonObject stats = json.getAsJsonObject("stats");
      if (stats != null) {
        covered.addAll(stats.keySet());
      }
    }
    assertThat(covered).containsAll(MaterialStatTypeRegistry.TYPES.keySet());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void testOneFile(String fileName) {
    JsonObject json = loadFixture(fileName);
    JsonObject stats = json.getAsJsonObject("stats");
    assertThat(stats).as("fixture should have a stats object: " + fileName).isNotNull();
    for (Map.Entry<String,JsonElement> entry : stats.entrySet()) {
      MaterialStatType type = MaterialStatTypeRegistry.TYPES.get(entry.getKey());
      if (type == null) {
        // stat type not mirrored in our test registry (shouldn't happen for real generated files, but don't hard fail unrelated fixtures)
        continue;
      }
      RecordLoadable loader = type.getLoadable();
      TypedMap context = TypedMapBuilder.builder().put(MaterialStatType.CONTEXT_KEY, type).build();
      JsonObject statJson = entry.getValue().getAsJsonObject();
      RoundTripAssertions.assertJsonRoundTrip(loader, statJson, context);
      RoundTripAssertions.assertNetworkRoundTripJson(loader, statJson, context);
    }
  }

  private static JsonObject loadFixture(String fileName) {
    String path = FOLDER + "/" + fileName;
    try (InputStream stream = MaterialStatsCharacterizationTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing fixture " + path);
      }
      return JsonHelper.DEFAULT_GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
