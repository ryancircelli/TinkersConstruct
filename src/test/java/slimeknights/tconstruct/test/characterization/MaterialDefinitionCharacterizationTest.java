package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.neoforge.common.crafting.CraftingHelper;
import net.neoforged.neoforge.common.conditions.AndCondition;
import net.neoforged.neoforge.common.conditions.FalseCondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.conditions.NotCondition;
import net.neoforged.neoforge.common.conditions.OrCondition;
import net.neoforged.neoforge.common.conditions.TrueCondition;
import org.junit.jupiter.api.BeforeAll;
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

  @BeforeAll
  static void registerConditions() {
    register(TrueCondition.Serializer.INSTANCE);
    register(FalseCondition.Serializer.INSTANCE);
    // forge's own built-in compound conditions are normally registered by ForgeMod's mod construction, which
    // never runs in these headless unit tests (see BaseMcTest) - register them the same way ForgeMod does
    register(AndCondition.Serializer.INSTANCE);
    register(OrCondition.Serializer.INSTANCE);
    register(NotCondition.Serializer.INSTANCE);
    register(ModLoadedCondition.Serializer.INSTANCE);
    register(slimeknights.tconstruct.common.json.ConfigEnabledCondition.SERIALIZER);
    register(slimeknights.mantle.recipe.condition.TagFilledCondition.SERIALIZER);
  }

  private static void register(net.minecraftforge.common.crafting.conditions.IConditionSerializer<?> serializer) {
    try {
      CraftingHelper.register(serializer);
    } catch (Exception ignored) {
      // already registered - fine
    }
  }

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
