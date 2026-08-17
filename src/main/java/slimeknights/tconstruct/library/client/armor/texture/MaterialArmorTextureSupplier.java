package slimeknights.tconstruct.library.client.armor.texture;

import lombok.RequiredArgsConstructor;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.mantle.data.loadable.field.LoadableField;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataComponent;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;

/** Logic to create material texture variants for armor */
@RequiredArgsConstructor
public abstract class MaterialArmorTextureSupplier implements ArmorTextureSupplier {
  /** Field for parsing the variant from JSON */
  private static final LoadableField<ResourceLocation,MaterialArmorTextureSupplier> PREFIX_FIELD = Loadables.RESOURCE_LOCATION.requiredField("prefix", m -> m.prefix);

  /** Makes a texture for the given variant and material, returns null if its missing */
  private static ArmorTexture tryTexture(ResourceLocation name, int color, int luminosity, String material) {
    ResourceLocation texture = name.withSuffix(material);
    if (TEXTURE_VALIDATOR.test(texture)) {
      return new TintedArmorTexture(ArmorTextureSupplier.getTexturePath(texture), color, luminosity);
    }
    return ArmorTexture.EMPTY;
  }

  /**
   * Makes a material getter for the given base and type.
   * <p>
   * 1.20 keyed this on the material's string form because that is what the tool's NBT held. 1.21 stores the material
   * list as parsed {@link MaterialVariantId}s in a data component, so the key is the id itself and the per-frame
   * string round trip this used to pay is gone.
   */
  public static Function<MaterialVariantId,ArmorTexture> materialGetter(ResourceLocation name) {
    // if the base texture does not exist, means we decided to skip this piece. Notably used for skipping some layers of wings
    if (!TEXTURE_VALIDATOR.test(name)) {
      return material -> ArmorTexture.EMPTY;
    }
    // TODO: consider memoizing these functions, as if the same name appears twice in different models we can reuse it
    return Util.memoize(material -> {
      int color = -1;
      int luminosity = 0;
      Optional<MaterialRenderInfo> infoOptional = MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material);
      if (infoOptional.isPresent()) {
        MaterialRenderInfo info = infoOptional.get();
        ResourceLocation untinted = info.texture();
        luminosity = info.luminosity();
        if (untinted != null) {
          ArmorTexture texture = tryTexture(name, -1, luminosity, '_' + untinted.getNamespace() + '_' + untinted.getPath());
          if (texture != ArmorTexture.EMPTY) {
            return texture;
          }
        }
        color = info.vertexColor();
        for (String fallback : info.fallbacks()) {
          ArmorTexture texture = tryTexture(name, color, luminosity, '_' + fallback);
          if (texture != ArmorTexture.EMPTY) {
            return texture;
          }
        }
      }
      // base texture guaranteed to exist, else we would not be in this function
      return new TintedArmorTexture(ArmorTextureSupplier.getTexturePath(name), color, luminosity);
    });
  }

  private final ResourceLocation prefix;
  private final Function<MaterialVariantId, ArmorTexture>[] textures;
  @SuppressWarnings("unchecked")
  public MaterialArmorTextureSupplier(ResourceLocation prefix) {
    this.prefix = prefix;
      this.textures = new Function[] {
      materialGetter(prefix.withSuffix("armor")),
      materialGetter(prefix.withSuffix("leggings")),
      materialGetter(prefix.withSuffix("wings"))
    };
  }

  /** Gets the material from a given stack, null if the stack names none */
  @Nullable
  protected abstract MaterialVariantId getMaterial(ItemStack stack);

  @Override
  public ArmorTexture getArmorTexture(ItemStack stack, TextureType textureType, RegistryAccess access) {
    MaterialVariantId material = getMaterial(stack);
    if (material != null) {
      return textures[textureType.ordinal()].apply(material);
    }
    return ArmorTexture.EMPTY;
  }

  /** Material supplier using persistent data */
  public static class PersistentData extends MaterialArmorTextureSupplier {
    public static final RecordLoadable<PersistentData> LOADER = RecordLoadable.create(
      PREFIX_FIELD,
      Loadables.RESOURCE_LOCATION.requiredField("material_key", d -> d.key),
      PersistentData::new);

    private final ResourceLocation key;

    public PersistentData(ResourceLocation prefix, ResourceLocation key) {
      super(prefix);
      this.key = key;
    }

    public PersistentData(ResourceLocation base, String suffix, ResourceLocation key) {
      this(base.withSuffix(suffix), key);
    }

    @Nullable
    @Override
    protected MaterialVariantId getMaterial(ItemStack stack) {
      // persistent data genuinely holds a string, so this is the one place a parse is still needed
      String material = ModifierUtil.getPersistentString(stack, key);
      return material.isEmpty() ? null : MaterialVariantId.tryParse(material);
    }

    @Override
    public RecordLoadable<PersistentData> getLoader() {
      return LOADER;
    }
  }

  /** Material supplier using material data */
  public static class Material extends MaterialArmorTextureSupplier {
    public static final RecordLoadable<Material> LOADER = RecordLoadable.create(
      PREFIX_FIELD,
      IntLoadable.FROM_ZERO.requiredField("index", m -> m.index),
      Material::new);

    private final int index;
    public Material(ResourceLocation prefix, int index) {
      super(prefix);
      this.index = index;
    }

    public Material(ResourceLocation base, String variant, int index) {
      this(base.withSuffix(variant), index);
    }

    @Nullable
    @Override
    protected MaterialVariantId getMaterial(ItemStack stack) {
      // 1.21: the material list is a data component, not a string list inside the stack's NBT. Read the component
      // directly rather than building a ToolStack: this runs per frame per armour layer and nothing else is wanted.
      MaterialNBT materials = ToolDataComponent.get(stack).materials();
      if (index < materials.size()) {
        return materials.get(index).getVariant();
      }
      return null;
    }

    @Override
    public RecordLoadable<Material> getLoader() {
      return LOADER;
    }
  }
}
