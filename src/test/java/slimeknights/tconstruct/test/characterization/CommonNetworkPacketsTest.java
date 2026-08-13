package slimeknights.tconstruct.test.characterization;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.common.network.InventorySlotSyncPacket;
import slimeknights.tconstruct.common.network.UpdateNeighborsPacket;
import slimeknights.tconstruct.shared.network.GeneratePartTexturesPacket;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Object -> encode -> decode -> object field round trips for every packet registered under {@code common} and
 * {@code shared} in {@code TinkerNetwork}. {@code SyncPersistentDataPacket}'s round trip went with the packet:
 * a synced data attachment needs none (T10 SS1.2). Fields are compared via AssertJ's
 * reflection-based {@code extracting} (works on private fields with no getters) against the literal values used
 * to build the original packet, rather than reading the original packet's own fields back - avoids needing any
 * production-code changes (no test-only getters) to verify private state.
 */
class CommonNetworkPacketsTest extends BaseMcTest {
  @Test
  void inventorySlotSyncPacket_roundTrips() {
    ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE, 1);
    int slot = 3;
    BlockPos pos = new BlockPos(1, 2, 3);

    InventorySlotSyncPacket packet = new InventorySlotSyncPacket(stack, slot, pos);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    InventorySlotSyncPacket decoded = new InventorySlotSyncPacket(buffer);

    assertThat(decoded.itemStack.getItem()).isEqualTo(stack.getItem());
    assertThat(decoded.itemStack.getCount()).isEqualTo(stack.getCount());
    assertThat(decoded.slot).isEqualTo(slot);
    assertThat(decoded.pos).isEqualTo(pos);
  }

  @Test
  void updateNeighborsPacket_roundTrips() {
    BlockState state = Blocks.STONE.defaultBlockState();
    BlockPos pos = new BlockPos(4, 5, 6);

    UpdateNeighborsPacket packet = new UpdateNeighborsPacket(state, pos);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    UpdateNeighborsPacket decoded = new UpdateNeighborsPacket(buffer);

    // 1.21 reads the state back through vanilla's Block.stateById rather than Forge's GameData id map, and
    // vanilla's is populated by Bootstrap - so unlike the 1.20 test, the state itself round trips here.
    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertThat(decoded).extracting("state").isEqualTo(state);
  }

  @Test
  void generatePartTexturesPacket_roundTrips() {
    GeneratePartTexturesPacket packet = new GeneratePartTexturesPacket(GeneratePartTexturesPacket.Operation.MISSING, "tconstruct", "cobalt");
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    GeneratePartTexturesPacket decoded = new GeneratePartTexturesPacket(buffer);

    assertThat(decoded).extracting("operation").isEqualTo(GeneratePartTexturesPacket.Operation.MISSING);
    assertThat(decoded).extracting("modId").isEqualTo("tconstruct");
    assertThat(decoded).extracting("materialPath").isEqualTo("cobalt");
  }
}
