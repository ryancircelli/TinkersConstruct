package slimeknights.tconstruct.tools.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.data.loadable.Streamable;
import slimeknights.mantle.network.packet.IPacket;
import slimeknights.mantle.network.packet.PacketContext;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;

import java.util.Objects;

public record SyncProjectileModifiersPacket(int entityId, ModifierNBT modifiers, CompoundTag persistentData) implements IPacket.Threadsafe {
  private static final Streamable<ModifierNBT> MODIFIER_LIST = ModifierEntry.LOADABLE.list(0).flatXmap(ModifierNBT::new, ModifierNBT::getModifiers);

  public SyncProjectileModifiersPacket(Entity entity) {
    this(entity.getId(), EntityModifierCapability.getOrEmpty(entity), PersistentDataCapability.getData(entity).getCopy());
  }

  public SyncProjectileModifiersPacket(RegistryFriendlyByteBuf buffer) {
    this(buffer.readVarInt(), MODIFIER_LIST.decode(buffer), Objects.requireNonNullElse(buffer.readNbt(), new CompoundTag()));
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeVarInt(entityId);
    MODIFIER_LIST.encode(buffer, modifiers);
    buffer.writeNbt(persistentData);
  }

  @Override
  public void handleThreadsafe(PacketContext context) {
    Level level = SafeClientAccess.getLevel();
    if (level != null) {
      Entity entity = level.getEntity(entityId);
      if (entity != null) {
        EntityModifierCapability.getCapability(entity).setModifiers(modifiers);
        PersistentDataCapability.getData(entity).copyFrom(persistentData);
      }
    }
  }
}
