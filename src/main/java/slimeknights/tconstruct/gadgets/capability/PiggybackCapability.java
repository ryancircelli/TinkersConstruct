package slimeknights.tconstruct.gadgets.capability;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;

import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

/**
 * Registration for {@link PiggybackHandler}.
 * <p>
 * Not a capability despite the name it inherited from 1.20: this is Tinkers' own bookkeeping on a player, not a
 * service offered to other mods, the same reasoning {@link EntityModifierCapability} gives for making the same
 * choice. It is a plain {@link AttachmentType}, created per player the first time it is asked for rather than at
 * an {@code AttachCapabilitiesEvent} that no longer exists.
 */
public class PiggybackCapability {
  private PiggybackCapability() {}

  private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TConstruct.MOD_ID);

  /** Attachment holding a player's piggyback passenger-sync state. Not serialized: the passengers are saved with the
   * world already and simply dismount on logout, so there is nothing here worth persisting. */
  public static final Supplier<AttachmentType<PiggybackHandler>> PIGGYBACK = ATTACHMENTS.register(
    "piggyback", () -> AttachmentType.builder(holder -> new PiggybackHandler((Player) holder)).build());

  /** Registers the attachment with the mod event bus */
  public static void register(IEventBus bus) {
    ATTACHMENTS.register(bus);
  }

  /** Gets the piggyback handler for the given player, creating it if missing */
  public static PiggybackHandler get(Player player) {
    return player.getData(PIGGYBACK);
  }
}
