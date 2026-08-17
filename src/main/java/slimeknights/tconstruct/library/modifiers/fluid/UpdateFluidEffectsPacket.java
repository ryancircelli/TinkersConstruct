package slimeknights.tconstruct.library.modifiers.fluid;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus.Internal;
import slimeknights.mantle.network.packet.IPacket;
import slimeknights.mantle.network.packet.PacketContext;
import slimeknights.tconstruct.TConstruct;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet to sync fluid effects to the client.
 * <p>
 * Unlike the modifier packet, this one may decode its payloads eagerly: a fluid effect names fluids and mob
 * effects, both static registries frozen before login, so there is no self-referential hazard to defer past
 * (T8b SS9, T7 SS1).
 */
@Internal
public record UpdateFluidEffectsPacket(List<FluidEffects.Entry> fluids) implements IPacket.Threadsafe {
  /** Clientside constructor, reading from the buffer */
  public UpdateFluidEffectsPacket(RegistryFriendlyByteBuf buffer) {
    this(decode(buffer));
  }

  private static List<FluidEffects.Entry> decode(RegistryFriendlyByteBuf buffer) {
    int size = buffer.readVarInt();
    List<FluidEffects.Entry> entries = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      ResourceLocation key = buffer.readResourceLocation();
      try {
        FluidEffects effects = FluidEffects.LOADABLE.decode(buffer, FluidEffectManager.contextBuilder(key).build());
        entries.add(new FluidEffects.Entry(key, effects));
      } catch (RuntimeException e) {
        // put exception in the log with a bit more info
        TConstruct.LOG.error("Failed to decode fluid effects with ID {}", key, e);
        throw e;
      }
    }
    return List.copyOf(entries);
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeVarInt(fluids.size());
    for (FluidEffects.Entry entry : fluids) {
      ResourceLocation key = entry.name();
      buffer.writeResourceLocation(key);
      try {
        FluidEffects.LOADABLE.encode(buffer, entry.effects());
      } catch (RuntimeException e) {
        TConstruct.LOG.error("Failed to encode fluid effects with ID {}", key, e);
        throw e;
      }
    }
  }

  @Override
  public void handleThreadsafe(PacketContext context) {
    FluidEffectManager.INSTANCE.updateFromServer(fluids);
  }
}
