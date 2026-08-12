package slimeknights.tconstruct.library.tools.nbt;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.data.loadable.common.NBTLoadable;
import slimeknights.mantle.data.loadable.primitive.BooleanLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;

import javax.annotation.Nullable;

/**
 * Value of the {@code tconstruct:tool} data component: everything about a tool that is saved.
 * <p>
 * This is the whole of what {@link ToolStack} used to keep in the stack's tag under {@code tic_materials},
 * {@code tic_upgrades}, {@code tic_persistent} and {@code tic_broken}, minus durability, which
 * {@code minecraft:damage} owns in 1.21. Everything a tool computes from these lives in
 * {@link ToolStatsComponent} instead and is deliberately not saved.
 * <p>
 * The component participates in stacking and in {@link ItemStack#isSameItemSameComponents}, which is correct and
 * intended: two tools are the same tool exactly when they are made of the same materials, carry the same modifiers,
 * hold the same modifier data and are equally broken.
 *
 * @param materials  Materials the tool is built from, empty for a tool with no parts
 * @param upgrades   Modifiers added by recipes. Traits from the tool and its materials are not here, they are
 *                   recomputed into {@link ToolStatsComponent#modifiers()}
 * @param data       Modifier data that survives a stat rebuild, the backing compound of a {@link ToolDataNBT}.
 *                   Treat as immutable: {@link #mutableData()} is the only supported way to edit it
 * @param broken     Whether the tool is broken. Kept beside the data it invalidates rather than derived from damage,
 *                   because the durability it would be compared against lives in {@link ToolStatsComponent}
 */
public record ToolDataComponent(MaterialNBT materials, ModifierNBT upgrades, CompoundTag data, boolean broken) {
  /** Tool with nothing on it. A stack carrying this is indistinguishable from a stack carrying no component at all, so {@link ToolStack} stores it as absence. */
  public static final ToolDataComponent EMPTY = new ToolDataComponent(MaterialNBT.EMPTY, ModifierNBT.EMPTY, new CompoundTag(), false);

  /**
   * Loadable for the component, and so the definition of the on disk, on network and in datapack shape of a tool.
   * Every field defaults and none writes its default, so an empty tool is an empty object and a plain built tool is
   * just its material list.
   */
  public static final RecordLoadable<ToolDataComponent> LOADABLE = RecordLoadable.create(
    MaterialNBT.LOADABLE.defaultField("materials", MaterialNBT.EMPTY, ToolDataComponent::materials),
    ModifierNBT.LOADABLE.defaultField("upgrades", ModifierNBT.EMPTY, ToolDataComponent::upgrades),
    NBTLoadable.DISALLOW_STRING.defaultField("data", EMPTY.data(), ToolDataComponent::data),
    BooleanLoadable.INSTANCE.defaultField("broken", false, ToolDataComponent::broken),
    ToolDataComponent::new);
  /** Codec for the persistent half of the component type */
  public static final Codec<ToolDataComponent> CODEC = LOADABLE.codec();
  /** Stream codec for the network half of the component type. A loadable is already a stream codec (Mantle M4 section 1). */
  public static final StreamCodec<RegistryFriendlyByteBuf,ToolDataComponent> STREAM_CODEC = LOADABLE;

  /** Reads the component off a stack, giving {@link #EMPTY} when the stack is not a tool or has never been built */
  public static ToolDataComponent get(ItemStack stack) {
    return stack.getOrDefault(ToolComponents.TOOL, EMPTY);
  }

  /**
   * Writes this component onto a stack, storing {@link #EMPTY} as absence.
   * Removing rather than storing an empty component keeps a freshly created tool equal to one that has been read and
   * written back unchanged, which stacking depends on.
   */
  public void set(ItemStack stack) {
    if (isEmpty()) {
      stack.remove(ToolComponents.TOOL);
    } else {
      stack.set(ToolComponents.TOOL, this);
    }
  }

  /** Checks whether this holds nothing at all */
  public boolean isEmpty() {
    return materials.isEmpty() && upgrades.isEmpty() && data.isEmpty() && !broken;
  }

  /**
   * Gets an editable copy of the persistent modifier data.
   * The copy is what makes {@link #data()} safe to hand out by reference: a {@link ModDataNBT} writes straight through
   * to the compound it wraps, so it must never be given the component's own.
   */
  public ToolDataNBT mutableData() {
    return ToolDataNBT.readFromNBT(data.copy());
  }

  /** Creates a copy of this component with new materials */
  public ToolDataComponent withMaterials(MaterialNBT materials) {
    return new ToolDataComponent(materials, upgrades, data, broken);
  }

  /** Creates a copy of this component with new upgrades */
  public ToolDataComponent withUpgrades(ModifierNBT upgrades) {
    return new ToolDataComponent(materials, upgrades, data, broken);
  }

  /** Creates a copy of this component with a new broken state */
  public ToolDataComponent withBroken(boolean broken) {
    return this.broken == broken ? this : new ToolDataComponent(materials, upgrades, data, broken);
  }

  /** Creates a copy of this component with new persistent modifier data, taking ownership of the passed compound */
  public ToolDataComponent withData(@Nullable CompoundTag data) {
    return new ToolDataComponent(materials, upgrades, data == null ? new CompoundTag() : data, broken);
  }
}
