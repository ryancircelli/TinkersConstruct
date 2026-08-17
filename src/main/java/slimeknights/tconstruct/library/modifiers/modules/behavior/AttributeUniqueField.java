package slimeknights.tconstruct.library.modifiers.modules.behavior;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.util.typed.TypedMap;
import slimeknights.tconstruct.TConstruct;

import java.util.function.Function;

/** Field for the unique key for an attribute. If the key is unset in JSON, defaults to the modifier name */
public record AttributeUniqueField<P>(String key, Function<P,String> getter) implements LoadableField<String,P> {
  public AttributeUniqueField(Function<P, String> getter) {
    this("unique", getter);
  }

  /**
   * Builds the {@link net.minecraft.world.entity.ai.attributes.AttributeModifier} ID for a unique key.
   * @apiNote 1.21 identifies an attribute modifier by a {@link ResourceLocation} rather than a UUID plus a display
   * name. The unique key is free text (validated only as a JSON string), so it becomes the id's path under
   * Tinkers' own namespace rather than the modifier's namespace: two modules sharing a unique string collide
   * exactly as they did when the string fed a name-based UUID, and nothing about a foreign mod's namespace was
   * ever encoded in that UUID either.
   */
  public static ResourceLocation id(String unique) {
    return TConstruct.getResource(unique);
  }

  /** Builds the per-slot {@link net.minecraft.world.entity.ai.attributes.AttributeModifier} ID for a unique key. */
  public static ResourceLocation id(String unique, EquipmentSlot slot) {
    return TConstruct.getResource(unique + "." + slot.getName());
  }

  @Override
  public String get(JsonObject json, String key, TypedMap context) {
    if (json.has(key)) {
      return GsonHelper.getAsString(json, key);
    }
    ResourceLocation id = context.get(ContextKey.ID);
    if (id == null) {
      throw new JsonParseException("Missing modifier ID in context, cannot default " + key);
    }
    return id.getNamespace() + ".modifier." + id.getPath();
  }

  @Override
  public void serialize(P parent, JsonObject json) {
    String unique = getter.apply(parent);
    if (!unique.isEmpty()) {
      json.addProperty(key, unique);
    }
  }

  @Override
  public String decode(RegistryFriendlyByteBuf buffer, TypedMap typedMap) {
    return buffer.readUtf();
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer, P parent) {
    buffer.writeUtf(getter.apply(parent));
  }
}
