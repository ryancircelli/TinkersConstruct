package slimeknights.tconstruct.smeltery.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import slimeknights.mantle.network.packet.IPacket;
import slimeknights.mantle.network.packet.PacketContext;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;

/** Packet for when the flowing state changes on a channel side */
public class ChannelFlowPacket implements IPacket.Threadsafe {
	private final BlockPos pos;
	private final Direction side;
	private final boolean flow;
	public ChannelFlowPacket(BlockPos pos, Direction side, boolean flow) {
		this.pos = pos;
		this.side = side;
		this.flow = flow;
	}

	public ChannelFlowPacket(RegistryFriendlyByteBuf buffer) {
		pos = buffer.readBlockPos();
		side = buffer.readEnum(Direction.class);
		flow = buffer.readBoolean();
	}

	@Override
	public void encode(RegistryFriendlyByteBuf buffer) {
		buffer.writeBlockPos(pos);
		buffer.writeEnum(side);
		buffer.writeBoolean(flow);
	}

	@Override
	public void handleThreadsafe(PacketContext context) {
		HandleClient.handle(this);
	}

	private static class HandleClient {
		private static void handle(ChannelFlowPacket packet) {
			BlockEntityHelper.get(ChannelBlockEntity.class, Minecraft.getInstance().level, packet.pos).ifPresent(te -> te.setFlow(packet.side, packet.flow));
		}
	}
}
