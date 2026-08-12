package slimeknights.tconstruct.library.json.predicate.tool;

import com.mojang.serialization.Codec;
import lombok.RequiredArgsConstructor;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags.Items;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Variant of {@link ItemPredicate} for matching Tinker tools.
 * @apiNote  1.21 made {@link ItemPredicate} a final record, so this is an {@link ItemSubPredicate} rather than a
 *           subclass. That is the mechanism vanilla replaced Forge's {@code ItemPredicate.register} with: a sub
 *           predicate is registered into {@code BuiltInRegistries.ITEM_SUB_PREDICATE_TYPE} under {@link #ID} and
 *           appears in JSON under the parent predicate's {@code predicates} object, keyed by that ID.
 */
@RequiredArgsConstructor(staticName = "ofTool")
public class ToolStackItemPredicate implements ItemSubPredicate {
  public static final ResourceLocation ID = TConstruct.getResource("tool_stack");
  /** Codec for this predicate; the body is the tool predicate itself, as this wrapper has no other fields */
  public static final Codec<ToolStackItemPredicate> CODEC = ToolStackPredicate.LOADER.codec().xmap(ToolStackItemPredicate::ofTool, predicate -> predicate.predicate);
  /** Sub predicate type, registered by {@code TinkerTools} */
  public static final ItemSubPredicate.Type<ToolStackItemPredicate> TYPE = new ItemSubPredicate.Type<>(CODEC);

  private final IJsonPredicate<IToolStackView> predicate;

  public static ToolStackItemPredicate ofContext(IJsonPredicate<IToolContext> predicate) {
    return ofTool(ToolStackPredicate.context(predicate));
  }

  @Override
  public boolean matches(ItemStack stack) {
    // tag check is important to prevent accidently modifying the NBT of non-tools
    return stack.is(Items.MODIFIABLE) && predicate.matches(ToolStack.from(stack));
  }

  /** Wraps this predicate into a full item predicate, which is what advancement criteria take */
  public ItemPredicate asItemPredicate() {
    return ItemPredicate.Builder.item().withSubPredicate(TYPE, this).build();
  }
}
