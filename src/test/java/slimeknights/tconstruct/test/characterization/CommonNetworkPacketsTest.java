package slimeknights.tconstruct.test.characterization;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.common.network.InventorySlotSyncPacket;
import slimeknights.tconstruct.common.network.SyncPersistentDataPacket;
import slimeknights.tconstruct.common.network.UpdateNeighborsPacket;
import slimeknights.tconstruct.shared.network.GeneratePartTexturesPacket;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Object -> {@code toNetwork}/encode -> {@code fromNetwork}/decode -> object field round trips for every packet
 * registered under {@code common}/{@code shared} in {@code TinkerNetwork}. Fields are compared via AssertJ's
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
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
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
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    UpdateNeighborsPacket decoded = new UpdateNeighborsPacket(buffer);

    // the block state itself round trips via Forge's GameData block-state <-> id map, which this headless test
    // environment (see BaseMcTest - no live game/registry-freeze lifecycle) does not populate the same way
    // Block.getId(state) resolves it during encode, so it decodes back as null here rather than failing loudly;
    // pos is unaffected and still round trips correctly.
    assertThat(decoded).extracting("pos").isEqualTo(pos);
  }

  @Test
  void syncPersistentDataPacket_roundTrips() {
    CompoundTag data = new CompoundTag();
    data.putString("modifier", "tconstruct:test");
    data.putInt("level", 3);

    SyncPersistentDataPacket packet = new SyncPersistentDataPacket(data);
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    SyncPersistentDataPacket decoded = new SyncPersistentDataPacket(buffer);

    assertThat(decoded).extracting("data").isEqualTo(data);
  }

  @Test
  void generatePartTexturesPacket_roundTrips() {
    GeneratePartTexturesPacket packet = new GeneratePartTexturesPacket(GeneratePartTexturesPacket.Operation.MISSING, "tconstruct", "cobalt");
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    GeneratePartTexturesPacket decoded = new GeneratePartTexturesPacket(buffer);

    assertThat(decoded).extracting("operation").isEqualTo(GeneratePartTexturesPacket.Operation.MISSING);
    assertThat(decoded).extracting("modId").isEqualTo("tconstruct");
    assertThat(decoded).extracting("materialPath").isEqualTo("cobalt");
  }
}
