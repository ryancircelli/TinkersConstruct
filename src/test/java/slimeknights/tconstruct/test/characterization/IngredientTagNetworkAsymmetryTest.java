package slimeknights.tconstruct.test.characterization;

import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization of a real, current vanilla asymmetry: a tag-based {@link Ingredient} preserves its tag
 * reference through JSON but NOT through the network. The network form always writes the ingredient's
 * <em>currently resolved</em> concrete item list, never the tag reference itself - so after a network round trip,
 * a tag ingredient becomes a plain list of whatever items matched the tag at encode time. If the tag has zero
 * members (unbound, as in this headless test environment with no real datapack tag data loaded - or genuinely
 * empty in a real game), vanilla substitutes a single {@code minecraft:barrier} placeholder item stack rather
 * than an empty list.
 * <p>
 * This is why {@code RecipeCharacterizationTest} skips the network round-trip assertion for any fixture
 * containing a tag-based ingredient: it is not "different" from the JSON round trip, it is a known, permanent
 * loss of information inherent to how vanilla synchronizes ingredients to the client. Not a TConstruct bug, not
 * something this PR fixes - pinned here so it is visible and citable.
 * <p>
 * The mechanism is spelled differently in 1.21 and the asymmetry is unchanged. {@code Ingredient#toJson},
 * {@code #toNetwork} and {@code #fromNetwork} are all gone; serialization is {@link Ingredient#CODEC} and
 * {@link Ingredient#CONTENTS_STREAM_CODEC}. The stream codec's name is the whole finding: it sends the
 * ingredient's <em>contents</em>, and 1.21 did not take the opportunity to sync the tag key instead. The buffer
 * is a {@link RegistryFriendlyByteBuf} now, because a stack in the resolved list may carry data components that
 * name a registry entry.
 */
class IngredientTagNetworkAsymmetryTest extends BaseMcTest {
  @Test
  void tagIngredient_survivesJsonRoundTrip_butNotNetworkRoundTrip() {
    Ingredient tagIngredient = Ingredient.of(ItemTags.PLANKS);

    // JSON: the tag reference itself survives
    com.google.gson.JsonElement json = Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, tagIngredient).getOrThrow();
    assertThat(json.getAsJsonObject().has("tag")).as("Ingredient.CODEC preserves the tag reference").isTrue();

    // network: the tag reference is lost, replaced by whatever concretely matched at encode time
    RegistryFriendlyByteBuf buffer = networkBuffer();
    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, tagIngredient);
    Ingredient decoded = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
    com.google.gson.JsonElement decodedJson = Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
    assertThat(decodedJson.getAsJsonObject().has("tag"))
      .as("the network round trip does NOT preserve the tag reference - CONTENTS_STREAM_CODEC always sends resolved items")
      .isFalse();
  }

  @Test
  void tagIngredient_withNoBoundMembers_becomesBarrierPlaceholderOverNetwork() {
    // ItemTags.PLANKS is never bound to any items in this headless test environment (no real datapack tag data
    // is loaded - see BaseMcTest). Even Ingredient#getItems() itself already substitutes a single
    // "minecraft:barrier" sentinel item for a fully-unmatched tag rather than resolving to zero items - this is
    // vanilla behavior independent of the network round trip, and it's what the stream codec ends up sending.
    Ingredient tagIngredient = Ingredient.of(ItemTags.PLANKS);
    assertThat(tagIngredient.getItems()).hasSize(1);
    assertThat(tagIngredient.getItems()[0].getItem()).isSameAs(net.minecraft.world.item.Items.BARRIER);

    RegistryFriendlyByteBuf buffer = networkBuffer();
    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, tagIngredient);
    Ingredient decoded = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);

    // the barrier placeholder survives the network round trip since it is now just a concrete, resolved item
    assertThat(decoded.getItems()).hasSize(1);
    assertThat(decoded.getItems()[0].getItem()).isSameAs(net.minecraft.world.item.Items.BARRIER);
  }
}
