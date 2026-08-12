package slimeknights.tconstruct.test.characterization;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.tables.network.StationTabPacket;
import slimeknights.tconstruct.tables.network.TinkerStationRenamePacket;
import slimeknights.tconstruct.tables.network.TinkerStationSelectionPacket;
import slimeknights.tconstruct.tables.network.UpdateCraftingRecipePacket;
import slimeknights.tconstruct.tables.network.UpdateStationScreenPacket;
import slimeknights.tconstruct.tables.network.UpdateTinkerStationRecipePacket;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Object -> encode -> decode -> object field round trips for every packet registered under {@code tables} in {@code TinkerNetwork}. */
class TablesNetworkPacketsTest extends BaseMcTest {
  @Test
  void stationTabPacket_roundTrips() {
    BlockPos pos = new BlockPos(11, 12, 13);
    StationTabPacket packet = new StationTabPacket(pos);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    StationTabPacket decoded = new StationTabPacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
  }

  @Test
  void tinkerStationRenamePacket_roundTrips() {
    TinkerStationRenamePacket packet = new TinkerStationRenamePacket("My Favorite Hammer");
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    TinkerStationRenamePacket decoded = new TinkerStationRenamePacket(buffer);

    assertThat(decoded).extracting("name").isEqualTo("My Favorite Hammer");
  }

  @Test
  void updateCraftingRecipePacket_roundTrips() {
    BlockPos pos = new BlockPos(1, 1, 1);
    ResourceLocation recipeId = TConstruct.getResource("scorched_anvil_material");
    // only recipe.getId() is ever read by the constructor - a mock stubbing that one method is enough,
    // avoiding needing a live RecipeManager/real CraftingRecipe instance
    CraftingRecipe recipe = mock(CraftingRecipe.class);
    when(recipe.getId()).thenReturn(recipeId);

    UpdateCraftingRecipePacket packet = new UpdateCraftingRecipePacket(pos, recipe);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    UpdateCraftingRecipePacket decoded = new UpdateCraftingRecipePacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertThat(decoded).extracting("recipe").isEqualTo(recipeId);
  }

  @Test
  void tinkerStationSelectionPacket_roundTrips() {
    ResourceLocation layoutName = TConstruct.getResource("tinker_station");
    TinkerStationSelectionPacket packet = new TinkerStationSelectionPacket(layoutName);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    TinkerStationSelectionPacket decoded = new TinkerStationSelectionPacket(buffer);

    assertThat(decoded).extracting("layoutName").isEqualTo(layoutName);
  }

  @Test
  void updateStationScreenPacket_isASingletonWithNoPayload() {
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    UpdateStationScreenPacket.INSTANCE.encode(buffer);
    assertThat(buffer.readableBytes()).as("encode writes nothing").isZero();
    // registered in TinkerNetwork as `buf -> UpdateStationScreenPacket.INSTANCE` - decode always yields the
    // same singleton regardless of buffer contents, verified directly rather than via a nonexistent constructor
    assertThat(UpdateStationScreenPacket.INSTANCE).isNotNull();
  }

  @Test
  void updateTinkerStationRecipePacket_roundTrips() {
    BlockPos pos = new BlockPos(2, 2, 2);
    ResourceLocation recipeId = TConstruct.getResource("part_builder");
    // only recipe.getId() is ever read by the constructor
    ITinkerStationRecipe recipe = mock(ITinkerStationRecipe.class);
    when(recipe.getId()).thenReturn(recipeId);

    UpdateTinkerStationRecipePacket packet = new UpdateTinkerStationRecipePacket(pos, recipe);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    UpdateTinkerStationRecipePacket decoded = new UpdateTinkerStationRecipePacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertThat(decoded).extracting("recipe").isEqualTo(recipeId);
  }
}
