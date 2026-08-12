package slimeknights.tconstruct.library.tools.nbt;

import lombok.Getter;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus.Internal;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierTraitHook.TraitBuilder;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.context.ToolRebuildContext;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionData;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.material.MissingMaterialsToolHook;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import javax.annotation.Nullable;
import java.lang.ref.Cleaner;
import java.util.Collections;
import java.util.List;

/**
 * A tool, as a builder over the two tool data components.
 *
 * <h2>Write back</h2>
 * In 1.20 this class wrapped the item stack's own {@code CompoundTag}, so every setter was immediately visible on the
 * stack and there was nothing to commit. Components are values, not shared mutable state, so that write through is
 * gone: a {@code ToolStack} now holds its own copy of the tool and every edit stays local until
 * {@link #updateStack(ItemStack)} runs. Nothing about the API forced that change, and nothing about the API announces
 * it, which is why {@link #mutable(ItemStack)} exists (see T-A1) and why a tool that was edited and never written back
 * complains in a development run (see {@link #TRACK_EDITS}).
 *
 * <h2>Durability</h2>
 * Damage is {@code minecraft:damage} now; there is no {@code Damage} key here and no Tinkers owned copy of it. The
 * value is still read into a field and committed with everything else, so that a detached tool - a copy, or a tool
 * being built - has somewhere to keep it, and so that the commit is one operation rather than one plus damage.
 *
 * <h2>Derived data</h2>
 * The stats, multipliers, merged modifiers and volatile data are a {@link ToolStatsComponent}, and a tool that has
 * never successfully rebuilt has none. That is the whole of the "registries not ready" story: {@link #rebuildStats()}
 * gives up rather than writing an empty answer, and {@link #updateStack(ItemStack)} then leaves whatever the stack
 * already had. See {@link #rebuildStats()}.
 */
public class ToolStack implements IToolStackView {
  /** Error messages for when there are not enough remaining modifiers */
  private static final String KEY_VALIDATE_SLOTS = TConstruct.makeTranslationKey("recipe", "modifier.validate_slots");

  /**
   * Whether an edited but uncommitted tool is reported. Captured into a {@code static final} so a production build
   * folds every use of it away and pays nothing; {@link SharedConstants#IS_RUNNING_IN_IDE} is NeoForge's
   * {@code !FMLLoader.isProduction()}.
   */
  private static final boolean TRACK_EDITS = SharedConstants.IS_RUNNING_IN_IDE;
  /** Cleaner backing the edit tracker. Only created in a development run, so a production build never starts its thread. */
  @Nullable
  private static final Cleaner CLEANER = TRACK_EDITS ? Cleaner.create() : null;

  /** Item representing this tool */
  @Getter
  private final Item item;
  /** Tool definition, describing part count and alike */
  @Getter
  private final ToolDefinition definition;

  /**
   * Stack this tool was taken from and will be written back to, or null for a tool nobody else holds.
   * Only a tool from {@link #mutable(ItemStack)} has one; {@link #from(ItemStack)}, {@link #copyFrom(ItemStack)},
   * {@link #copy()} and {@link #createTool} all hand back a detached tool on purpose.
   */
  @Nullable
  private final ItemStack boundStack;
  /** Tracker for the dev mode leak assertion, null in production and for a tool that was never anybody's */
  @Nullable
  private final EditTracker tracker;

  // persistent tool data: these describe the tool and are saved
  /** Data object containing materials */
  private MaterialNBT materials;
  /** Upgrades are modifiers that come from recipes. Abilities are included with these */
  private ModifierNBT upgrades;
  /** Data object containing modifier data that persists on stat rebuild */
  private ToolDataNBT persistentModData;
  /** If true, tool is broken */
  private boolean broken;
  /** Current damage of the tool, the value of {@code minecraft:damage} */
  private int damage;
  /** If true, the stack carried {@code minecraft:unbreakable}. Read only, Tinkers never sets it. */
  private boolean unbreakable;
  /** The tool's {@code minecraft:custom_data}, for the raw data modifier hook. Lazily loaded as most tools have none. */
  @Nullable
  private RawDataNBT rawData;

  // derived tool data: these are computed from the above and are never saved
  /** Data object containing the original tool stats, null if never computed */
  @Nullable
  private StatsNBT stats;
  /** Data object containing stat multipliers for each stat, null if never computed */
  @Nullable
  private MultiplierNBT multipliers;
  /** Combination of modifiers from upgrades and material traits, null if never computed */
  @Nullable
  private ModifierNBT modifiers;
  /** Data object containing modifier data that is recreated when the modifier list changes, null if never computed */
  @Nullable
  private IModDataView volatileModData;


  /* Creating */

  private ToolStack(Item item, ToolDefinition definition, @Nullable ItemStack boundStack, ToolDataComponent persistent,
                    @Nullable ToolStatsComponent derived, int damage, boolean unbreakable, boolean track) {
    this.item = item;
    this.definition = definition;
    this.boundStack = boundStack;
    this.materials = persistent.materials();
    this.upgrades = persistent.upgrades();
    this.persistentModData = persistent.mutableData();
    this.broken = persistent.broken();
    this.damage = damage;
    this.unbreakable = unbreakable;
    if (derived != null) {
      this.stats = derived.stats();
      this.multipliers = derived.multipliers();
      this.modifiers = derived.modifiers();
      this.volatileModData = derived.volatileView();
    }
    if (track && CLEANER != null) {
      EditTracker tracker = new EditTracker(boundStack != null, item);
      CLEANER.register(this, tracker);
      this.tracker = tracker;
    } else {
      this.tracker = null;
    }
  }

  /**
   * Creates a new tool stack from item, definition and saved tool data.
   * The result is detached: it is not bound to any stack, so {@link #updateStack(ItemStack)} has to be told where to go.
   * @param item        Item instance
   * @param definition  Item tool definition
   * @param persistent  Saved tool data
   * @return  Tool stack instance
   */
  public static ToolStack from(Item item, ToolDefinition definition, ToolDataComponent persistent) {
    return new ToolStack(item, definition, null, persistent, null, 0, false, false);
  }

  /** Reads the tool definition off an item, {@link ToolDefinition#EMPTY} for an item that is not modifiable */
  private static ToolDefinition definitionOf(Item item) {
    return item instanceof IModifiable mod ? mod.getToolDefinition() : ToolDefinition.EMPTY;
  }

  /** Shared body of the two stack factories */
  private static ToolStack from(ItemStack stack, @Nullable ItemStack boundStack) {
    Item item = stack.getItem();
    ToolDefinition definition = definitionOf(item);
    if (definition == ToolDefinition.EMPTY && boundStack != null && !stack.has(ToolComponents.TOOL)) {
      // only a wrongly made tool has an empty definition, and only a writer of an unbuilt one will notice,
      // as a reader just sees an empty tool. Matches the 1.20 check, which fired only for a stack with no tag at all
      switch (Config.COMMON.logInvalidToolStack.get()) {
        case STACKTRACE ->
          TConstruct.LOG.warn("Tool stack constructed using non-modifiable tool, this may cause issues as it has no tool data. Stacktrace can be disabled in config.", new Exception("Stack trace"));
        case WARNING ->
          TConstruct.LOG.warn("Tool stack constructed using non-modifiable tool, this may cause issues as it has no tool data. To debug this issue or disable the warning, use logInvalidToolStack in the config.");
      }
    }
    return new ToolStack(item, definition, boundStack, ToolDataComponent.get(stack), ToolStatsComponent.get(stack),
                         stack.getOrDefault(DataComponents.DAMAGE, 0), stack.has(DataComponents.UNBREAKABLE), true);
  }

  /**
   * Creates a read only view of the given item stack, not changing the stack in any way.
   * Prefer this over {@link #mutable(ItemStack)} whenever the tool is only read, as it prevents accidentally editing a
   * tool you do not own. The view is a snapshot: it does not track later changes to {@code stack}, and it has nowhere
   * to write, so an edit made through {@link IToolStackView} goes nowhere. In a development run such an edit is
   * reported when the view is collected.
   * @param stack  Stack
   * @return  Read only view of the stack
   */
  public static IToolStackView from(ItemStack stack) {
    return from(stack, null);
  }

  /**
   * Creates a mutable tool stack bound to the given item stack.
   * Changes made through it are local until {@link #updateStack()} or {@link #updateStack(ItemStack)} commits them.
   * That is the one behavioural difference from 1.20 that no signature expresses; a bound tool that is edited and
   * collected without being committed is reported in a development run.
   * @param stack  Stack
   * @return  Mutable tool stack bound to the passed stack
   */
  public static ToolStack mutable(ItemStack stack) {
    return from(stack, stack);
  }

  /**
   * Creates a detached tool stack from the given item stack, so edits reach neither the stack nor anything else until
   * they are written somewhere explicitly.
   * @param stack  Stack
   * @return  Tool stack
   */
  public static ToolStack copyFrom(ItemStack stack) {
    Item item = stack.getItem();
    return new ToolStack(item, definitionOf(item), null, ToolDataComponent.get(stack), ToolStatsComponent.get(stack),
                         stack.getOrDefault(DataComponents.DAMAGE, 0), stack.has(DataComponents.UNBREAKABLE), false);
  }

  /**
   * Creates a new tool stack for a completely new tool
   * @param item        Item
   * @param definition  Tool definition
   * @param materials   Materials list
   * @return  Tool stack
   */
  public static ToolStack createTool(Item item, ToolDefinition definition, MaterialNBT materials) {
    ToolStack tool = from(item, definition, ToolDataComponent.EMPTY);
    // update the materials, this will also rebuild the stats
    tool.setMaterials(materials);
    return tool;
  }

  /**
   * Creates a detached copy of this tool.
   * @return  Copy of this tool
   */
  public ToolStack copy() {
    ToolStack tool = new ToolStack(item, definition, null, getPersistentComponent(), getStatsComponent(), damage, unbreakable, false);
    if (rawData != null) {
      tool.rawData = new RawDataNBT(rawData.getData().copy());
    }
    return tool;
  }

  /** Clears all derived data, forcing it to be recomputed */
  public void clearCache() {
    this.stats = null;
    this.multipliers = null;
    this.modifiers = null;
    this.volatileModData = null;
  }

  /** Reloads this tool from the given item stack, discarding any uncommitted edits */
  @Internal
  public void refresh(ItemStack stack) {
    ToolDataComponent persistent = ToolDataComponent.get(stack);
    this.materials = persistent.materials();
    this.upgrades = persistent.upgrades();
    this.persistentModData = persistent.mutableData();
    this.broken = persistent.broken();
    this.damage = stack.getOrDefault(DataComponents.DAMAGE, 0);
    this.unbreakable = stack.has(DataComponents.UNBREAKABLE);
    this.rawData = null;
    ToolStatsComponent derived = ToolStatsComponent.get(stack);
    if (derived == null) {
      clearCache();
    } else {
      this.stats = derived.stats();
      this.multipliers = derived.multipliers();
      this.modifiers = derived.modifiers();
      this.volatileModData = derived.volatileView();
    }
    if (tracker != null) {
      tracker.reset();
    }
  }


  /* Writing */

  /** Gets the saved half of this tool as a component value */
  public ToolDataComponent getPersistentComponent() {
    return new ToolDataComponent(materials, upgrades, persistentModData.getData().copy(), broken);
  }

  /**
   * Gets the computed half of this tool as a component value, or null if this tool has never successfully rebuilt.
   * A null here is what stops a tool that could not be rebuilt from overwriting a good answer with an empty one.
   */
  @Nullable
  public ToolStatsComponent getStatsComponent() {
    if (stats == null) {
      return null;
    }
    return new ToolStatsComponent(stats, getMultipliers(), getModifiers(), volatileDataTag());
  }

  /** Gets the volatile data as a compound for storing */
  private CompoundTag volatileDataTag() {
    if (volatileModData instanceof ToolDataNBT data) {
      return data.getData().copy();
    }
    return new CompoundTag();
  }

  /** Creates an item stack from this tool stack */
  public ItemStack createStack(int size) {
    return write(new ItemStack(item, size));
  }

  /** Creates an item stack from this tool stack */
  public ItemStack createStack() {
    return createStack(1);
  }

  /**
   * Writes this tool onto the stack it was taken from.
   * @return  The bound stack
   * @throws IllegalStateException  If this tool is not bound to a stack, which means it came from something other than
   *                                {@link #mutable(ItemStack)} and there is no "the stack" to write to
   */
  public ItemStack updateStack() {
    if (boundStack == null) {
      throw new IllegalStateException("Tool stack is not bound to an item stack, pass the destination to updateStack(ItemStack)");
    }
    return updateStack(boundStack);
  }

  /**
   * Writes this tool onto the given stack.
   * @param stack  Stack instance
   * @return  The passed stack
   */
  public ItemStack updateStack(ItemStack stack) {
    if (stack.getItem() != item) {
      throw new IllegalArgumentException("Wrong item in stack");
    }
    if (tracker != null && stack == boundStack) {
      tracker.committed();
    }
    return write(stack);
  }

  /** Writes every component this tool owns onto the stack */
  private ItemStack write(ItemStack stack) {
    getPersistentComponent().set(stack);
    // a tool that never computed leaves the stack's own answer alone rather than replacing it with an empty one
    ToolStatsComponent derived = getStatsComponent();
    if (derived != null) {
      derived.set(stack);
    }
    // only a damageable stack gets a damage entry, as an entry vanilla did not expect would break stacking.
    // Set directly rather than through ItemStack#setDamageValue: that setter routes through IItemExtension#setDamage,
    // which our items implement in terms of this class, so it would re-enter ToolStack halfway through writing itself
    // out. Same re-entrancy the 1.20 class avoided by assigning ItemStack#tag instead of calling setTag.
    if (stack.has(DataComponents.MAX_DAMAGE)) {
      stack.set(DataComponents.DAMAGE, damage);
    }
    if (rawData != null) {
      CustomData.set(DataComponents.CUSTOM_DATA, stack, rawData.getData().copy());
    }
    return stack;
  }

  /** Creates a stack that is a copy of the given stack with this tool written onto it */
  public ItemStack copyStack(ItemStack stack) {
    return write(stack.copy());
  }

  /** Creates a stack that is a copy of the given stack with the given size. ItemHandlerHelper#copyStackWithSize is gone in 1.21, ItemStack owns the operation now. */
  public ItemStack copyStack(ItemStack stack, int size) {
    return write(stack.copyWithCount(size));
  }

  /**
   * Gets an editable view of the tool's {@code minecraft:custom_data}, for the raw data modifier hook.
   * Replaces the 1.20 restricted tag; see {@link RawDataNBT} for why the restriction is gone.
   */
  public RawDataNBT getRawData() {
    markEdited();
    if (rawData == null) {
      rawData = boundStack == null
                ? new RawDataNBT()
                : RawDataNBT.from(boundStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
    }
    return rawData;
  }

  @Override
  public boolean isSameStack(ItemStack stack) {
    // a tool no longer shares storage with its stack, so the question this used to answer - "are changes mirrored?" -
    // is now "is this the stack updateStack will write to?"
    return boundStack == stack && (stack.isEmpty() || stack.getItem() == item);
  }

  /** Records that this tool has been edited, for the dev mode report. Folded away entirely in production. */
  private void markEdited() {
    if (tracker != null) {
      tracker.edited();
    }
  }


  /* Durability */

  @Override
  public boolean isBroken() {
    return broken;
  }

  @Override
  public boolean isUnbreakable() {
    return unbreakable;
  }

  /**
   * Sets the broken state on the tool
   * @param broken  New broken value
   */
  protected void setBrokenRaw(boolean broken) {
    markEdited();
    this.broken = broken;
  }

  /**
   * Breaks the tool
   */
  protected void breakTool() {
    setDamage(getStats().getInt(ToolStats.DURABILITY));
  }

  /**
   * Gets damage, ignoring broken checks
   * @return  Damage ignoring broken state
   */
  protected int getDamageRaw() {
    return damage;
  }

  @Override
  public int getDamage() {
    // if broken, return full damage
    int durability = getStats().getInt(ToolStats.DURABILITY);
    if (isBroken()) {
      return durability;
    }
    // ensure we never return a number larger than max
    return Math.min(damage, durability - 1);
  }

  @Override
  public int getCurrentDurability() {
    if (isBroken()) {
      return 0;
    }
    // ensure we never return a number smaller than 0
    return Math.max(0, getStats().getInt(ToolStats.DURABILITY) - damage);
  }

  @Override
  public void setDamage(int damage) {
    markEdited();
    int durability = getStats().getInt(ToolStats.DURABILITY);
    if (damage >= durability) {
      damage = Math.max(0, durability);
      setBrokenRaw(true);
    } else {
      setBrokenRaw(false);
    }
    this.damage = damage;
  }


  /* Stats */

  @Override
  public StatsNBT getStats() {
    return stats == null ? StatsNBT.EMPTY : stats;
  }

  /**
   * Sets the tool stats
   * @param stats  Stats instance
   */
  protected void setStats(StatsNBT stats) {
    markEdited();
    this.stats = stats;
    // if we no longer have enough durability, decrease the damage and mark it broken
    int newMax = stats.getInt(ToolStats.DURABILITY);
    if (damage >= newMax) {
      setDamage(newMax);
    }
  }

  @Override
  public MultiplierNBT getMultipliers() {
    return multipliers == null ? MultiplierNBT.EMPTY : multipliers;
  }

  /**
   * Sets the tool multipliers
   * @param multipliers  Stats instance
   */
  protected void setMultipliers(MultiplierNBT multipliers) {
    markEdited();
    this.multipliers = multipliers.getContainedStats().isEmpty() ? MultiplierNBT.EMPTY : multipliers;
  }


  /* Materials */

  @Override
  public MaterialNBT getMaterials() {
    if (!getDefinition().hasMaterials()) {
      return MaterialNBT.EMPTY;
    }
    return materials;
  }

  /**
   * Sets the materials without updating the tool stats
   * @param materials  New materials
   */
  protected void setMaterialsRaw(MaterialNBT materials) {
    markEdited();
    this.materials = materials;
  }

  /**
   * Sets the materials on this tool stack, updating tool stats
   * @param materials  New materials NBT
   */
  public void setMaterials(MaterialNBT materials) {
    setMaterialsRaw(materials);
    rebuildStats();
  }

  /**
   * Replaces the material at the given index
   * @param index        Index to replace
   * @param replacement  New material
   * @throws IndexOutOfBoundsException  If the index is invalid
   */
  public void replaceMaterial(int index, MaterialVariant replacement) {
    setMaterials(getMaterials().replaceMaterial(index, replacement));
  }

  /**
   * Replaces the material at the given index
   * @param index        Index to replace
   * @param replacement  New material
   * @throws IndexOutOfBoundsException  If the index is invalid
   */
  public void replaceMaterial(int index, MaterialVariantId replacement) {
    setMaterials(getMaterials().replaceMaterial(index, replacement));
  }


  /* Modifiers */

  @Override
  public ModifierNBT getUpgrades() {
    return upgrades;
  }

  /**
   * Updates the upgrades list on the tool
   * @param modifiers  New upgrades
   */
  public void setUpgrades(ModifierNBT modifiers) {
    markEdited();
    this.upgrades = modifiers;
    rebuildStats();
  }

  /**
   * Adds a single modifier to this tool
   * @param modifier  Modifier to add
   * @param level     Level to add
   */
  public void addModifier(ModifierId modifier, int level) {
    if (level <= 0) {
      throw new IllegalArgumentException("Invalid level, must be above 0");
    }
    setUpgrades(getUpgrades().withModifier(modifier, level));
  }

  /**
   * Adds a single modifier to this tool
   * @param modifier  Modifier to add
   * @param amount    Amount to add
   * @param needed    Amount needed for a full level
   */
  public void addModifierAmount(ModifierId modifier, int amount, int needed) {
    if (needed <= 0) {
      throw new IllegalArgumentException("Invalid needed, must be above 0");
    }
    if (amount > 0) {
      setUpgrades(getUpgrades().addAmount(modifier, amount, needed));
    }
  }

  /**
   * Removes a single modifier to this tool
   * @param modifier  Modifier to remove
   * @param level     Level to remove
   */
  public void removeModifier(ModifierId modifier, int level) {
    if (level <= 0) {
      throw new IllegalArgumentException("Invalid level, must be above 0");
    }
    setUpgrades(getUpgrades().withoutModifier(modifier, level));
  }

  @Override
  public ModifierNBT getModifiers() {
    return modifiers == null ? ModifierNBT.EMPTY : modifiers;
  }

  /**
   * Updates the list of all modifiers, called in {@link #rebuildStats()}
   * @param modifiers  New modifiers
   */
  protected void setModifiers(ModifierNBT modifiers) {
    markEdited();
    this.modifiers = modifiers;
  }


  /* Data */

  @Override
  public ToolDataNBT getPersistentData() {
    // this hands out a write handle, and a caller that writes through it never touches another setter,
    // so this is the write that has to be assumed rather than observed
    markEdited();
    return persistentModData;
  }

  @Override
  public IModDataView getVolatileData() {
    return volatileModData == null ? IModDataView.EMPTY : volatileModData;
  }

  /**
   * Updates the volatile mod data, called in {@link #rebuildStats()}
   * @param modData  New data
   */
  protected void setVolatileModData(ToolDataNBT modData) {
    markEdited();
    volatileModData = modData.getData().isEmpty() ? IModDataView.EMPTY : modData;
  }


  /* Utilities */

  @Nullable
  public Component tryValidate() {
    // first check slot counts
    for (SlotType slotType : SlotType.getAllSlotTypes()) {
      if (getFreeSlots(slotType) < 0) {
        return Component.translatable(KEY_VALIDATE_SLOTS, slotType.getDisplayName());
      }
    }
    // next, ensure modifiers validate
    Component result;
    for (ModifierEntry entry : getModifiers()) {
      result = entry.getHook(ModifierHooks.VALIDATE).validate(this, entry);
      if (result != null) {
        return result;
      }
    }
    // some validations should only run if the modifier was crafted on the tool
    for (ModifierEntry entry : getUpgrades()) {
      result = entry.getHook(ModifierHooks.VALIDATE_UPGRADE).validate(this, entry);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /** Called on inventory tick to ensure the tool has all required data including materials and starting slots, prevents tools with no stats from existing */
  public void ensureHasData() {
    // if we try initializing before datapacks load we will get garbage data
    if (definition.isDataLoaded()) {
      // check if missing materials; either means we have none or too few. An absent material list and an empty one are
      // the same thing to a component, and needsMaterials answers the same for both, so the two cases have merged
      MissingMaterialsToolHook missingMaterials = definition.getHook(ToolHooks.MISSING_MATERIALS);
      boolean needsMaterials = definition.hasMaterials() && missingMaterials.needsMaterials(definition, getMaterials().size());
      // build data if we either lack data (signified by no stats) or we lack materials but expect them
      if (needsMaterials || !isInitialized()) {
        // randomize materials if missing
        if (needsMaterials) {
          setMaterialsRaw(missingMaterials.fillMaterials(definition, getMaterials(), RandomSource.create()));
        }
        rebuildStats();
      }
    }
  }

  /**
   * Recalculates all derived data. Called after either the materials or modifiers list changes.
   * <p>
   * If the data a rebuild needs is not loaded, this gives up and changes nothing, which is what the 1.20 version did
   * too. The consequence is different though, and is the reason the derived component is nullable: in 1.20 the stale
   * {@code tic_stats} tag stayed on the stack, so the tool kept its last good answer for free. Here the answer lives
   * in a field that may never have been filled, so "gave up" and "computed nothing" have to stay distinguishable, or a
   * tool loaded before the datapacks would be written back with zero durability. That is what
   * {@link #getStatsComponent()} returning null expresses and what {@link #write(ItemStack)} acts on. A tool in that
   * state is fixed by the next {@link #ensureHasData()}, which the inventory tick runs.
   */
  public void rebuildStats() {
    // quick safety checks: to rebuild stats we need
    // * tool definition (contains stats and traits)
    // * material registry (to fetch material stats and traits)
    // * modifier registry (run relevant modifier hooks)
    // * item tags (control tool behaviors in various places)
    // if any of these are missing, attempting to rebuild stats may corrupt the tool's state (persistent data, damage, broken)
    if (!definition.isDataLoaded() || !MaterialRegistry.isFullyLoaded() || !ModifierManager.INSTANCE.isDynamicModifiersLoaded() || !TinkerTags.isTagsLoaded()) {
      return;
    }

    // add tool slots to volatile data, ensures it is there even from an empty tool, and properly updates on datapack update
    ToolDefinitionData toolData = getDefinitionData();

    // first, determine the list of modifiers, this is done in a couple stages
    // we start by cloning upgrades and adding tool traits and material traits
    MaterialNBT materials = getMaterials();
    ModifierNBT.Builder modBuilder = ModifierNBT.builder();
    modBuilder.add(getUpgrades());
    toolData.getHook(ToolHooks.TOOL_TRAITS).addTraits(definition, materials, modBuilder);
    ModifierNBT beforeTraits = modBuilder.build();

    // temporary context while we add modifier traits, will recreate if we have modifiers
    // clear out volatile data, mostly affects the volatile data hook
    ToolRebuildContext context = new ToolRebuildContext(item, definition, materials, getUpgrades(), beforeTraits, getPersistentData());

    // if we have modifiers, apply modifier traits, saves creating some builders if empty
    List<ModifierEntry> modifierList = Collections.emptyList();
    if (beforeTraits.isEmpty()) {
      // if no modifiers, just clear modifiers
      setModifiers(ModifierNBT.EMPTY);
    } else {
      modBuilder = ModifierNBT.builder();
      TraitBuilder traitBuilder = new TraitBuilder(context, modBuilder);
      traitBuilder.add(beforeTraits);

      // set the final modifier list on the tool
      ModifierNBT allMods = modBuilder.build();
      setModifiers(allMods);
      modifierList = allMods.getModifiers();
      // context for further modifier hooks
      context = context.withModifiers(allMods);
    }

    // build volatile data first, it's a parameter to the other hooks
    ToolDataNBT volatileData = new ToolDataNBT();
    toolData.getHook(ToolHooks.VOLATILE_DATA).addVolatileData(context, volatileData);
    for (ModifierEntry entry : modifierList) {
      entry.getHook(ModifierHooks.VOLATILE_DATA).addVolatileData(context, entry, volatileData);
    }
    setVolatileModData(volatileData);

    // regular stats last so we can include volatile data
    ModifierStatsBuilder statBuilder = ModifierStatsBuilder.builder();
    toolData.getHook(ToolHooks.TOOL_STATS).addToolStats(context, statBuilder);
    for (ModifierEntry entry : modifierList) {
      entry.getHook(ModifierHooks.TOOL_STATS).addToolStats(context, entry, statBuilder);
    }
    setStats(statBuilder.build());
    setMultipliers(statBuilder.buildMultipliers());

    // finally, update raw data, called last to make the parameters more convenient mostly, plus no other hooks should be responding to this data
    for (ModifierEntry entry : modifierList) {
      entry.getHook(ModifierHooks.RAW_DATA).addRawData(this, entry, getRawData());
    }
  }


  /* Static helpers */

  /** Checks whether this tool has computed its stats, used as a marker to indicate slots are not yet applied */
  public boolean isInitialized() {
    return stats != null;
  }

  /**
   * Checks if the given tool stats have been computed, used as a marker to indicate slots are not yet applied
   * @param stack  Stack to check
   * @return  True if initialized
   */
  public static boolean isInitialized(ItemStack stack) {
    return stack.has(ToolComponents.TOOL_STATS);
  }

  /**
   * Ensures the given item stack is initialized. Called in crafting hooks
   * @param stack ItemStack to initialize
   */
  public static void ensureInitialized(ItemStack stack) {
    if (stack.getItem() instanceof IModifiable modifiable) {
      ensureInitialized(stack, modifiable.getToolDefinition());
    }
  }

  /**
   * Ensures the given item stack is initialized. Intended to be called in {@link Item#onCraftedBy(ItemStack, Level, Player)}
   * @param stack           ItemStack to initialize
   * @param toolDefinition  Tool definition
   */
  public static void ensureInitialized(ItemStack stack, ToolDefinition toolDefinition) {
    // must be loaded
    if (!toolDefinition.isDataLoaded() || isInitialized(stack)) {
      return;
    }
    // time to initialize
    ToolStack tool = ToolStack.mutable(stack);
    tool.ensureHasData();
    tool.updateStack();
  }

  /**
   * Rebuilds the tool when its stack is loaded, so it is not left wrong when modifiers or materials change.
   * <p>
   * Replaces the 1.20 {@code verifyTag}. The hook it hangs off, {@code Item#verifyComponentsAfterLoad}, runs on every
   * item stack construction rather than only on the ones read back from disk, so the fast path here is the one that
   * matters: a stack that already carries {@code tconstruct:tool_stats} is left alone after one map lookup.
   * @param stack       Stack to verify
   * @param definition  Tool definition
   */
  public static void verifyComponents(ItemStack stack, ToolDefinition definition) {
    // display stacks are props, they are deliberately half built
    if (TooltipUtil.isDisplay(stack)) {
      return;
    }
    // already computed? nothing to do, and this is the case nearly every call takes
    if (isInitialized(stack)) {
      return;
    }
    ToolDataComponent persistent = ToolDataComponent.get(stack);
    // resolve all material redirects
    boolean hasMaterials = MaterialRegistry.isFullyLoaded() && !persistent.materials().isEmpty();
    if (hasMaterials) {
      MaterialIdNBT stored = MaterialIdNBT.of(persistent.materials());
      MaterialIdNBT resolved = stored.resolveRedirects();
      if (resolved != stored) {
        resolved.updateStack(stack);
      }
    }
    // only rebuild stats if we either have materials, or we don't need materials
    if (definition.isDataLoaded() && (hasMaterials || !definition.hasMaterials())) {
      ToolStack tool = ToolStack.mutable(stack);
      tool.rebuildStats();
      tool.updateStack();
    }
  }


  /**
   * Records whether a tool was edited and whether the edit was committed, and complains at collection time if it was
   * not. Lives in its own object because a {@link Cleaner} action must not reference the thing it is watching, or the
   * thing is never collected and the action never runs.
   * <p>
   * A cleaner was chosen over the two alternatives. {@code finalize} is removal-deprecated and unreliable, and it
   * cannot be made free in production. An explicit scope - {@code AutoCloseable} plus try-with-resources - reads well
   * but javac does not enforce it, so it would catch nothing that this does not while rewriting all fifty write sites
   * into a shape upstream does not use. The cleaner costs one allocation per tool taken from a stack, in a development
   * run only: {@link #TRACK_EDITS} is a {@code static final} read of a constant, so a production build folds the whole
   * mechanism, the field and the cleaner thread away.
   */
  private static final class EditTracker implements Runnable {
    private final boolean bound;
    private final Item item;
    private boolean edited;
    private boolean committed;
    /** Where the first edit happened, captured lazily so a tool that is only read pays nothing */
    @Nullable
    private Throwable origin;

    private EditTracker(boolean bound, Item item) {
      this.bound = bound;
      this.item = item;
    }

    private void edited() {
      if (!edited) {
        edited = true;
        origin = new Throwable("First edit made here");
      }
    }

    private void committed() {
      committed = true;
    }

    private void reset() {
      edited = false;
      committed = false;
      origin = null;
    }

    @Override
    public void run() {
      if (edited && !committed) {
        if (bound) {
          TConstruct.LOG.error("A mutable ToolStack for {} was edited and discarded without updateStack(). In 1.20 the edit would have reached the stack through shared NBT; it no longer does, and this tool's changes are lost.", item, origin);
        } else {
          TConstruct.LOG.error("A read only ToolStack view of {} was edited. A view has nowhere to write, so the edit is lost; take ToolStack.mutable(stack) and call updateStack() instead.", item, origin);
        }
      }
    }
  }
}
