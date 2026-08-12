package slimeknights.tconstruct.test.characterization;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.helper.SimpleRecipeSerializer;
import slimeknights.tconstruct.tables.recipe.CraftingTableRepairKitRecipe;
import slimeknights.tconstruct.tables.recipe.TinkerStationRepairRecipe;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.tools.recipe.ArmorDyeingRecipe;
import slimeknights.tconstruct.tools.recipe.ArmorTrimRecipe;
import slimeknights.tconstruct.tools.recipe.BannerModifierRecipe;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization of a genuine upstream asymmetry: {@code tinker_station_repair}, {@code crafting_table_repair},
 * {@code armor_dyeing_modifier}, {@code banner_modifier} and {@code armor_trim_modifier} all use Mantle's
 * {@link SimpleRecipeSerializer}, whose codec discards its JSON body entirely and whose stream codec writes
 * nothing. These recipes carry no data at all; the fixture files for them are just {@code {"type": "..."}}.
 * <p>
 * This is pinned down here rather than "fixed", per the characterization-only scope of the corpus: it documents
 * that a JSON round trip is meaningless for these recipe types, while the network no-op behavior is verified.
 * <p>
 * 1.21 removed the one thing these recipes did carry. In 1.20 the serializer's only field was the recipe ID, so
 * the test could at least assert the ID survived both directions; the ID lives on {@code RecipeHolder} now and a
 * recipe never sees it, which is why {@link SimpleRecipeSerializer} takes a {@code Supplier} rather than a
 * {@code Function<ResourceLocation,T>} (M8 section 8). What is left to assert is that both directions succeed and
 * that the network form is genuinely empty.
 */
class NoDataRecipeCharacterizationTest extends BaseMcTest {
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
    // the codec ignores the JSON entirely - even an empty object produces a valid recipe
    T recipe = serializer.codec().codec().parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
    assertThat(recipe).as("codec should build a recipe from an empty object").isNotNull();

    // and it writes nothing back, since there is nothing to write
    assertThat(serializer.codec().codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow())
      .as("codec should write an empty object")
      .isEqualTo(new JsonObject());

    // the stream codec is a documented no-op: nothing is written, so decode must not attempt to read anything either
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    serializer.streamCodec().encode(buffer, recipe);
    assertThat(buffer.readableBytes()).as("streamCodec should write nothing for a data free recipe").isZero();

    T decoded = serializer.streamCodec().decode(buffer);
    assertThat(decoded).as("streamCodec should build a recipe from nothing").isNotNull();
  }
}
