package slimeknights.tconstruct.library.recipe.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.recipe.helper.LoggingRecipeSerializer;
import slimeknights.mantle.util.LogicHelper;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tables.TinkerTables;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shaped recipe with a number of {@link slimeknights.tconstruct.library.recipe.ingredient.MaterialIngredient} and
 * {@link slimeknights.tconstruct.library.recipe.ingredient.MaterialValueIngredient} to set the materials of the result.
 */
public class ShapedMaterialsRecipe extends ShapedRecipe implements MaterialsCraftingTableRecipe {
  /**
   * Symbols from the recipe's key naming {@link #parts}, in order.
   * @apiNote  Kept because writing the recipe back to JSON needs the symbols, and 1.21's {@link ShapedRecipePattern}
   *           packs the key map away without an accessor, so the ingredients cannot be turned back into symbols. Empty
   *           for a recipe read from the network, which therefore cannot be written to JSON - the same asymmetry
   *           vanilla's own shaped recipe has, for the same reason.
   */
  private final String partsPattern;
  /** List of tool parts to search for in the final recipe */
  @Getter
  private final List<Ingredient> parts;
  /**
   * If true, a part may show up multiple times in the inputs, and all copies should match.
   * If false, only the first instance of a part is checked for each input, allowing a tool with the same part multiple times.
   */
  private final boolean checkRepeats;
  /** List of additional materials to add beyond the parts */
  @Getter
  private final List<MaterialVariantId> extraMaterials;

  public ShapedMaterialsRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification,
                               String partsPattern, List<Ingredient> parts, List<MaterialVariantId> extraMaterials) {
    super(group, category, pattern, result, showNotification);
    this.partsPattern = partsPattern;
    this.parts = parts;
    this.checkRepeats = parts.stream().unordered().distinct().count() == parts.size();
    this.extraMaterials = extraMaterials;
  }

  public ShapedMaterialsRecipe(ShapedRecipe recipe, String partsPattern, List<Ingredient> parts, List<MaterialVariantId> extraMaterials) {
    this(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.result, recipe.showNotification(), partsPattern, parts, extraMaterials);
  }

  @Override
  public int getPartCount() {
    return parts.size();
  }

  /**
   * Finds materials for each of the parts
   * @return Array of all matched materials. Array will have no null entries, though the array may be null if no match was found.
   */
  @Nullable
  static MaterialVariantId[] findMaterials(CraftingInput input, List<Ingredient> parts, int partCount, boolean checkRepeats) {
    // want one material for each
    MaterialVariantId[] materials = new MaterialVariantId[partCount];
    for (int i = 0; i < input.size(); i++) {
      ItemStack stack = input.getItem(i);
      if (!stack.isEmpty()) {
        for (int p = 0; p < partCount; p++) {
          MaterialVariantId current = materials[p];
          // if we have not found the material yet, or repeats are considered the same material, test the ingredient
          if ((current == null || checkRepeats) && parts.get(p).test(stack)) {
            MaterialVariantId matched;
            if (stack.getItem() instanceof IMaterialItem materialItem) {
              matched = materialItem.getMaterial(stack);
            } else {
              matched = MaterialRecipeCache.findRecipe(stack).getMaterial().getVariant();
            }
            // first occurrence? thats our material
            if (current == null) {
              materials[p] = matched;
              break;
            } else if (!current.matchesVariant(matched)) {
              // if same material but different variants, just discard the variant
              if (current.getId().equals(matched.getId())) {
                materials[p] = current.getId();
                break;
              } else {
                // if different materials, no match
                return null;
              }
            }
          }
        }
      }
    }
    // ensure we found all materials needed
    for (int p = 0; p < partCount; p++) {
      if (materials[p] == null) {
        return null;
      }
    }
    return materials;
  }

  @Override
  public boolean matches(CraftingInput input, Level level) {
    if (!super.matches(input, level)) {
      return false;
    }
    // ensure all part materials matched and we found all parts
    return findMaterials(input, parts, parts.size(), checkRepeats) != null;
  }

  /** Common logic to this and {@link ShapelessMaterialsRecipe} */
  public static void setMaterial(ItemStack stack, MaterialVariantId material, List<MaterialVariantId> extraMaterials) {
    if (extraMaterials.isEmpty() && stack.getItem() instanceof IMaterialItem materialItem) {
      materialItem.setMaterial(stack, material);
    } else {
      MaterialNBT.Builder builder = MaterialNBT.builder();
      builder.add(material);
      for (MaterialVariantId extraMaterial : extraMaterials) {
        builder.add(extraMaterial);
      }
      ToolStack.mutable(stack).setMaterials(builder.build());
    }
  }

  /** Sets the material for the given stack */
  @Override
  public void setMaterial(ItemStack stack, MaterialVariantId material) {
    setMaterial(stack, material, extraMaterials);
  }

  /** Assembles the item with material information */
  static ItemStack assemble(ItemStack stack, CraftingInput input, List<Ingredient> parts, int partCount, boolean checkRepeats, List<MaterialVariantId> extraMaterials) {
    MaterialVariantId[] materials = findMaterials(input, parts, partCount, checkRepeats);
    if (materials != null) {
      // if the result is a tool part, and we only have the one material, set its material
      if (materials.length == 1 && extraMaterials.isEmpty() && stack.getItem() instanceof IMaterialItem materialItem) {
        return materialItem.setMaterial(stack, materials[0]);
      }
      MaterialNBT.Builder builder = MaterialNBT.builder();
      // add each material
      for (MaterialVariantId material : materials) {
        builder.add(material);
      }
      // add extra materials
      builder.add(extraMaterials);
      ToolStack.mutable(stack).setMaterials(builder.build());
    }
    return stack;
  }

  @Override
  public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
    return assemble(super.assemble(input, registries), input, parts, parts.size(), checkRepeats, extraMaterials);
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return TinkerTables.shapedMaterialsRecipeSerializer.get();
  }

  public static class Serializer implements LoggingRecipeSerializer<ShapedMaterialsRecipe> {
    static final Loadable<List<MaterialVariantId>> EXTRA_MATERIALS = MaterialVariantId.LOADABLE.list(0);
    static final LoadableField<List<MaterialVariantId>,ShapedMaterialsRecipe> MATERIAL_FIELD = EXTRA_MATERIALS.defaultField("extra_materials", List.of(), r -> r.extraMaterials);
    private static final Codec<List<MaterialVariantId>> EXTRA_MATERIALS_CODEC = EXTRA_MATERIALS.codec();

    /** Intermediate form, needed as the recipe reads the pattern's key to resolve its parts but stores the resolved ingredients */
    private record Raw(String group, CraftingBookCategory category, ShapedRecipePattern.Data pattern, ItemStack result, boolean showNotification,
                       String parts, List<MaterialVariantId> extraMaterials) {}

    private static final MapCodec<Raw> RAW_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
      Codec.STRING.optionalFieldOf("group", "").forGetter(Raw::group),
      CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(Raw::category),
      ShapedRecipePattern.Data.MAP_CODEC.forGetter(Raw::pattern),
      ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Raw::result),
      Codec.BOOL.optionalFieldOf("show_notification", Boolean.TRUE).forGetter(Raw::showNotification),
      Codec.STRING.fieldOf("parts").forGetter(Raw::parts),
      EXTRA_MATERIALS_CODEC.optionalFieldOf("extra_materials", List.of()).forGetter(Raw::extraMaterials)
    ).apply(instance, Raw::new));

    /**
     * Encoder half of {@link #CODEC}.
     * @apiNote  Reading needs the key map, which only {@link ShapedRecipePattern.Data} exposes, while writing needs the
     *           packed pattern the recipe holds; its apply function is never called, as this is only used to encode.
     */
    private static final MapCodec<ShapedMaterialsRecipe> ENCODER = RecordCodecBuilder.mapCodec(instance -> instance.group(
      Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
      CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapedRecipe::category),
      ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
      ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
      Codec.BOOL.optionalFieldOf("show_notification", Boolean.TRUE).forGetter(ShapedRecipe::showNotification),
      Codec.STRING.fieldOf("parts").forGetter(recipe -> recipe.partsPattern),
      EXTRA_MATERIALS_CODEC.optionalFieldOf("extra_materials", List.of()).forGetter(recipe -> recipe.extraMaterials)
    ).apply(instance, (group, category, pattern, result, showNotification, parts, extraMaterials) -> {
      throw new UnsupportedOperationException("Decode through CODEC, which reads the key map this cannot see");
    }));

    public static final MapCodec<ShapedMaterialsRecipe> CODEC = MapCodec.of(ENCODER, RAW_CODEC.flatMap(Serializer::unpack), () -> "ShapedMaterialsRecipe");

    /**
     * Network form, which syncs each distinct ingredient once and then an index per grid slot and per part.
     * @apiNote  This is 1.20's format unchanged. It cannot go through {@link ShapedRecipePattern#STREAM_CODEC}, which
     *           writes the whole ingredient list itself and offers no way to interleave the part indices.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialsRecipe> STREAM_CODEC = StreamCodec.of(
      (buffer, recipe) -> {
        // standard shaped recipe stuff
        buffer.writeVarInt(recipe.getWidth());
        buffer.writeVarInt(recipe.getHeight());
        buffer.writeUtf(recipe.getGroup());
        buffer.writeEnum(recipe.category());
        // skipping ingredients for now
        ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        buffer.writeBoolean(recipe.showNotification());
        // sync remaining non-ingredient elements
        MATERIAL_FIELD.encode(buffer, recipe);

        // save memory and ensure instance matching by syncing only unique ingredients
        List<Ingredient> inputs = recipe.getIngredients();
        List<Ingredient> distinct = inputs.stream().unordered().distinct().toList();
        buffer.writeVarInt(distinct.size());
        for (Ingredient ingredient : distinct) {
          Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
        }
        // to sync inputs, we just sync the index within the distinct list
        for (Ingredient ingredient : inputs) {
          buffer.writeByte(distinct.indexOf(ingredient));
        }
        // parts size is not determined from ingredients size, so sync it directly
        buffer.writeVarInt(recipe.parts.size());
        for (Ingredient ingredient : recipe.parts) {
          buffer.writeByte(distinct.indexOf(ingredient));
        }
      },
      buffer -> {
        // shaped syncing
        int width = buffer.readVarInt();
        int height = buffer.readVarInt();
        String group = buffer.readUtf();
        CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
        // skipping ingredients for now
        ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
        boolean showNotification = buffer.readBoolean();
        // fetch remaining non-ingredient elements
        List<MaterialVariantId> extraMaterials = MATERIAL_FIELD.decode(buffer);

        // start syncing ingredients back over, they are distinct so we will need to rematch them
        int size = buffer.readVarInt();
        List<Ingredient> distinct = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
          distinct.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
        }

        // form inputs and parts lists
        NonNullList<Ingredient> inputs = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int i = 0; i < inputs.size(); i++) {
          inputs.set(i, LogicHelper.getOrDefault(distinct, buffer.readByte(), Ingredient.EMPTY));
        }
        size = buffer.readVarInt();
        // read in parts
        List<Ingredient> parts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
          parts.add(i, LogicHelper.getOrDefault(distinct, buffer.readByte(), Ingredient.EMPTY));
        }
        // the key map is not synced, so the pattern gets no source data and the recipe no part symbols
        ShapedRecipePattern pattern = new ShapedRecipePattern(width, height, inputs, Optional.empty());
        return new ShapedMaterialsRecipe(group, category, pattern, result, showNotification, "", List.copyOf(parts), extraMaterials);
      });

    /** Resolves the part symbols against the key and builds the pattern */
    private static DataResult<ShapedMaterialsRecipe> unpack(Raw raw) {
      // specific to shaped part recipe, map from a pattern string to the ingredients for each character
      // saves memory by not having separate copies of each, plus simplifies the JSON
      Map<Character,Ingredient> key = raw.pattern().key();
      List<Ingredient> parts = new ArrayList<>();
      for (int i = 0; i < raw.parts().length(); i++) {
        char sym = raw.parts().charAt(i);
        Ingredient ingredient = key.get(sym);
        if (ingredient == null) {
          return DataResult.error(() -> "Parts references symbol '" + sym + "' but it's not defined in the key");
        }
        parts.add(ingredient);
      }
      ShapedRecipePattern pattern;
      try {
        pattern = ShapedRecipePattern.of(key, raw.pattern().pattern());
      } catch (IllegalStateException e) {
        return DataResult.error(e::getMessage);
      }
      return DataResult.success(new ShapedMaterialsRecipe(raw.group(), raw.category(), pattern, raw.result(), raw.showNotification(),
                                                          raw.parts(), List.copyOf(parts), raw.extraMaterials()));
    }

    @Override
    public MapCodec<ShapedMaterialsRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf,ShapedMaterialsRecipe> streamCodecSafe() {
      return STREAM_CODEC;
    }
  }
}
