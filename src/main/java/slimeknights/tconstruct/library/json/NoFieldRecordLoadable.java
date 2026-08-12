package slimeknights.tconstruct.library.json;

import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.util.typed.TypedMap;

import java.util.function.Supplier;

/** Same as {@link slimeknights.mantle.data.loadable.record.SingletonLoader}, but creates a new instance each time. Used to cache unique data in the object. */
public record NoFieldRecordLoadable<T>(Supplier<T> constructor) implements RecordLoadable<T> {
  @Override
  public T deserialize(JsonObject json, TypedMap context) {
    return constructor.get();
  }

  @Override
  public void serialize(T object, JsonObject json) {}

  @Override
  public T decode(RegistryFriendlyByteBuf buffer, TypedMap context) {
    return constructor.get();
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer, T value) {}
}
