package slimeknights.tconstruct.library.data.recipe;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.conditions.ICondition;

import javax.annotation.Nullable;

/**
 * Helper to add data to the result stack of a vanilla crafting recipe. NeoForge supports it in the format but the
 * vanilla builders take an {@link net.minecraft.world.level.ItemLike} and a count, with nowhere to say more.
 * <p>
 * 1.21 changed both halves of the 1.20 class. Stacks carry a {@link DataComponentPatch} instead of a {@code CompoundTag},
 * and {@code FinishedRecipe} is gone, so there is no JSON to reach into between the builder and the file: a builder now
 * hands a real {@link Recipe} to a {@link RecipeOutput} and the recipe serializes itself. This is therefore a
 * {@link RecipeOutput} wrapper rather than a recipe wrapper, and it edits the recipe rather than the JSON.
 * <p>
 * The edit is applied to the result stack in place. Rebuilding the recipe around a new stack was the alternative, but
 * that requires knowing the concrete recipe type and would silently downcast subclasses (Mantle's retextured recipe
 * extends {@link net.minecraft.world.item.crafting.ShapedRecipe}, and this wrapper sits underneath its builder) back to
 * their base type. Mutating is safe here because a datagen recipe object exists only to be serialized once.
 * @apiNote  The name is kept from 1.20 so addon imports survive; read "NBT" as "components".
 */
public record CraftingNBTWrapper(RecipeOutput output, DataComponentPatch components) implements RecipeOutput {
  @Override
  public Advancement.Builder advancement() {
    return output.advancement();
  }

  @Override
  public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
    // vanilla crafting recipes hold their result stack directly and ignore the registries argument, so an empty
    // registry access is enough to reach it. A recipe that builds a fresh stack per call cannot be patched this way,
    // which the empty check below turns into an error rather than a silently missing component.
    ItemStack result = recipe.getResultItem(RegistryAccess.EMPTY);
    if (result.isEmpty()) {
      throw new IllegalStateException("Cannot add components to recipe " + id + " as it has no static result");
    }
    result.applyComponents(components);
    output.accept(id, recipe, advancement, conditions);
  }

  /** Creates a wrapped recipe output, adding the given components to every result passing through */
  public static RecipeOutput wrap(RecipeOutput base, DataComponentPatch components) {
    return new CraftingNBTWrapper(base, components);
  }
}
