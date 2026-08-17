package slimeknights.tconstruct.smeltery.network;

import lombok.AllArgsConstructor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import slimeknights.mantle.inventory.BaseContainerMenu;
import slimeknights.mantle.network.packet.IPacket;
import slimeknights.mantle.network.packet.PacketContext;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler;

/**
 * Packet sent when a fluid is clicked in the smeltery UI
 */
@AllArgsConstructor
public class SmelteryFluidClickedPacket implements IPacket.Threadsafe {
  private final int index;

  public SmelteryFluidClickedPacket(RegistryFriendlyByteBuf buffer) {
    index = buffer.readVarInt();
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeVarInt(index);
  }

  @Override
  public void handleThreadsafe(PacketContext context) {
    ServerPlayer sender = context.getSender();
    if (sender != null && !sender.isSpectator()) {
      AbstractContainerMenu container = sender.containerMenu;
      if (container instanceof BaseContainerMenu<?> base && base.getTile() instanceof ISmelteryTankHandler tank) {
        tank.getTank().moveFluidToBottom(index);
      }
    }
  }
}
