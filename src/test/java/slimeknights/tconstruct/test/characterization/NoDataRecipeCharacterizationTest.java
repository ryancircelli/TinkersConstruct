package slimeknights.tconstruct.test.characterization;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.SimpleRecipeSerializer;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.tables.recipe.CraftingTableRepairKitRecipe;
import slimeknights.tconstruct.tables.recipe.TinkerStationRepairRecipe;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.tools.recipe.ArmorDyeingRecipe;
import slimeknights.tconstruct.tools.recipe.BannerModifierRecipe;
import slimeknights.tconstruct.tools.recipe.ArmorTrimRecipe;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization of a genuine upstream asymmetry: {@code tinker_station_repair}, {@code crafting_table_repair},
 * {@code armor_dyeing_modifier}, {@code banner_modifier} and {@code armor_trim_modifier} all use Mantle's
 * {@link SimpleRecipeSerializer}, which discards its JSON body entirely - {@code fromJson} ignores the passed
 * {@code JsonObject} and just constructs {@code T} from the id, and {@code toNetwork} writes nothing. These
 * recipes carry no data beyond their registry id; the fixture files for them are just {@code {"type": "..."}}.
 * <p>
 * This is pinned down here rather than "fixed", per this PR's characterization-only scope: it documents that a
 * JSON round trip is meaningless for these recipe types (there is nothing to serialize back), while the
 * id-preservation and network no-op behavior are still verified.
 */
class NoDataRecipeCharacterizationTest extends BaseMcTest {
  private static final ResourceLocation ID = TConstruct.getResource("characterization_test/no_data_recipe");

  @Test
  void tinkerStationRepair_discardsJsonBody_hasNoNetworkPayload() {
    assertNoDataRoundTrip(new SimpleRecipeSerializer<>(TinkerStationRepairRecipe::new));
  }

  @Test
  void craftingTableRepairKit_discardsJsonBody_hasNoNetworkPayload() {
    assertNoDataRoundTrip(new SimpleRecipeSerializer<>(CraftingTableRepairKitRecipe::new));
  }

  @Test
  void armorDyeing_discardsJsonBody_hasNoNetworkPayload() {
    assertNoDataRoundTrip(new SimpleRecipeSerializer<>(ArmorDyeingRecipe::new));
  }

  @Test
  void bannerModifier_discardsJsonBody_hasNoNetworkPayload() {
    assertNoDataRoundTrip(new SimpleRecipeSerializer<>(BannerModifierRecipe::new));
  }

  @Test
  void armorTrim_discardsJsonBody_hasNoNetworkPayload() {
    assertNoDataRoundTrip(new SimpleRecipeSerializer<>(ArmorTrimRecipe::new));
  }

  private static <T extends Recipe<?>> void assertNoDataRoundTrip(RecipeSerializer<T> serializer) {
    // fromJson ignores the JSON entirely - even garbage/empty JSON produces a valid recipe carrying just the id
    T recipe = serializer.fromJson(ID, new com.google.gson.JsonObject());
    assertThat(recipe.getId()).isEqualTo(ID);

    // toNetwork is a documented no-op: nothing is written, so decode must not attempt to read anything either
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    serializer.toNetwork(buffer, recipe);
    assertThat(buffer.readableBytes()).as("toNetwork should write nothing for id-only recipes").isZero();

    T decoded = serializer.fromNetwork(ID, buffer);
    assertThat(decoded.getId()).isEqualTo(ID);
  }
}
