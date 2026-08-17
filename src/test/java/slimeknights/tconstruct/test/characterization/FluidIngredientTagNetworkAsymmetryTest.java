package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.FluidTags;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization of a sharper version of the same class of bug as {@link IngredientTagNetworkAsymmetryTest},
 * specific to Mantle's {@link FluidIngredient}: a tag-based fluid ingredient with zero currently-bound members
 * does not merely lose information over the network - re-serializing the decoded result throws a
 * {@code RuntimeException}, making the round trip genuinely unrecoverable rather than just lossy.
 * <p>
 * Root cause, traced through {@code FluidIngredient}: the network form encodes a tag ingredient as its resolved
 * {@code FluidStack} list ({@code getAllFluids()}); with zero bound members that list is empty. On decode,
 * {@code FluidIngredient.of(List)} only special-cases a list of size 1 (returning that single element directly);
 * for any other size - including zero - it wraps the result in a {@code Compound}. But {@code Compound}'s own
 * JSON writer requires at least 2 entries ({@code FluidIngredient.COMPOUND} is built with {@code .list(2)}), so
 * serializing a zero-element {@code Compound} throws {@code RuntimeException: Collection must have at least 2
 * elements} instead of producing valid JSON.
 * <p>
 * This is why {@code RecipeCharacterizationTest} skips the network round-trip assertion for any fixture
 * containing a tag-based fluid ingredient (e.g. {@code "fluid": {"tag": "forge:molten_gold", "amount": 10}}).
 * Not a TConstruct-recipe bug and not fixed here - it lives in Mantle's {@code FluidIngredient}, out of scope for
 * this test-only PR; pinned so the exact failure mode is visible and citable for later work.
 */
class FluidIngredientTagNetworkAsymmetryTest extends BaseMcTest {
  @Test
  void tagFluidIngredient_survivesJsonRoundTrip() {
    JsonObject json = new JsonObject();
    JsonObject fluidObj = new JsonObject();
    fluidObj.addProperty("tag", "minecraft:water"); // FluidTags.WATER, a real vanilla tag id
    fluidObj.addProperty("amount", 100);

    FluidIngredient parsed = FluidIngredient.LOADABLE.convert(fluidObj, "fluid", TypedMap.EMPTY);
    com.google.gson.JsonElement reserialized = FluidIngredient.LOADABLE.serialize(parsed);
    assertThat(reserialized.getAsJsonObject().has("tag")).as("JSON round trip preserves the tag reference").isTrue();
  }

  @Test
  void tagFluidIngredient_withNoBoundMembers_becomesUnserializableAfterNetworkRoundTrip() {
    JsonObject fluidObj = new JsonObject();
    // FluidTags.WATER has no bound members in this headless test environment (no real datapack tag data loaded)
    fluidObj.addProperty("tag", FluidTags.WATER.location().toString());
    fluidObj.addProperty("amount", 100);

    FluidIngredient parsed = FluidIngredient.LOADABLE.convert(fluidObj, "fluid", TypedMap.EMPTY);

    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    FluidIngredient.LOADABLE.encode(buffer, parsed);
    FluidIngredient decoded = FluidIngredient.LOADABLE.decode(buffer, TypedMap.EMPTY);

    // the decoded ingredient cannot even be re-serialized to JSON - this is a genuine dead end, not just data loss
    assertThatThrownBy(() -> FluidIngredient.LOADABLE.serialize(decoded))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("must have at least 2 elements");
  }
}
