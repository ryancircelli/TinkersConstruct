package slimeknights.tconstruct.library.tools.layout;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.utils.LazyDecode;

import javax.annotation.Nullable;

/** Data holder for a button icon, currently supports item stack icons and pattern icons */
public abstract class LayoutIcon {
  /** JSON serializer for a layout button icon */
  public static final Serializer SERIALIZER = new Serializer();

  /** Empty icon, used primarily as a fallback */
  public static final LayoutIcon EMPTY = new LayoutIcon() {
    @Nullable
    @Override
    public <T> T getValue(Class<T> clazz) {
      return null;
    }

    @Override
    public void write(RegistryFriendlyByteBuf buffer) {
      buffer.writeEnum(Type.EMPTY);
    }

    @Override
    public JsonObject toJson() {
      return new JsonObject();
    }
  };

  /** Creates a stack icon */
  public static LayoutIcon ofItem(ItemStack stack) {
    return new ItemStackIcon(LazyDecode.of(ItemStack.OPTIONAL_STREAM_CODEC, stack));
  }

  /** Creates an icon from a pattern */
  public static LayoutIcon ofPattern(Pattern pattern) {
    return new PatternIcon(pattern);
  }

  /** Gets the value of this icon, done this way to separate the drawing logic out */
  @Nullable
  public abstract <T> T getValue(Class<T> clazz);

  /**
   * Reads the button icon from the buffer.
   * <p>
   * The item stack case does not decode its stack here, see {@link LazyDecode}: building an item stack runs the
   * item's load hook, and this packet is decoded during login, before the modifier and material registries the hook
   * consults have been synced.
   */
  public static LayoutIcon read(RegistryFriendlyByteBuf buffer) {
    Type type = buffer.readEnum(Type.class);
    switch (type) {
      case EMPTY: return EMPTY;
      case ITEM: return new ItemStackIcon(LazyDecode.read(buffer, ItemStack.OPTIONAL_STREAM_CODEC));
      case PATTERN: {
        Pattern pattern = new Pattern(buffer.readResourceLocation());
        return new PatternIcon(pattern);
      }
    }
    throw new DecoderException("Invalid LayoutButtonIcon " + type);
  }

  /** Writes this to the packet buffer */
  public abstract void write(RegistryFriendlyByteBuf buffer);

  /** Writes this object to json */
  public abstract JsonObject toJson();

  /** Icon drawing an item stack */
  @VisibleForTesting
  protected static class ItemStackIcon extends LayoutIcon {
    private final LazyDecode<ItemStack> stack;

    protected ItemStackIcon(LazyDecode<ItemStack> stack) {
      this.stack = stack;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(Class<T> clazz) {
      if (clazz == ItemStack.class) {
        return (T) stack.get();
      }
      return null;
    }

    @Override
    public void write(RegistryFriendlyByteBuf buffer) {
      buffer.writeEnum(Type.ITEM);
      stack.write(buffer);
    }

    @Override
    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      ItemStack stack = this.stack.get();
      json.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
      DataComponentPatch patch = stack.getComponentsPatch();
      if (!patch.isEmpty()) {
        json.add("components", DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch).getOrThrow(JsonSyntaxException::new));
      }
      return json;
    }
  }

  /** Icon drawing a static patttern sprite */
  @VisibleForTesting
  protected static class PatternIcon extends LayoutIcon {
    private final Pattern pattern;

    protected PatternIcon(Pattern pattern) {
      this.pattern = pattern;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(Class<T> clazz) {
      if (clazz == Pattern.class) {
        return (T) pattern;
      }
      return null;
    }

    @Override
    public void write(RegistryFriendlyByteBuf buffer) {
      buffer.writeEnum(Type.PATTERN);
      buffer.writeResourceLocation(pattern);
    }

    @Override
    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("pattern", pattern.toString());
      return json;
    }
  }

  /** enum of icon types for serialization */
  private enum Type {
    EMPTY,
    ITEM,
    PATTERN
  }

  /** Serializer class */
  protected static class Serializer implements JsonSerializer<LayoutIcon>, JsonDeserializer<LayoutIcon> {
    @Override
    public LayoutIcon deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      JsonObject object = GsonHelper.convertToJsonObject(json, "button_icon");
      if (object.has("pattern")) {
        Pattern pattern = new Pattern(JsonHelper.getResourceLocation(object, "pattern"));
        return new PatternIcon(pattern);
      }
      if (object.has("item")) {
        ItemStack stack = new ItemStack(Loadables.ITEM.getIfPresent(object, "item"));
        // "nbt" in 1.20; a patch rather than a whole stack, as the count and the item are already decided above.
        // Plain JSON ops means a component type that needs the registries cannot appear here, which is no loss:
        // the only components an icon has ever carried are the ones that make a tool render as a display tool.
        if (object.has("components")) {
          stack.applyComponents(DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, object.get("components")).getOrThrow(JsonSyntaxException::new));
        }
        return ofItem(stack);
      }
      // not sure why this would be needed, but might as well
      if (object.entrySet().isEmpty()) {
        return EMPTY;
      }
      throw new JsonSyntaxException("LayoutButtonIcon must have either pattern or item");
    }

    @Override
    public JsonElement serialize(LayoutIcon icon, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
      return icon.toJson();
    }
  }
}
