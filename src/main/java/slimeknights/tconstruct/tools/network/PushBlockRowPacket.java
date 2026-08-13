package slimeknights.tconstruct.tools.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import slimeknights.mantle.network.packet.IPacket;
import slimeknights.mantle.network.packet.PacketContext;
import slimeknights.tconstruct.library.modifiers.fluid.block.MoveBlocksFluidEffect;

/** Packet handling {@link MoveBlocksFluidEffect} syncing to the client */
public record PushBlockRowPacket(BlockPos pos, Direction direction, boolean push, int moving) implements IPacket.Threadsafe {
  public PushBlockRowPacket(RegistryFriendlyByteBuf buffer) {
    this(buffer.readBlockPos(), buffer.readEnum(Direction.class), buffer.readBoolean(), buffer.readVarInt());
  }

  /** Gets the facing value for this packet */
  private Direction facing() {
    return push ? direction : direction.getOpposite();
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeBlockPos(pos);
    buffer.writeEnum(direction);
    buffer.writeBoolean(push);
    buffer.writeVarInt(moving);
  }

  @Override
  public void handleThreadsafe(PacketContext context) {
    HandleClient.handle(this);
  }

  /** Accesses client only safely */
  private static class HandleClient {
    public static void handle(PushBlockRowPacket packet) {
      Level level = Minecraft.getInstance().level;
      if (level != null) {
        MoveBlocksFluidEffect.moveBlocks(level, packet.pos, level.getBlockState(packet.pos), packet.facing(), packet.direction, packet.moving);
      }
    }
  }
}
