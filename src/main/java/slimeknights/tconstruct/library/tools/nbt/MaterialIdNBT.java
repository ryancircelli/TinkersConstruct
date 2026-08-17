package slimeknights.tconstruct.library.tools.nbt;

import com.google.common.collect.ImmutableList;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.primitive.StringLoadable;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Similar to {@link slimeknights.tconstruct.library.tools.nbt.MaterialNBT}, but does not check materials against the registry.
 * Used for rendering so we can have client side only materials for UIs. Anything logic based should use the regular material NBT
 */
@EqualsAndHashCode
@ToString
public class MaterialIdNBT {
  /** Instance containing no materials, for errors with parsing NBT */
  public final static MaterialIdNBT EMPTY = new MaterialIdNBT(ImmutableList.of());

  /**
   * Loadable for the ID list. Unlike {@link MaterialNBT#LOADABLE} an unparsable entry is dropped rather than replaced
   * with unknown, which is what the tag reader did: this list is for rendering, where a missing entry falls back to
   * the default texture, and an unknown entry would render as the unknown material instead.
   */
  public static final Loadable<MaterialIdNBT> LOADABLE = StringLoadable.DEFAULT.list(0).flatXmap(
    list -> {
      List<MaterialVariantId> materials = list.stream().map(MaterialVariantId::tryParse).filter(Objects::nonNull).toList();
      return materials.isEmpty() ? EMPTY : new MaterialIdNBT(materials);
    },
    nbt -> nbt.materials.stream().map(MaterialVariantId::toString).toList());

  /** List of materials contained in this NBT */
  @Getter
  private final List<MaterialVariantId> materials;

  /** Creates a new material NBT */
  public MaterialIdNBT(List<? extends MaterialVariantId> materials) {
    this.materials = ImmutableList.copyOf(materials);
  }

  /**
   * Gets the material at the given index
   * @param index  Index
   * @return  Material, or unknown if index is invalid
   */
  public MaterialVariantId getMaterial(int index) {
    if (index >= materials.size() || index < 0) {
      return IMaterial.UNKNOWN_ID;
    }
    return materials.get(index);
  }

  /** Resolves all redirects, replacing with material redirects */
  public MaterialIdNBT resolveRedirects() {
    boolean changed = false;
    ImmutableList.Builder<MaterialVariantId> builder = ImmutableList.builder();
    IMaterialRegistry registry = MaterialRegistry.getInstance();
    for (MaterialVariantId id : materials) {
      MaterialId original = id.getId();
      MaterialId resolved = registry.resolve(original);
      if (resolved != original) {
        changed = true;
      }
      builder.add(MaterialVariantId.create(resolved, id.getVariant()));
    }
    // return a new instance only if things changed
    if (changed) {
      return new MaterialIdNBT(builder.build());
    }
    return this;
  }

  /**
   * Parses the material list from NBT
   * @param nbt  NBT instance
   * @return  MaterialNBT instance
   */
  public static MaterialIdNBT readFromNBT(@Nullable Tag nbt) {
    if (nbt == null || nbt.getId() != Tag.TAG_LIST) {
      return EMPTY;
    }
    ListTag listNBT = (ListTag) nbt;
    if (listNBT.getElementType() != Tag.TAG_STRING) {
      return EMPTY;
    }
    return LOADABLE.convert(NbtOps.INSTANCE, nbt, "materials");
  }

  /** Creates an ID list from a resolved material list */
  public static MaterialIdNBT of(MaterialNBT materials) {
    if (materials.isEmpty()) {
      return EMPTY;
    }
    return new MaterialIdNBT(materials.getList().stream().map(MaterialVariant::getVariant).toList());
  }

  /**
   * Writes this material list to NBT
   * @return  List of materials
   */
  public ListTag serializeToNBT() {
    return (ListTag)LOADABLE.serialize(NbtOps.INSTANCE, this);
  }

  /**
   * Parses the material list from a stack's tool component
   * @param stack  Tool stack instance
   * @return  MaterialNBT instance
   */
  public static MaterialIdNBT from(ItemStack stack) {
    return of(ToolDataComponent.get(stack).materials());
  }

  /** Writes this material list into the given stack's tool component, leaving the rest of the component alone */
  public ItemStack updateStack(ItemStack stack) {
    ToolDataComponent.get(stack).withMaterials(toMaterialNBT()).set(stack);
    return stack;
  }

  /** Writes this material list into the given tool component */
  public ToolDataComponent updateComponent(ToolDataComponent component) {
    return component.withMaterials(toMaterialNBT());
  }

  /** Resolves this list against the material registry. Client only ID lists become unknown materials. */
  public MaterialNBT toMaterialNBT() {
    if (materials.isEmpty()) {
      return MaterialNBT.EMPTY;
    }
    return new MaterialNBT(materials.stream().map(MaterialVariant::of).toList());
  }
}
