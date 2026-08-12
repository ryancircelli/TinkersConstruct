package slimeknights.tconstruct.library.recipe.material;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorates a vanilla crafting recipe builder's output into {@link ShapedMaterialsRecipe} or
 * {@link ShapelessMaterialsRecipe}.
 * @apiNote  1.20 wrapped the {@code FinishedRecipe} and rewrote the {@code type} field it was about to serialize.
 *           {@code FinishedRecipe} is gone and a recipe's type comes from the recipe object now, so this wraps the
 *           {@link RecipeOutput} and swaps the recipe on its way past, which is 1.21's spelling of the same idea.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MaterialsConsumerBuilder {
  private final String parts;
  private final int partCount;
  private final List<MaterialVariantId> materials = new ArrayList<>();

  /** Creates a new shaped recipe with the given ingredients as parts */
  public static MaterialsConsumerBuilder shaped(String parts) {
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("Parts may not be empty");
    }
    return new MaterialsConsumerBuilder(parts, 0);
  }

  /** Creates a new shapeless recipe with the first ingredients as parts */
  public static MaterialsConsumerBuilder shapeless(int parts) {
    if (parts <= 0) {
      throw new IllegalArgumentException("Parts must be greater than 0");
    }
    return new MaterialsConsumerBuilder("", parts);
  }

  /** Adds a material to the builder */
  public MaterialsConsumerBuilder material(MaterialVariantId material) {
    materials.add(material);
    return this;
  }

  /** Builds the wrapped output */
  public RecipeOutput build(RecipeOutput output) {
    List<MaterialVariantId> materials = List.copyOf(this.materials);
    return new RecipeOutput() {
      @Override
      public Advancement.Builder advancement() {
        return output.advancement();
      }

      @Override
      public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
        Recipe<?> wrapped;
        if (partCount > 0) {
          wrapped = new ShapelessMaterialsRecipe((ShapelessRecipe)recipe, partCount, materials);
        } else {
          // the resolved part ingredients are left empty: the built recipe is only ever serialized, and the
          // serialized form names the part symbols, which are resolved against the key map when it is read back.
          // 1.21's ShapedRecipePattern packs the key map away and never hands it back, so datagen cannot resolve them
          wrapped = new ShapedMaterialsRecipe((ShapedRecipe)recipe, parts, List.of(), materials);
        }
        output.accept(id, wrapped, advancement, conditions);
      }
    };
  }
}
