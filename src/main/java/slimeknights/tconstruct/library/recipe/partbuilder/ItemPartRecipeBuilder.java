package slimeknights.tconstruct.library.recipe.partbuilder;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;
import slimeknights.mantle.recipe.helper.ItemOutput;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;


@RequiredArgsConstructor(staticName = "item")
public class ItemPartRecipeBuilder extends AbstractRecipeBuilder<ItemPartRecipeBuilder> {
  private final ResourceLocation pattern;
  private final ItemOutput result;
  @Setter @Accessors(chain = true)
  private Ingredient patternItem = IPartBuilderRecipe.DEFAULT_PATTERNS;
  private MaterialId materialId = IMaterial.UNKNOWN_ID;
  private int cost = 0;

  /** Sets the material Id and cost */
  public ItemPartRecipeBuilder material(MaterialId material, int cost) {
    this.materialId = material;
    this.cost = cost;
    return this;
  }

  @Override
  public void save(RecipeOutput output) {
    save(output, BuiltInRegistries.ITEM.getKey(result.get().getItem()));
  }

  @Override
  public void save(RecipeOutput output, ResourceLocation id) {
    save(output, id, new ItemPartRecipe(materialId, new Pattern(pattern), patternItem, cost, result), "parts");
  }
}
