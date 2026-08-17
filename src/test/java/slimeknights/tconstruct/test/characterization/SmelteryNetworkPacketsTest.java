package slimeknights.tconstruct.test.characterization;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.smeltery.network.ChannelFlowPacket;
import slimeknights.tconstruct.smeltery.network.FaucetActivationPacket;
import slimeknights.tconstruct.smeltery.network.FluidUpdatePacket;
import slimeknights.tconstruct.smeltery.network.SmelteryFluidClickedPacket;
import slimeknights.tconstruct.smeltery.network.SmelteryTankUpdatePacket;
import slimeknights.tconstruct.smeltery.network.StructureErrorPositionPacket;
import slimeknights.tconstruct.smeltery.network.StructureUpdatePacket;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Object -> encode -> decode -> object field round trips for every packet registered under {@code smeltery} in {@code TinkerNetwork}. */
class SmelteryNetworkPacketsTest extends BaseMcTest {
  @Test
  void fluidUpdatePacket_roundTrips() {
    BlockPos pos = new BlockPos(1, 2, 3);
    FluidStack fluid = new FluidStack(Fluids.LAVA, 500);
    FluidUpdatePacket packet = new FluidUpdatePacket(pos, fluid);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    FluidUpdatePacket decoded = new FluidUpdatePacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertFluid(decoded, fluid);
  }

  @Test
  void faucetActivationPacket_roundTrips() {
    BlockPos pos = new BlockPos(4, 5, 6);
    FluidStack fluid = new FluidStack(Fluids.WATER, 250);
    FaucetActivationPacket packet = new FaucetActivationPacket(pos, fluid, true);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    FaucetActivationPacket decoded = new FaucetActivationPacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertFluid(decoded, fluid);
    assertThat(decoded).extracting("isPouring").isEqualTo(true);
  }

  @Test
  void channelFlowPacket_roundTrips() {
    BlockPos pos = new BlockPos(7, 8, 9);
    ChannelFlowPacket packet = new ChannelFlowPacket(pos, Direction.SOUTH, true);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    ChannelFlowPacket decoded = new ChannelFlowPacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertThat(decoded).extracting("side").isEqualTo(Direction.SOUTH);
    assertThat(decoded).extracting("flow").isEqualTo(true);
  }

  @Test
  void smelteryTankUpdatePacket_roundTrips() {
    BlockPos pos = new BlockPos(10, 11, 12);
    List<FluidStack> fluids = List.of(new FluidStack(Fluids.LAVA, 1000), new FluidStack(Fluids.WATER, 500));
    SmelteryTankUpdatePacket packet = new SmelteryTankUpdatePacket(pos, fluids);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    SmelteryTankUpdatePacket decoded = new SmelteryTankUpdatePacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertFluids(decoded, fluids);
  }

  @Test
  void structureUpdatePacket_roundTrips() {
    BlockPos pos = new BlockPos(0, 0, 0);
    BlockPos min = new BlockPos(-1, -1, -1);
    BlockPos max = new BlockPos(1, 1, 1);
    List<BlockPos> tanks = List.of(new BlockPos(1, 0, 0), new BlockPos(0, 1, 0));
    StructureUpdatePacket packet = new StructureUpdatePacket(pos, min, max, tanks);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    StructureUpdatePacket decoded = new StructureUpdatePacket(buffer);

    assertThat(decoded).extracting("pos").isEqualTo(pos);
    assertThat(decoded).extracting("minPos").isEqualTo(min);
    assertThat(decoded).extracting("maxPos").isEqualTo(max);
    assertThat(decoded).extracting("tanks").isEqualTo(tanks);
  }

  @Test
  void smelteryFluidClickedPacket_roundTrips() {
    SmelteryFluidClickedPacket packet = new SmelteryFluidClickedPacket(4);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    SmelteryFluidClickedPacket decoded = new SmelteryFluidClickedPacket(buffer);

    assertThat(decoded).extracting("index").isEqualTo(4);
  }

  @Test
  void structureErrorPositionPacket_roundTrips_withErrorPos() {
    BlockPos controller = new BlockPos(2, 2, 2);
    BlockPos error = new BlockPos(3, 3, 3);
    StructureErrorPositionPacket packet = new StructureErrorPositionPacket(controller, error);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    StructureErrorPositionPacket decoded = new StructureErrorPositionPacket(buffer);

    assertThat(decoded).extracting("controllerPos").isEqualTo(controller);
    assertThat(decoded).extracting("errorPos").isEqualTo(error);
  }

  @Test
  void structureErrorPositionPacket_roundTrips_withNullErrorPos() {
    BlockPos controller = new BlockPos(5, 5, 5);
    StructureErrorPositionPacket packet = new StructureErrorPositionPacket(controller, null);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    StructureErrorPositionPacket decoded = new StructureErrorPositionPacket(buffer);

    assertThat(decoded).extracting("controllerPos").isEqualTo(controller);
    assertThat(decoded).extracting("errorPos").isNull();
  }

  /**
   * Asserts a packet's {@code fluid} field survived the round trip.
   * <p>
   * {@link FluidStack} has no {@code equals} in 1.21 - it follows {@link net.minecraft.world.item.ItemStack}, which
   * dropped its own when data components landed, and offers {@link FluidStack#matches} instead. 1.20's Forge
   * FluidStack did implement equals, so {@code isEqualTo} used to work here and now silently compares identity:
   * every one of these assertions failed against a decoded stack holding exactly the right fluid and amount.
   */
  private static void assertFluid(FluidUpdatePacket packet, FluidStack expected) {
    assertThat(packet).extracting("fluid").satisfies(value -> {
      FluidStack actual = (FluidStack) value;
      assertThat(FluidStack.matches(actual, expected)).as("expected %s, got %s", expected, actual).isTrue();
    });
  }

  /** @see #assertFluid(FluidUpdatePacket, FluidStack) */
  private static void assertFluids(SmelteryTankUpdatePacket packet, List<FluidStack> expected) {
    assertThat(packet).extracting("fluids").satisfies(value -> {
      @SuppressWarnings("unchecked")
      List<FluidStack> actual = (List<FluidStack>) value;
      assertThat(actual).hasSameSizeAs(expected);
      for (int i = 0; i < expected.size(); i++) {
        assertThat(FluidStack.matches(actual.get(i), expected.get(i)))
          .as("fluid %d: expected %s, got %s", i, expected.get(i), actual.get(i)).isTrue();
      }
    });
  }
}
