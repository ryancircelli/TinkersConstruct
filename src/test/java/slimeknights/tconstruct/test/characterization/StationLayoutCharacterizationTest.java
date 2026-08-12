package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.VanillaIngredientSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayout;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayoutLoader;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden file round trip for every real station slot layout JSON
 * ({@code data/tconstruct/tinkering/station_layouts}). {@link StationSlotLayout} is a plain reflective-Gson
 * class (see {@link StationSlotLayoutLoader#GSON}), not a Mantle {@code Loadable}, so the JSON round trip here
 * uses {@code GSON.fromJson}/{@code toJsonTree} directly (matching the existing {@code StationSlotLayoutLoaderTest}
 * pattern) rather than {@link RoundTripAssertions}; the network round trip uses {@link StationSlotLayout#write}/
 * {@link StationSlotLayout#read} directly (the same methods {@code UpdateTinkerSlotLayoutsPacket} delegates to).
 */
class StationLayoutCharacterizationTest extends BaseMcTest {
  private static final String FOLDER = "characterization/station_layouts";

  @BeforeAll
  static void registerSerializers() {
    try {
      CraftingHelper.register(ResourceLocation.fromNamespaceAndPath("minecraft", "item"), VanillaIngredientSerializer.INSTANCE);
    } catch (Exception ignored) {
      // already registered - fine
    }
    RealItemStubs.ensureRegistered(FOLDER);
  }

  @TestFactory
  Stream<DynamicTest> stationLayoutRoundTrips() {
    List<String> files = FixtureFiles.listJsonFileNames(FOLDER);
    assertThat(files).as("station layout fixture corpus should not be empty").isNotEmpty();
    return files.stream().map(name -> DynamicTest.dynamicTest(name, () -> testOneFile(name)));
  }

  private static void testOneFile(String fileName) {
    JsonObject raw = loadFixture(fileName);
    // "name" is transient (not part of the JSON at all - StationSlotLayoutLoader#apply sets it from the file's
    // resource location after parsing) and GSON's reflective deserialization bypasses field initializers, so it
    // comes back null rather than defaulting; set it explicitly via reflection (the setter is package-private),
    // exactly like the real loader does.
    ResourceLocation name = ResourceLocation.fromNamespaceAndPath("tconstruct", fileName.replace(".json", ""));

    // JSON round trip: parse -> serialize -> re-parse -> serialize, assert idempotent
    StationSlotLayout first = StationSlotLayoutLoader.GSON.fromJson(raw, StationSlotLayout.class);
    setName(first, name);
    JsonElement firstJson = StationSlotLayoutLoader.GSON.toJsonTree(first);
    StationSlotLayout second = StationSlotLayoutLoader.GSON.fromJson(firstJson, StationSlotLayout.class);
    setName(second, name);
    JsonElement secondJson = StationSlotLayoutLoader.GSON.toJsonTree(second);
    assertThat(secondJson).as("re-parsing the canonical serialized form should be idempotent").isEqualTo(firstJson);

    // Network round trip: write/read directly (what UpdateTinkerSlotLayoutsPacket delegates to per-layout).
    // Note a real asymmetry: a layout with no explicit tool slot (e.g. "main" layouts like arrow.json) keeps
    // tool_slot genuinely null for JSON purposes (omitted from the GSON output entirely), but the network form
    // always writes/reads a concrete LayoutSlot (getToolSlot() defaults null to LayoutSlot.EMPTY before writing) -
    // so decodedJson will never equal firstJson for such layouts. Compare against a second network round trip
    // instead (stability from the network form onward) rather than the pre-network JSON form directly.
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    first.write(buffer);
    int written = buffer.readableBytes();
    StationSlotLayout decoded = StationSlotLayout.read(buffer);
    assertThat(written - buffer.readableBytes()).as("decode should consume exactly the bytes written by encode").isEqualTo(written);
    JsonElement decodedJson = StationSlotLayoutLoader.GSON.toJsonTree(decoded);

    FriendlyByteBuf buffer2 = new FriendlyByteBuf(Unpooled.buffer());
    decoded.write(buffer2);
    StationSlotLayout decodedTwice = StationSlotLayout.read(buffer2);
    JsonElement decodedTwiceJson = StationSlotLayoutLoader.GSON.toJsonTree(decodedTwice);
    assertThat(decodedTwiceJson).as("network round trip should be stable from the first decode onward").isEqualTo(decodedJson);
  }

  /** {@code StationSlotLayout#setName} is package-private; reach it via reflection to mimic what the real loader does after GSON parsing. */
  private static void setName(StationSlotLayout layout, ResourceLocation name) {
    try {
      java.lang.reflect.Field field = StationSlotLayout.class.getDeclaredField("name");
      field.setAccessible(true);
      field.set(layout, name);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static JsonObject loadFixture(String fileName) {
    String path = FOLDER + "/" + fileName;
    try (InputStream stream = StationLayoutCharacterizationTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing fixture " + path);
      }
      return StationSlotLayoutLoader.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
