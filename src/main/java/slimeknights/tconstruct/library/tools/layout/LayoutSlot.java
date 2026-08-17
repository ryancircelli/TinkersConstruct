package slimeknights.tconstruct.library.tools.layout;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.library.utils.LazyDecode;

import javax.annotation.Nullable;
import java.util.Objects;

/** A single slot in a slot layout */
public class LayoutSlot {
  // the cast picks the public constructor; the two five argument constructors are otherwise ambiguous on a null filter
  public static final LayoutSlot EMPTY = new LayoutSlot(null, "", -1, -1, (Ingredient)null);

  /** Icon to display when the slot is empty */
  @Nullable @Getter
  private final Pattern icon;
  /** Name to display in the sidebar for the slot's "needs" */
  @Nullable
  private final String translation_key;
  @Getter
  private final int x;
  @Getter
  private final int y;
  /**
   * Filter to only allow certain items in the slot under this layout.
   * <p>
   * Deferred rather than decoded with the rest of the slot, see {@link LazyDecode}. An ingredient's 1.21 network form
   * is a list of item stacks, building an item stack runs the item's load hook, and this slot arrives in a login
   * packet, before the registries that hook consults have been synced.
   */
  @Nullable
  private final LazyDecode<Ingredient> filter;

  public LayoutSlot(@Nullable Pattern icon, @Nullable String translationKey, int x, int y, @Nullable Ingredient filter) {
    this(icon, translationKey, x, y, filter == null ? null : LazyDecode.of(Ingredient.CONTENTS_STREAM_CODEC, filter));
  }

  private LayoutSlot(@Nullable Pattern icon, @Nullable String translationKey, int x, int y, @Nullable LazyDecode<Ingredient> filter) {
    this.icon = icon;
    this.translation_key = translationKey;
    this.x = x;
    this.y = y;
    this.filter = filter;
  }

  /** If true, this is an empty slot */
  public boolean isEmpty() {
    return getTranslationKey().isEmpty();
  }

  public boolean isHidden() {
    return x == -1 && y == -1;
  }

  /** Gets the translation key of this slot */
  public String getTranslationKey() {
    return Objects.requireNonNullElse(translation_key, "");
  }

  /** Gets the filter for this slot, decoding it if it arrived off the network. Null if the slot accepts anything */
  @Nullable
  @VisibleForTesting
  protected Ingredient getFilter() {
    return filter == null ? null : filter.get();
  }

  /** Checks if the given stack is valid for this slot */
  public boolean isValid(ItemStack stack) {
    return !stack.isEmpty() && (filter == null || filter.get().test(stack));
  }


  /* Buffers */

  /** Reads a slot from the packet buffer */
  public static LayoutSlot read(RegistryFriendlyByteBuf buffer) {
    Pattern pattern = null;
    if (buffer.readBoolean()) {
      pattern = new Pattern(buffer.readResourceLocation());
    }
    String name = buffer.readUtf(Short.MAX_VALUE);
    int x = buffer.readVarInt();
    int y = buffer.readVarInt();
    LazyDecode<Ingredient> ingredient = null;
    if (buffer.readBoolean()) {
      ingredient = LazyDecode.read(buffer, Ingredient.CONTENTS_STREAM_CODEC);
    }
    return new LayoutSlot(pattern, name, x, y, ingredient);
  }

  /** Writes a slot to the packet buffer */
  public void write(RegistryFriendlyByteBuf buffer) {
    if (icon != null) {
      buffer.writeBoolean(true);
      buffer.writeResourceLocation(icon);
    } else {
      buffer.writeBoolean(false);
    }
    buffer.writeUtf(getTranslationKey());
    buffer.writeVarInt(x);
    buffer.writeVarInt(y);
    if (filter != null) {
      buffer.writeBoolean(true);
      filter.write(buffer);
    } else {
      buffer.writeBoolean(false);
    }
  }
}
