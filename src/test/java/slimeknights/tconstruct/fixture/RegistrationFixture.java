package slimeknights.tconstruct.fixture;

import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.registry.GenericLoaderRegistry;
import slimeknights.mantle.util.typed.TypedMap;

/** Helpers for generic registration tasks */
public class RegistrationFixture {
  /** Registers an object to a registry without risk of tests failing if its registered already */
  public static <T> void register(GenericLoaderRegistry<? super T> registry, String name, RecordLoadable<T> value) {
    try {
      registry.register(ResourceLocation.fromNamespaceAndPath("test", name), value);
    } catch (Exception e) {
      // no-op
    }
  }

  /**
   * Wraps a loadable in a distinct object that behaves identically, for registering under a synthetic {@code test:} id.
   * <p>
   * {@link GenericLoaderRegistry} is backed by a Guava {@code BiMap}, so one loader object can only ever hold one
   * name. Registering a production {@code LOADER} singleton under a test id therefore takes that singleton away from
   * its real {@code tconstruct:} id, and whether that matters depends on which test class JUnit happens to run
   * first. A test that wants its own id registers an alias instead and leaves the singleton alone.
   * <p>
   * Aliases are for parsing only: serialization finds the id by looking the loader up by identity, and objects
   * parsed by a production loader report that production loader, not the alias.
   */
  public static <T> RecordLoadable<T> alias(RecordLoadable<T> loadable) {
    return new Alias<>(loadable);
  }

  /** @see #alias(RecordLoadable) */
  private record Alias<T>(RecordLoadable<T> delegate) implements RecordLoadable<T> {
    @Override
    public T deserialize(JsonObject json, TypedMap context) {
      return delegate.deserialize(json, context);
    }

    @Override
    public void serialize(T object, JsonObject json) {
      delegate.serialize(object, json);
    }

    @Override
    public T decode(RegistryFriendlyByteBuf buffer, TypedMap context) {
      return delegate.decode(buffer, context);
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer, T value) {
      delegate.encode(buffer, value);
    }
  }
}
