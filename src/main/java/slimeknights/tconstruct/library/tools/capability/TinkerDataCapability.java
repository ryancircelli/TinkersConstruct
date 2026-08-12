package slimeknights.tconstruct.library.tools.capability;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.mantle.registration.object.IdAwareObject;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Common scratch space for Tinkers' data on a living entity, primarily used by armor. The data is rebuilt from the
 * equipment change events, never saved and never sent, which is what decides its 1.21 shape.
 * <p>
 * <b>It is a data attachment, not a capability.</b> A capability answers "can this object do X" for anyone who asks;
 * this is Tinkers' own scratch space that happens to live on an entity, and 1.21's
 * {@link net.neoforged.neoforge.capabilities.EntityCapability} would additionally require naming every
 * {@link net.minecraft.world.entity.EntityType} it applies to, which for "every living entity, including modded ones"
 * is not expressible. An attachment is created on demand for whatever holder asks, which is exactly the old
 * {@code AttachCapabilitiesEvent} test {@code instanceof LivingEntity} without the event: {@link #getData} takes a
 * {@link LivingEntity}, so the restriction is in the signature instead.
 * <p>
 * The attachment is deliberately <b>not</b> serializable and not synced, matching 1.20 where the capability wrote no
 * NBT and had no packet. That also means NeoForge does not copy it when a player respawns or changes dimension - it is
 * skipped outright, see {@code AttachmentInternals#copyAttachments} - which is the behaviour 1.20 had too, since only
 * {@link PersistentDataCapability} hand-copied itself on {@code PlayerEvent.Clone}. The equipment change events refill
 * it on the new entity.
 * <p>
 * The one piece of 1.20 machinery with no counterpart is the provider's {@code Runnable}, which preserved the map
 * across a capability invalidate/revive cycle. Attachments are plain fields on the holder and are never invalidated,
 * so the hazard it worked around does not exist.
 */
public class TinkerDataCapability {
  private TinkerDataCapability() {}

  private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TConstruct.MOD_ID);

  /** Attachment holding the data. Same ID the capability used, as it is the same data under the same name. */
  public static final Supplier<AttachmentType<Holder>> TINKER_DATA = ATTACHMENTS.register(
    "modifier_data", () -> AttachmentType.builder(Holder::new).build());

  /** Registers the attachment with the mod event bus */
  public static void register(IEventBus bus) {
    ATTACHMENTS.register(bus);
  }

  /**
   * Gets the data for an entity, creating it if missing.
   * @apiNote  Unlike 1.20 this never returns null: an attachment is created on first access, so the callers that
   *           null checked the {@code LazyOptional} no longer have anything to check.
   */
  public static Holder getData(LivingEntity entity) {
    return entity.getData(TINKER_DATA);
  }

  /** Gets the data for an entity, or null if the entity has never had any. Use when only reading. */
  @Nullable
  public static Holder getExistingData(LivingEntity entity) {
    return entity.getExistingDataOrNull(TINKER_DATA);
  }


  /** Class for generic keys */
  @SuppressWarnings("unused")
  @RequiredArgsConstructor(staticName = "of")
  public static class TinkerDataKey<T> implements IdAwareObject {
    /** Name for debug */
    @Getter
    private final ResourceLocation id;

    @Override
    public String toString() {
      return "TinkerDataKey{" + id + '}';
    }
  }

  /** Extension key that can automatically create an instance if missing */
  public static class ComputableDataKey<T> extends TinkerDataKey<T> implements Function<TinkerDataKey<?>, T> {
    private final Supplier<T> constructor;
    private ComputableDataKey(ResourceLocation name, Supplier<T> constructor) {
      super(name);
      this.constructor = constructor;
    }

    /** Creates a new instance */
    public static <T> ComputableDataKey<T> of(ResourceLocation name, Supplier<T> constructor) {
      return new ComputableDataKey<>(name, constructor);
    }

    @Override
    public T apply(TinkerDataKey<?> tinkerDataKey) {
      return constructor.get();
    }
  }


  /** Data class holding the tinker data */
  public static class Holder {
    private final Map<TinkerDataKey<?>, Object> data = new IdentityHashMap<>();

    /**
     * Adds a value to the holder
     * @param key    Key to add
     * @param value  Value to add
     * @param <T>    Data type
     */
    public <T> void put(TinkerDataKey<T> key, T value) {
      data.put(key, value);
    }

    /**
     * Adds the given value to the float data key
     * @param key    Key to add
     * @param value  Value to add
     */
    public void add(TinkerDataKey<Float> key, float value) {
      float newValue = get(key, 0f) + value;
      if (newValue == 0) {
        data.remove(key);
      } else {
        data.put(key, newValue);
      }
    }

    /**
     * Removes a value to the holder
     * @param key  Key to remove
     */
    public void remove(TinkerDataKey<?> key) {
      data.remove(key);
    }

    /**
     * Gets a value from the holder, or a default if missing
     * @param key           Holder key
     * @param defaultValue  Value
     * @param <T>           Data type
     * @return  Data or default
     */
    @SuppressWarnings("unchecked")
    public <S, T extends S> S get(TinkerDataKey<T> key, S defaultValue) {
      return (T) data.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a value from the holder, or null if missing
     * @param key           Holder key
     * @param <T>           Data type
     * @return  Data or default
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(TinkerDataKey<T> key) {
      return (T) data.get(key);
    }

    /** Gets the value from the holder, creating it if missing */
    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(TinkerDataKey<T> key, Function<TinkerDataKey<?>,T> constructor) {
      return (T) data.computeIfAbsent(key, constructor);
    }

    /** Gets the value from the holder, creating it if missing */
    public <T, U extends TinkerDataKey<T> & Function<TinkerDataKey<?>,T>> T computeIfAbsent(U key) {
      return computeIfAbsent(key, key);
    }

    /**
     * Checks if the given key is present
     * @param key  Key to check
     * @return  true if present
     */
    public boolean contains(TinkerDataKey<?> key) {
      return data.containsKey(key);
    }
  }
}
