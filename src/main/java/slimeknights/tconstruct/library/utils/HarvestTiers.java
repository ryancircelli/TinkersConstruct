package slimeknights.tconstruct.library.utils;

import com.google.common.collect.Maps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.client.ResourceColorManager;
import slimeknights.mantle.data.listener.ISafeManagerReloadListener;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Naming, ordering and display names for harvest tiers.
 *
 * @apiNote 1.21 has no tier registry of any kind. Forge's {@code TierSortingRegistry} is gone, and vanilla never had a
 * replacement: {@link Tier} is a bare interface with no name and no ordering, since both moved into the
 * {@code #minecraft:incorrect_for_*_tool} block tags each tier names. Tinkers needs both halves - it stores a tier in a
 * tool stat, writes one to a datapack and compares two of them - so it owns them here. This class already held the
 * ordering half; naming joined it rather than becoming a second registry somewhere else.
 *
 * <p>The six vanilla tiers keep the IDs and the order Forge gave them, so every {@code "minecraft:iron"} in a shipped
 * or third party datapack still reads back as {@link Tiers#IRON}. Note the order is not the declaration order of
 * {@link Tiers}: gold sits between wood and stone.
 */
public class HarvestTiers {
  private HarvestTiers() {}

  /** All registered tiers, weakest first. */
  private static final List<Tier> SORTED = new ArrayList<>();
  /** Map of ID to tier */
  private static final Map<ResourceLocation,Tier> BY_ID = new HashMap<>();
  /** Map of tier to ID. Identity based as a {@link Tier} has no contract for equality */
  private static final Map<Tier,ResourceLocation> IDS = new IdentityHashMap<>();

  static {
    // vanilla's six, in Forge's sorted order rather than the enum's declaration order
    registerVanilla("wood", Tiers.WOOD);
    registerVanilla("gold", Tiers.GOLD);
    registerVanilla("stone", Tiers.STONE);
    registerVanilla("iron", Tiers.IRON);
    registerVanilla("diamond", Tiers.DIAMOND);
    registerVanilla("netherite", Tiers.NETHERITE);
  }

  /** Cache of name for each tier */
  private static final Map<Tier, Component> harvestLevelNames = Maps.newHashMap();
  /** Listener to clear name cache so we get new colors */
  public static final ISafeManagerReloadListener RELOAD_LISTENER = manager -> harvestLevelNames.clear();


  /* Registration */

  /** Registers one of the vanilla tiers under its unnamespaced ID */
  private static void registerVanilla(String name, Tier tier) {
    register(ResourceLocation.withDefaultNamespace(name), tier);
  }

  /**
   * Registers a tier as the new strongest tier.
   * @param id    Unique ID for the tier, used in datapacks and on the network
   * @param tier  Tier instance
   * @return  The tier, for chaining
   * @throws IllegalArgumentException  If the ID or the tier is already registered
   */
  public static <T extends Tier> T register(ResourceLocation id, T tier) {
    add(id, tier, SORTED.size());
    return tier;
  }

  /**
   * Registers a tier directly above an existing tier, for a tier that is not simply the new maximum.
   * @param id     Unique ID for the tier, used in datapacks and on the network
   * @param tier   Tier instance
   * @param after  Tier this one sorts directly above; must already be registered
   * @return  The tier, for chaining
   * @throws IllegalArgumentException  If the ID or the tier is already registered, or {@code after} is not
   */
  public static <T extends Tier> T register(ResourceLocation id, T tier, Tier after) {
    int index = SORTED.indexOf(after);
    if (index < 0) {
      throw new IllegalArgumentException("Cannot sort " + id + " after unregistered tier " + after);
    }
    add(id, tier, index + 1);
    return tier;
  }

  /** Shared implementation of the two register methods */
  private static void add(ResourceLocation id, Tier tier, int index) {
    if (BY_ID.containsKey(id)) {
      throw new IllegalArgumentException("Duplicate harvest tier " + id);
    }
    if (IDS.containsKey(tier)) {
      throw new IllegalArgumentException("Harvest tier " + tier + " is already registered as " + IDS.get(tier));
    }
    BY_ID.put(id, tier);
    IDS.put(tier, id);
    SORTED.add(index, tier);
  }


  /* Naming */

  /** {@return the tier with the given ID, or null if no tier is registered under it} */
  @Nullable
  public static Tier byId(ResourceLocation id) {
    return BY_ID.get(id);
  }

  /** {@return the ID of the given tier, or null if the tier was never registered} */
  @Nullable
  public static ResourceLocation getId(Tier tier) {
    return IDS.get(tier);
  }

  /** {@return every registered tier, weakest first} */
  public static List<Tier> getSortedTiers() {
    return Collections.unmodifiableList(SORTED);
  }


  /* Comparison */

  /** Gets the larger of two tiers */
  public static Tier max(Tier a, Tier b) {
    // note indexOf returns -1 if the tier is missing, so the larger of an unsorted tier and a sorted one is the sorted one
    if (SORTED.indexOf(b) > SORTED.indexOf(a)) {
      return b;
    }
    return a;
  }

  /** Gets the smaller of two tiers */
  public static Tier min(Tier a, Tier b) {
    // note indexOf returns -1 if the tier is missing, so the smaller of an unsorted tier and a sorted one is the unsorted one
    if (SORTED.indexOf(b) < SORTED.indexOf(a)) {
      return b;
    }
    return a;
  }

  /** Gets the smallest registered tier */
  public static Tier minTier() {
    return SORTED.get(0);
  }

  /**
   * Checks whether the given tier is strong enough for the block to drop its items.
   * @apiNote Successor to {@code TierSortingRegistry#isCorrectTierForDrops}, which is gone with the registry. A tier
   * now names the blocks it is <i>not</i> good enough for, and this is the check vanilla's own {@code Tool.Rule} makes.
   */
  public static boolean isCorrectTierForDrops(Tier tier, BlockState state) {
    return !state.is(tier.getIncorrectBlocksForDrops());
  }


  /* Display */

  /** Makes a translation key for the given name */
  private static MutableComponent makeLevelKey(Tier tier) {
    ResourceLocation id = getId(tier);
    // an unregistered tier has no name to translate; it is a bug in whoever made it, so say so rather than crashing a tooltip
    String key = id != null ? Util.makeTranslationKey("harvest_tier", id) : "harvest_tier.unregistered";
    TextColor color = ResourceColorManager.getTextColor(key);
    return TConstruct.makeTranslation("stat", key).withStyle(style -> style.withColor(color));
  }

  /**
   * Gets the harvest level name for the given level number
   * @param tier  Tier
   * @return  Level name
   */
  public static Component getName(Tier tier) {
    return harvestLevelNames.computeIfAbsent(tier, n ->  makeLevelKey(tier));
  }
}
