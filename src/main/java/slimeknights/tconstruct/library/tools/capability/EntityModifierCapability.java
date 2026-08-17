package slimeknights.tconstruct.library.tools.capability;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The modifiers an entity carries, used for projectiles fired from a modifiable item so the arrow knows what shot it.
 * <p>
 * <b>It is a serialized data attachment, not a capability</b>, for the same reason as {@link TinkerDataCapability}: it
 * is Tinkers' own data on an entity rather than a service offered to other mods, and a 1.21
 * {@link net.neoforged.neoforge.capabilities.EntityCapability} has to name the entity types it applies to.
 * <p>
 * That last point is what deletes the most code here. 1.20 could not create the data on demand - the
 * {@code AttachCapabilitiesEvent} fires once, at entity construction, and had to decide there and then - so the class
 * carried a list of {@code Predicate<Entity>} that anything wanting the capability had to register itself into, and
 * {@link PersistentDataCapability} consulted the same list to decide who it attached to. An attachment is created the
 * first time it is asked for, so the predicate list, {@code registerEntityPredicate} and {@code supportCapability} are
 * all gone; whoever ports {@code tools/TinkerModifiers} drops its {@code Projectile} registration.
 * <p>
 * "Created on demand" cuts the other way for reads, though: {@link IAttachmentHolder#getData} <i>stores</i> the
 * default in the holder, so a read through it on every arrow in flight would give every arrow an attachment. Reads
 * therefore go through {@link #getOrEmpty}, which does not create, and the serializer declines to write empty
 * modifiers, so an entity that was only asked about stays clean in the save.
 */
public class EntityModifierCapability {
  private EntityModifierCapability() {}

  /** Default instance to use for an entity with no modifiers */
  public static final EntityModifiers EMPTY = new EntityModifiers() {
    @Override
    public ModifierNBT getModifiers() {
      return ModifierNBT.EMPTY;
    }

    @Override
    public void setModifiers(ModifierNBT nbt) {}

    @Override
    public void addModifiers(ModifierNBT nbt) {}
  };

  private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TConstruct.MOD_ID);

  /** Attachment holding the modifiers. Same ID the capability used, as it is the same data under the same name. */
  public static final Supplier<AttachmentType<Mutable>> MODIFIERS = ATTACHMENTS.register(
    "modifiers", () -> AttachmentType.builder(Mutable::new)
      .serialize(new IAttachmentSerializer<ListTag,Mutable>() {
        @Override
        public Mutable read(IAttachmentHolder holder, ListTag tag, HolderLookup.Provider provider) {
          Mutable modifiers = new Mutable();
          modifiers.setModifiers(ModifierNBT.readFromNBT(tag));
          return modifiers;
        }

        @Nullable
        @Override
        public ListTag write(Mutable attachment, HolderLookup.Provider provider) {
          ModifierNBT modifiers = attachment.getModifiers();
          return modifiers.isEmpty() ? null : modifiers.serializeToNBT();
        }
      })
      .build());

  /** Registers the attachment with the mod event bus */
  public static void register(IEventBus bus) {
    ATTACHMENTS.register(bus);
  }

  /** Gets the modifier holder for an entity, creating it if missing. Use when writing. */
  public static EntityModifiers getCapability(Entity entity) {
    return entity.getData(MODIFIERS);
  }

  /** Gets the modifiers on an entity, or {@link ModifierNBT#EMPTY} if it has none. Does not create the attachment. */
  public static ModifierNBT getOrEmpty(Entity entity) {
    Mutable modifiers = entity.getExistingDataOrNull(MODIFIERS);
    return modifiers == null ? ModifierNBT.EMPTY : modifiers.getModifiers();
  }

  /** Mutable holder, which is what the attachment stores */
  public static class Mutable implements EntityModifiers {
    @Getter
    @Setter
    private ModifierNBT modifiers = ModifierNBT.EMPTY;
  }

  /** Interface for callers to use */
  public interface EntityModifiers {
    /** Gets the stored modifiers */
    ModifierNBT getModifiers();

    /** Sets the stored modifiers */
    void setModifiers(ModifierNBT nbt);

    /** Adds additional modifiers to the stored modifiers */
    default void addModifiers(ModifierNBT nbt) {
      ModifierNBT existing = getModifiers();
      if (existing.isEmpty()) {
        setModifiers(nbt);
      } else {
        setModifiers(ModifierNBT.builder().add(existing).add(nbt).build());
      }
    }
  }
}
