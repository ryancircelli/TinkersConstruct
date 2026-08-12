package slimeknights.tconstruct.library.tools.part;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.ToolComponents;

import java.util.function.Consumer;

/**
 * Items implementing this interface contain a material
 */
public interface IMaterialItem extends ItemLike {
  /**
   * Tag used for the material ID in a {@link slimeknights.tconstruct.library.tools.part.block.MaterialBlockEntity}'s
   * NBT. It was also the item's tag key until 1.21; an item's material is {@code tconstruct:material} now.
   */
  String MATERIAL_TAG = "Material";

  /**
   * Returns the material ID of the part this itemstack holds.
   * <p>
   * A default rather than an abstract method since 1.21: the storage is a data component, so every implementation was
   * the same lookup, and {@link #setMaterialForced(ItemStack, MaterialVariantId)} has always assumed it. The pair
   * stays overridable together for an addon that wants to store the material elsewhere.
   *
   * @return Material ID or {@link IMaterial#UNKNOWN_ID} if invalid
   */
  default MaterialVariantId getMaterial(ItemStack stack) {
    return stack.getOrDefault(ToolComponents.MATERIAL, IMaterial.UNKNOWN_ID);
  }

  /** Sets the material on the existing stack. */
  default ItemStack setMaterial(ItemStack stack, MaterialVariantId material) {
    if (canUseMaterial(material.getId())) {
      return setMaterialForced(stack, material);
    }
    return stack;
  }

  /** Sets the material on the existing stack, bypassing the valid material check. */
  default ItemStack setMaterialForced(ItemStack stack, MaterialVariantId material) {
    // unknown is the absent value, so it is stored by removing the component rather than by writing it;
    // that keeps a materialless part stacking with a freshly created one
    if (IMaterial.UNKNOWN_ID.equals(material)) {
      stack.remove(ToolComponents.MATERIAL);
    } else {
      stack.set(ToolComponents.MATERIAL, material);
    }
    return stack;
  }

  /** Returns the item with the given material, bypassing material validation */
  default ItemStack withMaterialForDisplay(MaterialVariantId material) {
    // TODO 1.21: ditch this in favor of setMaterialForDisplay?
    return setMaterialForced(new ItemStack(this), material);
  }

  /** Returns the item with the given material, validating it */
  default ItemStack withMaterial(MaterialVariantId material) {
    return setMaterial(new ItemStack(this), material);
  }

  /**
   * Returns true if the material can be used for this toolpart
   */
  default boolean canUseMaterial(MaterialId mat) {
    return true;
  }

  /** Returns true if the material can be used for this toolpart, simply an alias for {@link #canUseMaterial(MaterialId)} */
  default boolean canUseMaterial(IMaterial mat) {
    return canUseMaterial(mat.getIdentifier());
  }

  /** Adds all variants of the material item to the given item stack list */
  default void addVariants(Consumer<ItemStack> items, String showOnlyMaterial) {
    if (MaterialRegistry.isFullyLoaded()) {
      // TODO: filter is not the best for the different material stat types
      // if a specific material is set in the config, try adding that as search tab only
      boolean added = false;
      if (!showOnlyMaterial.isEmpty()) {
        MaterialVariantId materialId = MaterialVariantId.tryParse(showOnlyMaterial);
        if (materialId != null && canUseMaterial(materialId.getId())) {
          items.accept(this.withMaterialForDisplay(materialId));
          added = true;
        }
      }
      // add all applicable materials to the tab, and possibly to serach
      if (!added) {
        for (IMaterial material : MaterialRegistry.getInstance().getVisibleMaterials()) {
          MaterialId id = material.getIdentifier();
          if (this.canUseMaterial(id)) {
            items.accept(this.withMaterial(id));
            // if filter is set we wanted just the 1 item
            if (!showOnlyMaterial.isEmpty()) {
              break;
            }
          }
        }
      }
    }
  }

  /**
   * Gets the material from a given item stack
   * @param stack  Item stack containing a material item
   * @return  Material, or unknown if none
   */
  static MaterialVariantId getMaterialFromStack(ItemStack stack) {
    if ((stack.getItem() instanceof IMaterialItem)) {
      return ((IMaterialItem) stack.getItem()).getMaterial(stack);
    }
    return IMaterial.UNKNOWN_ID;
  }

  /**
   * Gets the given item stack with this material applied
   * @param stack     Stack instance
   * @param material  Material
   * @return  Stack with material, or original stack if not a material item
   */
  static ItemStack withMaterial(ItemStack stack, MaterialVariantId material) {
    Item item = stack.getItem();
    if (item instanceof IMaterialItem materialItem) {
      return materialItem.setMaterial(stack.copy(), material);
    }
    return stack;
  }

  /**
   * Resolves the material redirect on a stack that was just loaded, if there is one to resolve.
   * <p>
   * Successor to {@code MaterialItem.verifyTag}. Its hook, {@code Item#verifyComponentsAfterLoad}, runs on every item
   * stack construction rather than only on the ones read back from disk or the network - including the ones a network
   * decode builds before the material registry has been synced - so it opens with the two cheap exits: no component,
   * or no registry yet. See {@code ToolStack#verifyComponents}, which makes the same two calls for the same reason.
   * @param stack  Stack to resolve
   */
  static void resolveRedirect(ItemStack stack) {
    MaterialVariantId material = stack.get(ToolComponents.MATERIAL);
    if (material == null || !MaterialRegistry.isFullyLoaded()) {
      return;
    }
    MaterialId original = material.getId();
    MaterialId resolved = MaterialRegistry.getInstance().resolve(original);
    if (!original.equals(resolved)) {
      stack.set(ToolComponents.MATERIAL, MaterialVariantId.create(resolved, material.getVariant()));
    }
  }
}
