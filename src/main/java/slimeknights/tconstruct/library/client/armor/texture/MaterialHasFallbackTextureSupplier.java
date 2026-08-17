package slimeknights.tconstruct.library.client.armor.texture;

import com.google.common.collect.ImmutableSet;
import lombok.RequiredArgsConstructor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.loadable.array.ArrayLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Texture supplier that runs the nested supplier if the material at the given index has the given fallback in its render info. */
@RequiredArgsConstructor
public class MaterialHasFallbackTextureSupplier implements ArmorTextureSupplier, Function<MaterialVariantId, Boolean> {
  public static final RecordLoadable<MaterialHasFallbackTextureSupplier> LOADER = RecordLoadable.create(
    IntLoadable.FROM_ZERO.requiredField("index", m -> m.index),
    StringLoadable.DEFAULT.set(ArrayLoadable.COMPACT).requiredField("fallback", m -> m.fallback),
    ArmorTextureSupplier.LOADER.requiredField("apply", m -> m.apply),
    MaterialHasFallbackTextureSupplier::new);

  /** Material index on the tool */
  private final int index;
  /** Set of fallback options, if any exist in the render info than {@code apply} is used */
  private final Set<String> fallback;
  /** Texture to apply if conditions are met. */
  private final ArmorTextureSupplier apply;

  /**
   * Cache of the predicate for each seen material.
   * 1.20 keyed this on the string the tool's NBT held; 1.21 keeps parsed material variants in a data component, so
   * the key is the variant itself and no string is round tripped per frame.
   */
  private final Map<MaterialVariantId,Boolean> cache = new HashMap<>();

  public MaterialHasFallbackTextureSupplier(int index, ArmorTextureSupplier apply, String... fallback) {
    this(index, ImmutableSet.copyOf(fallback), apply);
  }

  @Override
  public RecordLoadable<? extends MaterialHasFallbackTextureSupplier> getLoader() {
    return LOADER;
  }

  /** Logic to compute a given material if its not present in the cache */
  @Override
  public Boolean apply(MaterialVariantId material) {
    return MaterialRenderInfoLoader.INSTANCE.hasFallback(material, fallback);
  }

  @Override
  public ArmorTexture getArmorTexture(ItemStack stack, TextureType type, RegistryAccess access) {
    // 1.21: the material list is a data component, not a string list inside the stack's NBT
    MaterialNBT materials = ToolDataComponent.get(stack).materials();
    if (index < materials.size() && cache.computeIfAbsent(materials.get(index).getVariant(), this)) {
      return apply.getArmorTexture(stack, type, access);
    }
    return ArmorTexture.EMPTY;
  }
}
