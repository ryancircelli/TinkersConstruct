package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.HolderLookup;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Tinkers' own persistent NBT on an entity: modifier state that has to outlive a stat rebuild, a death or a logout.
 * <p>
 * <b>It is a serialized, synced data attachment, not a capability.</b> Everything the 1.20 capability did by hand,
 * 1.21 has a mechanism for, and each of the three pieces lines up exactly:
 * <ul>
 *   <li><b>Saving.</b> {@code ICapabilitySerializable<CompoundTag>} becomes an {@link IAttachmentSerializer} over the
 *       same {@link CompoundTag}. The payload is byte for byte what 1.20 wrote - it is {@link ModDataNBT}'s own
 *       compound either way - so only the envelope moves, from the entity's {@code ForgeCaps} compound to NeoForge's
 *       {@code neoforge:attachments} compound, both keyed {@code tconstruct:persistent_data}. There is no automatic
 *       migration between the two and this port does not write one: a 1.20 world does not survive the item and block
 *       component rewrite anyway, so a reader for the old key would only ever run on a save that cannot load.</li>
 *   <li><b>Surviving death and the end portal.</b> {@code PlayerEvent.Clone} copied the data by hand, taking care to
 *       {@code reviveCaps()} the corpse first. {@link AttachmentType.Builder#copyOnDeath()} is the death half;
 *       returning from the end is already copied for every serializable attachment. Both listeners go, and so does the
 *       revive dance, which existed only because capabilities were invalidated out from under it.</li>
 *   <li><b>Syncing to the owner.</b> Three listeners pushed a packet on login, respawn and dimension change.
 *       NeoForge syncs a player's own synced attachments at precisely those three points
 *       ({@code PlayerList#placeNewPlayer}, {@code PlayerList#respawn} and {@code ServerPlayer#changeDimension} all
 *       call {@code AttachmentSync#syncInitialPlayerAttachments}), so declaring a sync handler reproduces the old
 *       behaviour - including the "not during gameplay" part, since nothing here calls
 *       {@link IAttachmentHolder#syncData}. The {@code sendToPlayer} predicate keeps it to the owning player, which is
 *       what the old packet did; without it the data would also go to everyone tracking them.</li>
 * </ul>
 * That last point deletes {@code SyncPersistentDataPacket} and its handler, which is a residual for whoever ports
 * {@code common/network}.
 */
public class PersistentDataCapability {
  private PersistentDataCapability() {}

  private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TConstruct.MOD_ID);

  /**
   * Attachment holding the data. Same ID the capability used, as it is the same data under the same name.
   * <p>
   * The serializer writes null for empty data so an entity that never gained any does not grow an empty compound in
   * every save; {@link IAttachmentSerializer#write} treats null as "nothing to store".
   */
  public static final Supplier<AttachmentType<ModDataNBT>> PERSISTENT_DATA = ATTACHMENTS.register(
    "persistent_data", () -> AttachmentType.builder(ModDataNBT::new)
      .serialize(new IAttachmentSerializer<CompoundTag,ModDataNBT>() {
        @Override
        public ModDataNBT read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
          return ModDataNBT.readFromNBT(tag);
        }

        @Nullable
        @Override
        public CompoundTag write(ModDataNBT attachment, HolderLookup.Provider provider) {
          CompoundTag tag = attachment.getCopy();
          return tag.isEmpty() ? null : tag;
        }
      })
      .copyOnDeath()
      // only the owner ever read this on the client, and the 1.20 packet was addressed to them alone
      .sync((holder, to) -> holder == to, ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(ModDataNBT::readFromNBT, ModDataNBT::getCopy))
      .build());

  /** Registers the attachment with the mod event bus */
  public static void register(IEventBus bus) {
    ATTACHMENTS.register(bus);
  }

  /**
   * Gets the data for an entity, creating it if missing.
   * @apiNote  Replaces {@code getOrWarn}: there is nothing left to warn about. The 1.20 method existed because the
   *           capability was attached by an event that could decline, so a caller could legitimately find nothing;
   *           an attachment is created on demand for any holder, so the empty case cannot happen.
   */
  public static ModDataNBT getData(Entity entity) {
    return entity.getData(PERSISTENT_DATA);
  }

  /** Gets the data for an entity, or null if the entity has never had any. Use when only reading. */
  @Nullable
  public static ModDataNBT getExistingData(Entity entity) {
    return entity.getExistingDataOrNull(PERSISTENT_DATA);
  }

  /** Sends the data to the owning client, for a mid-gameplay change that has to be seen there */
  public static void sync(Entity entity) {
    entity.syncData(PERSISTENT_DATA);
  }
}
