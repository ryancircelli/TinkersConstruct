package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.util.typed.TypedMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic characterization helpers for round tripping Mantle {@link Loadable} backed objects (recipes,
 * modifiers, material stats, tool definitions, fluid effects, ...) through JSON and through the network.
 * <p>
 * These do not assert that the re-serialized JSON matches the original file byte-for-byte (compact/shorthand
 * forms are allowed to normalize into their full form). Instead they assert the round trip is a stable fixed
 * point: once parsed, serializing and re-parsing an object produces an object that serializes identically
 * again. That is the property "characterization" testing cares about: pinning down current behavior without
 * asserting anything about the *authored* JSON shape.
 */
public final class RoundTripAssertions {
  private RoundTripAssertions() {}

  /**
   * Parses {@code rawJson}, serializes the result back to JSON, re-parses that, and asserts the two
   * serialized forms are semantically identical (a Gson {@link JsonElement#equals}, which is order independent
   * for objects). Returns the canonical serialized form of the first parse for further inspection by callers.
   */
  public static <T> JsonElement assertJsonRoundTrip(Loadable<T> loadable, JsonObject rawJson, TypedMap context) {
    T first = loadable.convert(rawJson, "<characterization test>", context);
    JsonElement firstJson = loadable.serialize(first);
    T second = loadable.convert(firstJson, "<characterization test>", context);
    JsonElement secondJson = loadable.serialize(second);
    assertThat(secondJson)
      .as("re-parsing the canonical serialized form should be idempotent")
      .isEqualTo(firstJson);
    return firstJson;
  }

  /** Same as {@link #assertJsonRoundTrip(Loadable, JsonObject, TypedMap)} but with an empty context. */
  public static <T> JsonElement assertJsonRoundTrip(Loadable<T> loadable, JsonObject rawJson) {
    return assertJsonRoundTrip(loadable, rawJson, TypedMap.EMPTY);
  }

  /**
   * Weaker variant of {@link #assertJsonRoundTrip(Loadable, JsonObject, TypedMap)}: only asserts that parsing
   * {@code rawJson} and serializing the result succeeds without throwing (a single parse -> serialize, no
   * re-parse). Use this instead of the strict form for corpora with a known, separately-pinned re-parse
   * instability (see e.g. {@code PredicateInversionJsonAsymmetryTest}), where asserting exact idempotency would
   * just re-report that same already-documented issue on every affected fixture.
   */
  public static <T> JsonElement assertJsonRoundTripLenient(Loadable<T> loadable, JsonObject rawJson, TypedMap context) {
    T first = loadable.convert(rawJson, "<characterization test>", context);
    return loadable.serialize(first);
  }

  /**
   * Parses {@code rawJson}, then round trips the resulting object through a {@link FriendlyByteBuf}, and
   * asserts the decoded object serializes identically to the original parse. Also asserts decode consumes
   * exactly the bytes encode wrote (no under/over-read).
   */
  public static <T> void assertNetworkRoundTripJson(Loadable<T> loadable, JsonObject rawJson, TypedMap context) {
    T original = loadable.convert(rawJson, "<characterization test>", context);
    assertNetworkRoundTrip(loadable, original, context);
  }

  /** Same as {@link #assertNetworkRoundTripJson(Loadable, JsonObject, TypedMap)} but with an empty context. */
  public static <T> void assertNetworkRoundTripJson(Loadable<T> loadable, JsonObject rawJson) {
    assertNetworkRoundTripJson(loadable, rawJson, TypedMap.EMPTY);
  }

  /** Network round trip starting from an already parsed object. */
  public static <T> void assertNetworkRoundTrip(Loadable<T> loadable, T original, TypedMap context) {
    JsonElement originalJson = loadable.serialize(original);
    // Loadable#encode/#decode want a RegistryFriendlyByteBuf now (M4 SS1); T5's pattern for a registry access
    // with no live game to draw one from.
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    loadable.encode(buffer, original);
    int written = buffer.readableBytes();
    T decoded = loadable.decode(buffer, context);
    JsonElement decodedJson = loadable.serialize(decoded);
    assertThat(decodedJson)
      .as("network round trip should preserve all loadable data")
      .isEqualTo(originalJson);
    assertThat(written - buffer.readableBytes())
      .as("decode should consume exactly the bytes written by encode")
      .isEqualTo(written);
  }
}
