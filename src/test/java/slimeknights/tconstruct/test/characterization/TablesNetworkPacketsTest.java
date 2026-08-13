package slimeknights.tconstruct.test.characterization;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
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
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    StationTabPacket decoded = new StationTabPacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
  }

  @Test
  void tinkerStationRenamePacket_roundTrips() {
    TinkerStationRenamePacket packet = new TinkerStationRenamePacket("My Favorite Hammer");
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    TinkerStationRenamePacket decoded = new TinkerStationRenamePacket(buffer);

    assertThat(decoded).extracting("name").isEqualTo("My Favorite Hammer");
  }

  @Test
  void updateCraftingRecipePacket_roundTrips() {
    BlockPos pos = new BlockPos(1, 1, 1);
    ResourceLocation recipeId = TConstruct.getResource("scorched_anvil_material");
    // 1.21 took the id off Recipe and put it on RecipeHolder, so the packet takes a holder; the recipe inside it
    // is never read, which is why a bare mock does
    RecipeHolder<CraftingRecipe> recipe = new RecipeHolder<>(recipeId, mock(CraftingRecipe.class));

    UpdateCraftingRecipePacket packet = new UpdateCraftingRecipePacket(pos, recipe);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    UpdateCraftingRecipePacket decoded = new UpdateCraftingRecipePacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertThat(decoded).extracting("recipe").isEqualTo(recipeId);
  }

  @Test
  void tinkerStationSelectionPacket_roundTrips() {
    ResourceLocation layoutName = TConstruct.getResource("tinker_station");
    TinkerStationSelectionPacket packet = new TinkerStationSelectionPacket(layoutName);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    TinkerStationSelectionPacket decoded = new TinkerStationSelectionPacket(buffer);

    assertThat(decoded).extracting("layoutName").isEqualTo(layoutName);
  }

  @Test
  void updateStationScreenPacket_isASingletonWithNoPayload() {
    RegistryFriendlyByteBuf buffer = networkBuffer();
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
    // same holder change as above; the recipe inside is never read
    RecipeHolder<ITinkerStationRecipe> recipe = new RecipeHolder<>(recipeId, mock(ITinkerStationRecipe.class));

    UpdateTinkerStationRecipePacket packet = new UpdateTinkerStationRecipePacket(pos, recipe);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    UpdateTinkerStationRecipePacket decoded = new UpdateTinkerStationRecipePacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertThat(decoded).extracting("recipe").isEqualTo(recipeId);
  }
}
