package slimeknights.tconstruct.library.recipe.ingredient;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.crafting.IngredientType;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.data.predicate.IJsonPredicate;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicate;
import slimeknights.tconstruct.library.json.predicate.material.MaterialPredicateField;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipeCache;
import slimeknights.tconstruct.library.tools.nbt.ToolComponents;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Ingredient displaying materials on items and matching by material.
 * @apiNote  The static factories return an {@link Ingredient} rather than this class, as 1.21's {@link Ingredient} is
 *           final and every caller of these wants one. Where the ingredient itself is wanted, notably in the shaped
 *           material recipes, {@link Ingredient#getCustomIngredient()} hands it back.
 * @apiNote  The 1.20 version cached its expanded stack list behind Forge's tag invalidation counter. 1.21 has no such
 *           counter and the vanilla {@link Ingredient} wrapper caches the stream's result itself, so the cache here is
 *           gone as a second one under it would never be cleared. The wrapper's cache lives exactly as long as the
 *           recipe that parsed it, and recipes are re-parsed on every datapack reload, which is what the invalidation
 *           was buying.
 */
public class MaterialIngredient extends NestedIngredient {
  /** Loadable for this ingredient */
  public static final RecordLoadable<MaterialIngredient> LOADABLE = RecordLoadable.create(
    nestedField(),
    new MaterialPredicateField<MaterialIngredient>("material", i -> i.material),
    MaterialIngredient::new);

  private final IJsonPredicate<MaterialVariantId> material;

  protected MaterialIngredient(Ingredient nested, IJsonPredicate<MaterialVariantId> material) {
    super(nested);
    this.material = material;
  }

  /** Creates an ingredient matching the given materials */
  public static Ingredient of(Ingredient ingredient, IJsonPredicate<MaterialVariantId> material) {
    return new MaterialIngredient(ingredient, material).toVanilla();
  }

  /** Creates an ingredient matching the given materials */
  public static Ingredient of(ItemLike item, IJsonPredicate<MaterialVariantId> material) {
    return of(Ingredient.of(item), material);
  }

  /** Creates an ingredient matching any material */
  public static Ingredient of(Ingredient ingredient) {
    return of(ingredient, MaterialPredicate.ANY);
  }

  /** Creates an ingredient matching a single material */
  public static Ingredient of(Ingredient ingredient, MaterialVariantId material) {
    return of(ingredient, MaterialPredicate.variant(material));
  }

  /** Creates an ingredient matching a material tag */
  public static Ingredient of(Ingredient ingredient, TagKey<IMaterial> tag) {
    return of(ingredient, MaterialPredicate.tag(tag));
  }

  /**
   * Creates a new instance from an item with a fixed material
   * @param item      Material item
   * @param material  Material ID
   * @return  Material ingredient instance
   */
  public static Ingredient of(ItemLike item, MaterialVariantId material) {
    return of(Ingredient.of(item), material);
  }

  /**
   * Creates a new instance from an item with a tagged material
   * @param item  Material item
   * @param tag   Material tag
   * @return  Material ingredient instance
   */
  public static Ingredient of(ItemLike item, TagKey<IMaterial> tag) {
    return of(Ingredient.of(item), tag);
  }

  /**
   * Creates a new ingredient matching any material from items
   * @param item  Material item
   * @return  Material ingredient instance
   */
  public static Ingredient of(ItemLike item) {
    return of(Ingredient.of(item));
  }

  /**
   * Creates a new ingredient from a tag
   * @param tag       Tag instance
   * @param material  Material value
   * @return  Material with tag
   */
  public static Ingredient of(TagKey<Item> tag, MaterialVariantId material) {
    return of(Ingredient.of(tag), material);
  }

  /**
   * Creates a new ingredient matching any material from a tag
   * @param tag  Tag instance
   * @return  Material with tag
   */
  public static Ingredient of(TagKey<Item> tag) {
    return of(Ingredient.of(tag));
  }

  @Override
  public boolean test(ItemStack stack) {
    // check super first, should be faster
    if (stack.isEmpty() || !super.test(stack)) {
      return false;
    }
    // no need to read the material component if the material is the any predicate
    if (material != MaterialPredicate.ANY) {
      return material.matches(IMaterialItem.getMaterialFromStack(stack));
    }
    return true;
  }

  @Override
  public Stream<ItemStack> getItems() {
    if (!MaterialRegistry.isFullyLoaded()) {
      return super.getItems();
    }
    // no material? apply all materials for variants
    // note this only shows craftable material variants, and only the ones the item actually accepts:
    // setting a material the item rejects leaves the component absent, which is how the filter reads
    return Arrays.stream(nested.getItems())
                 .flatMap(stack -> MaterialRecipeCache.getAllVariants().stream()
                                                      .filter(material::matches)
                                                      .map(mat -> IMaterialItem.withMaterial(stack, mat))
                                                      .filter(withMaterial -> withMaterial.has(ToolComponents.MATERIAL)));
  }

  @Override
  public boolean isSimple() {
    return material == MaterialPredicate.ANY;
  }

  @Override
  public IngredientType<?> getType() {
    return TinkerIngredients.MATERIAL.get();
  }
}
