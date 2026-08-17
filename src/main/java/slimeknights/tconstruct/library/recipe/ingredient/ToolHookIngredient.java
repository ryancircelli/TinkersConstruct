package slimeknights.tconstruct.library.recipe.ingredient;

import lombok.RequiredArgsConstructor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.item.IModifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ingredient that only matches tools with a specific hook.
 * @apiNote  The tag filtering used to live in an {@link Ingredient.Value}, which 1.21 closed to extension: its javadoc
 *           says to implement a custom ingredient instead, and its one implementation is a record. The filtering moved
 *           into {@link #getItems()} unchanged, which is where a custom ingredient expresses it.
 */
@RequiredArgsConstructor
public class ToolHookIngredient implements ICustomIngredient {
  /** Loadable for this ingredient */
  public static final RecordLoadable<ToolHookIngredient> LOADABLE = RecordLoadable.create(
    Loadables.ITEM_TAG.defaultField("tag", TinkerTags.Items.MODIFIABLE, true, i -> i.tag),
    ToolHooks.LOADER.requiredField("hook", i -> i.hook),
    ToolHookIngredient::new);

  private final TagKey<Item> tag;
  private final ModuleHook<?> hook;

  /** Creates an ingredient matching tools in the given tag with the given hook */
  public static Ingredient of(TagKey<Item> tag, ModuleHook<?> hook) {
    return new ToolHookIngredient(tag, hook).toVanilla();
  }

  /** Creates an ingredient matching any modifiable item with the given hook */
  public static Ingredient of(ModuleHook<?> hook) {
    return of(TinkerTags.Items.MODIFIABLE, hook);
  }

  /** Checks whether the given item has the hook this ingredient wants */
  private boolean hasHook(Item item) {
    return item instanceof IModifiable modifiable && modifiable.getToolDefinition().getData().getHooks().hasHook(hook);
  }

  @Override
  public boolean test(ItemStack stack) {
    return stack.is(tag) && hasHook(stack.getItem());
  }

  @Override
  public boolean isSimple() {
    return true;
  }

  @Override
  public Stream<ItemStack> getItems() {
    List<ItemStack> list = new ArrayList<>();
    // filtered version of tag values
    for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
      if (hasHook(holder.value())) {
        list.add(new ItemStack(holder));
      }
    }
    if (list.isEmpty()) {
      ItemStack empty = new ItemStack(Blocks.BARRIER);
      empty.set(DataComponents.CUSTOM_NAME, Component.literal("Empty Tag: " + tag.location()));
      list.add(empty);
    }
    return list.stream();
  }

  @Override
  public IngredientType<?> getType() {
    return TinkerIngredients.TOOL_HOOK.get();
  }
}
