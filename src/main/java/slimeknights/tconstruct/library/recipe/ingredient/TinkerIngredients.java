package slimeknights.tconstruct.library.recipe.ingredient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import slimeknights.mantle.recipe.helper.LoadableIngredientType;
import slimeknights.tconstruct.TConstruct;

/**
 * Registration for Tinkers' custom ingredient types.
 * @apiNote  Forge kept ingredient serializers in a static map a mod wrote into during construction, and the ingredient
 *           handed back its own serializer. NeoForge gives ingredient types a registry, so the ingredient names a type
 *           it does not own and this class is where those types live. It replaces the {@code CraftingHelper.register}
 *           calls that used to sit in {@code TinkerCommons}, {@code TinkerMaterials} and {@code TinkerTools}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TinkerIngredients {
  private static final DeferredRegister<IngredientType<?>> INGREDIENTS = DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, TConstruct.MOD_ID);

  /** Ingredient matching a material item with a given material */
  public static final DeferredHolder<IngredientType<?>,IngredientType<MaterialIngredient>> MATERIAL =
    INGREDIENTS.register("material", () -> LoadableIngredientType.of(MaterialIngredient.LOADABLE));
  /** Ingredient matching a material item worth a given amount of material */
  public static final DeferredHolder<IngredientType<?>,IngredientType<MaterialValueIngredient>> MATERIAL_VALUE =
    INGREDIENTS.register("material_value", () -> LoadableIngredientType.of(MaterialValueIngredient.LOADABLE));
  /** Ingredient matching an item with no container item */
  public static final DeferredHolder<IngredientType<?>,IngredientType<NoContainerIngredient>> NO_CONTAINER =
    INGREDIENTS.register("no_container", () -> LoadableIngredientType.of(NoContainerIngredient.LOADABLE));
  /** Ingredient matching a tool with a given hook */
  public static final DeferredHolder<IngredientType<?>,IngredientType<ToolHookIngredient>> TOOL_HOOK =
    INGREDIENTS.register("tool_hook", () -> LoadableIngredientType.of(ToolHookIngredient.LOADABLE));

  /** Registers this to the bus */
  public static void init(IEventBus bus) {
    INGREDIENTS.register(bus);
  }
}
