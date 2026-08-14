package slimeknights.tconstruct.library.tools.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.context.EquipmentIterator.EquipmentEntry;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static slimeknights.tconstruct.common.TinkerTags.Items.MODIFIABLE;

/**
 * Context for a modifier hook that runs on multiple equipment slots
 * @apiNote  1.21 added {@link EquipmentSlot#BODY} for animal armor, so the per-slot arrays are no longer six long and
 *           are sized from the enum rather than counted by hand. They are indexed by {@link EquipmentSlot#ordinal()}
 *           for the same reason as {@code ModifierMaxLevel}: {@link EquipmentSlot#getFilterFlag()} happens to be dense
 *           today, but it is a bit position in an unrelated protocol and nothing keeps it that way. An entity that is
 *           not an animal simply has an empty stack in the body slot, so nothing needs to filter it out.
 */
@RequiredArgsConstructor
public class EquipmentContext {
  /** Entity who changed equipment */
  @Getter
  private final LivingEntity entity;
  /** Determines if the tool in the given slot was fetched */
  protected final boolean[] fetchedTool = new boolean[EquipmentSlot.values().length];
  /** Array of tools currently on the entity */
  protected final IToolStackView[] toolsInSlots = new IToolStackView[EquipmentSlot.values().length];
  /** Cached tinker data capability, saves capability lookup times slightly. Null is a valid value, hence the flag beside it */
  @Nullable
  private TinkerDataCapability.Holder tinkerData = null;
  /** Determines whether {@link #tinkerData} was fetched */
  private boolean fetchedData = false;

  /** Creates a context with an existing tool instance */
  public static EquipmentContext withTool(LivingEntity living, IToolStackView tool, EquipmentSlot slot) {
    EquipmentContext context = new EquipmentContext(living);
    int index = slot.ordinal();
    context.toolsInSlots[index] = tool;
    context.fetchedTool[index] = true;
    return context;
  }

  /** Gets a tool stack if the stack is modifiable, null otherwise */
  @Nullable
  protected static IToolStackView getToolStackIfModifiable(ItemStack stack) {
    if (!stack.isEmpty() && stack.is(MODIFIABLE)) {
      return ToolStack.from(stack);
    }
    return null;
  }

  /** Gets the level for this context */
  public Level getLevel() {
    return entity.level();
  }

  /**
   * Gets the tool stack in the given slot
   * @param slotType  Slot type
   * @return  Tool stack in the given slot, or null if the slot is not modifiable
   */
  @Nullable
  public IToolStackView getToolInSlot(EquipmentSlot slotType) {
    int index = slotType.ordinal();
    if (!fetchedTool[index]) {
      toolsInSlots[index] = getToolStackIfModifiable(entity.getItemBySlot(slotType));
      fetchedTool[index] = true;
    }
    return toolsInSlots[index];
  }

  /** Same as {@link #getToolInSlot(EquipmentSlot)}, but validates that the tool can be used in this slot */
  @Nullable
  public IToolStackView getValidTool(EquipmentSlot slotType) {
    return ModifierUtil.validArmorSlot(entity, slotType) ? getToolInSlot(slotType) : null;
  }

  /** Checks if any of the armor items are modifiable, limiting to the passed slots. Filters out holding armor to get its effects. */
  public boolean hasModifiableArmor(EquipmentSlot... slots) {
    for (EquipmentSlot slotType : slots) {
      if (getValidTool(slotType) != null) {
        return true;
      }
    }
    return false;
  }

  /** Checks if any of the armor items are modifiable. Filters out holding armor to get its effects. */
  public boolean hasModifiableArmor() {
    return hasModifiableArmor(EquipmentSlot.values());
  }

  /**
   * Gets the tinker data capability, or null if absent.
   * @apiNote Replaces {@code getTinkerData()}, which returned a {@code LazyOptional}. 1.21 deleted that type along
   * with the capability system that produced it; a capability query now simply returns null when it is absent, so
   * there is nothing left to wrap and this is the only accessor. A caller that used
   * {@code getTinkerData().ifPresent(data -> ...)} becomes a null check, and one that used
   * {@code LogicHelper.orElseNull(getTinkerData())} just drops the call.
   */
  @Nullable
  public TinkerDataCapability.Holder getDataHolder() {
    if (!fetchedData) {
      tinkerData = TinkerDataCapability.getData(entity);
      fetchedData = true;
    }
    return tinkerData;
  }


  /* Iteration */
  private Iterable<EquipmentEntry> toolIterable, armorIterable, wornArmorIterable;

  /** Gets all tools from the given function */
  public Iterable<EquipmentEntry> makeIterable(Function<EquipmentSlot,IToolStackView> getter) {
    List<IToolStackView> tools = new ArrayList<>(EquipmentSlot.values().length);
    List<EquipmentSlot> slots = new ArrayList<>(EquipmentSlot.values().length);
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      IToolStackView tool = getter.apply(slot);
      if (tool != null && !tool.isBroken() && !tool.getModifiers().isEmpty()) {
        tools.add(tool);
        slots.add(slot);
      }
    }
    return EquipmentIterator.iterable(tools, slots);
  }

  /** Iterates all non-broken tools that are in a valid slot in this context. */
  public Iterable<EquipmentEntry> iterateTools() {
    if (toolIterable == null) {
      toolIterable = makeIterable(this::getValidTool);
    }
    return toolIterable;
  }

  /** Iterates all non-broken armor in a valid slot in this context. */
  public Iterable<EquipmentEntry> iterateArmor() {
    if (armorIterable == null) {
      armorIterable = makeIterable(slot -> entity.getItemBySlot(slot).is(TinkerTags.Items.ARMOR) ? getValidTool(slot) : null);
    }
    return armorIterable;
  }

  /** Iterates all non-broken armor in a valid slot in this context. */
  public Iterable<EquipmentEntry> iterateWornArmor() {
    if (wornArmorIterable == null) {
      wornArmorIterable = makeIterable(slot -> slot.isArmor() ? getToolInSlot(slot) : null);
    }
    return wornArmorIterable;
  }
}
