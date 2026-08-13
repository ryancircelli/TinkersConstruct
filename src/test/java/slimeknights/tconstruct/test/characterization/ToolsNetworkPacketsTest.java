package slimeknights.tconstruct.test.characterization;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.tools.network.EntityMovementChangePacket;
import slimeknights.tconstruct.tools.network.InteractWithAirPacket;
import slimeknights.tconstruct.tools.network.PushBlockRowPacket;
import slimeknights.tconstruct.tools.network.SyncProjectileModifiersPacket;
import slimeknights.tconstruct.tools.network.TinkerControlPacket;
import slimeknights.tconstruct.tools.network.ToolContainerFluidUpdatePacket;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Object -> encode -> decode -> object field round trips for every packet registered under {@code tools} in {@code TinkerNetwork}. */
class ToolsNetworkPacketsTest extends BaseMcTest {
  @Test
  void tinkerControlPacket_everyEnumConstant_roundTrips() {
    for (TinkerControlPacket value : TinkerControlPacket.values()) {
      RegistryFriendlyByteBuf buffer = networkBuffer();
      value.encode(buffer);
      TinkerControlPacket decoded = TinkerControlPacket.read(buffer);
      assertThat(decoded).as("enum identity round trip for " + value).isSameAs(value);
    }
  }

  @Test
  void interactWithAirPacket_everyEnumConstant_roundTrips() {
    for (InteractWithAirPacket value : InteractWithAirPacket.values()) {
      RegistryFriendlyByteBuf buffer = networkBuffer();
      value.encode(buffer);
      InteractWithAirPacket decoded = InteractWithAirPacket.read(buffer);
      assertThat(decoded).as("enum identity round trip for " + value).isSameAs(value);
    }
  }

  @Test
  void entityMovementChangePacket_roundTrips() {
    // constructed via a mocked Entity (only getId/getDeltaMovement/getYRot/getXRot are read) rather than a live
    // level/entity, avoiding any need for a running world
    Entity entity = mock(Entity.class);
    when(entity.getId()).thenReturn(42);
    when(entity.getDeltaMovement()).thenReturn(new Vec3(1.5, -2.25, 3.75));
    when(entity.getYRot()).thenReturn(90f);
    when(entity.getXRot()).thenReturn(-45f);

    EntityMovementChangePacket packet = new EntityMovementChangePacket(entity);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    EntityMovementChangePacket decoded = new EntityMovementChangePacket(buffer);

    assertThat(decoded).extracting("entityID").isEqualTo(42);
    assertThat(decoded).extracting("x").isEqualTo(1.5);
    assertThat(decoded).extracting("y").isEqualTo(-2.25);
    assertThat(decoded).extracting("z").isEqualTo(3.75);
    assertThat(decoded).extracting("yRot").isEqualTo(90f);
    assertThat(decoded).extracting("xRot").isEqualTo(-45f);
  }

  @Test
  void pushBlockRowPacket_roundTrips() {
    BlockPos pos = new BlockPos(7, 8, 9);
    PushBlockRowPacket packet = new PushBlockRowPacket(pos, Direction.NORTH, true, 3);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    PushBlockRowPacket decoded = new PushBlockRowPacket(buffer);

    assertThat(decoded.pos()).isEqualTo(pos);
    assertThat(decoded.direction()).isEqualTo(Direction.NORTH);
    assertThat(decoded.push()).isTrue();
    assertThat(decoded.moving()).isEqualTo(3);
  }

  @Test
  void toolContainerFluidUpdatePacket_roundTrips() {
    FluidStack fluid = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 750);
    ToolContainerFluidUpdatePacket packet = new ToolContainerFluidUpdatePacket(fluid);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    ToolContainerFluidUpdatePacket decoded = new ToolContainerFluidUpdatePacket(buffer);

    assertThat(decoded.fluid().getFluid()).isEqualTo(fluid.getFluid());
    assertThat(decoded.fluid().getAmount()).isEqualTo(fluid.getAmount());
  }

  @Test
  void syncProjectileModifiersPacket_roundTrips() {
    // use the canonical (int, ModifierNBT, CompoundTag) constructor directly, not the Entity-consuming
    // convenience constructor (that one reads live capabilities off a real entity, which needs a live world)
    ModifierNBT modifiers = new ModifierNBT(List.of(new ModifierEntry(new ModifierId("tconstruct", "sharpness"), 2)));
    CompoundTag persistentData = new CompoundTag();
    persistentData.putBoolean("test", true);

    SyncProjectileModifiersPacket packet = new SyncProjectileModifiersPacket(17, modifiers, persistentData);
    RegistryFriendlyByteBuf buffer = networkBuffer();
    packet.encode(buffer);
    SyncProjectileModifiersPacket decoded = new SyncProjectileModifiersPacket(buffer);

    assertThat(decoded.entityId()).isEqualTo(17);
    assertThat(decoded.modifiers().getModifiers()).hasSize(1);
    assertThat(decoded.modifiers().getModifiers().get(0).getId()).isEqualTo(new ModifierId("tconstruct", "sharpness"));
    assertThat(decoded.modifiers().getModifiers().get(0).getLevel()).isEqualTo(2);
    assertThat(decoded.persistentData()).isEqualTo(persistentData);
  }
}
