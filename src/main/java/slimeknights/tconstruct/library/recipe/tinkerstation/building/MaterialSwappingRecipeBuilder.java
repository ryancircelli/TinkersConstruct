package slimeknights.tconstruct.library.recipe.tinkerstation.building;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.recipe.data.AbstractRecipeBuilder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.IToolPart;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Builder for {@link FixedMaterialSwappingRecipe} and {@link PartSwappingOverrideRecipe}. */
@Accessors(fluent = true)
@RequiredArgsConstructor(staticName = "tools")
public class MaterialSwappingRecipeBuilder extends AbstractRecipeBuilder<MaterialSwappingRecipeBuilder> {
  /** Tools that support this recipe */
  private final Ingredient tools;
  @Setter
  private int maxStackSize = 16;
  /** List of indices swapped by this recipe */
  private final BitSet indices = new BitSet();
  /** Additional requirements beyond the "part" */
  private final List<SizedIngredient> extraRequirements = new ArrayList<>();

  /** Part to swap, used by part override */
  @Setter
  private IToolPart part = null;

  /**
   * Ingredient for the input part, used by fixed.
   * @apiNote  Null rather than an empty sized ingredient: NeoForge's {@link SizedIngredient} throws on a count of
   *           zero, so there is no empty instance to compare against (M8 section 3.1).
   */
  @Nullable
  private SizedIngredient ingredient = null;
  /** Material to swap to, used by fixed */
  private MaterialVariantId material = IMaterial.UNKNOWN_ID;
  /** Repair value on swapping, used by fixed */
  @Setter
  private int repairValue = 0;

  /** Creates a builder for the given tool */
  public static MaterialSwappingRecipeBuilder tool(ItemLike tool) {
    return tools(Ingredient.of(tool));
  }

  /** Creates a builder for the given tool */
  public static MaterialSwappingRecipeBuilder tools(TagKey<Item> tag) {
    return tools(Ingredient.of(tag));
  }

  /** Adds the given index to the recipe */
  public MaterialSwappingRecipeBuilder index(int index) {
    indices.set(index);
    return this;
  }

  /** Sets the material for this builder */
  public MaterialSwappingRecipeBuilder material(MaterialVariantId material, SizedIngredient ingredient) {
    this.material = material;
    this.ingredient = ingredient;
    return this;
  }

  /** Sets the material for this builder */
  public MaterialSwappingRecipeBuilder material(MaterialVariantId material, ItemLike item) {
    return material(material, SizedIngredient.of(item, 1));
  }

  /** Adds an extra ingredient requirement */
  public MaterialSwappingRecipeBuilder addExtraRequirement(SizedIngredient ingredient) {
    extraRequirements.add(ingredient);
    return this;
  }

  /** Adds an extra ingredient requirement */
  public MaterialSwappingRecipeBuilder addExtraRequirement(Ingredient ingredient) {
    return addExtraRequirement(new SizedIngredient(ingredient, 1));
  }

  /** Adds an extra ingredient requirement */
  public MaterialSwappingRecipeBuilder addExtraRequirement(ItemLike... items) {
    return addExtraRequirement(new SizedIngredient(Ingredient.of(items), 1));
  }

  @Override
  public void save(RecipeOutput output) {
    save(output, Loadables.ITEM.getKey(tools.getItems()[0].getItem()));
  }

  @Override
  public void save(RecipeOutput output, ResourceLocation id) {
    int[] indices = this.indices.stream().toArray();
    if (indices.length == 0) {
      throw new IllegalStateException("Must set index");
    }
    if (part != null) {
      if (ingredient != null) {
        throw new IllegalStateException("Cannot set both part and ingredient");
      }
      output.accept(id, new PartSwappingOverrideRecipe(tools, maxStackSize, part, indices, extraRequirements), null);
    } else {
      if (ingredient == null) {
        throw new IllegalStateException("Must set either part or ingredient");
      }
      output.accept(id, new FixedMaterialSwappingRecipe(tools, maxStackSize, ingredient, material, indices, repairValue, extraRequirements), null);
    }
  }
}
