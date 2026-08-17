package slimeknights.tconstruct.library.recipe.material;

import lombok.NoArgsConstructor;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorates a vanilla shaped recipe builder's output into {@link ShapedMaterialRecipe}.
 * @apiNote  See {@link MaterialsConsumerBuilder} for why this wraps the output rather than the finished recipe.
 */
@Deprecated
@NoArgsConstructor(staticName = "wrap")
public class ShapedMaterialConsumerBuilder {
  private final List<MaterialVariantId> materials = new ArrayList<>();

  /** Adds a material to the builder */
  public ShapedMaterialConsumerBuilder material(MaterialVariantId material) {
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
        output.accept(id, new ShapedMaterialRecipe((ShapedRecipe)recipe, materials), advancement, conditions);
      }
    };
  }
}
