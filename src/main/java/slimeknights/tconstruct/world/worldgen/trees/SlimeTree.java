package slimeknights.tconstruct.world.worldgen.trees;

import net.minecraft.world.level.block.grower.TreeGrower;
import slimeknights.tconstruct.world.TinkerStructures;
import slimeknights.tconstruct.world.block.FoliageType;

import java.util.Optional;

/**
 * Builds the {@link TreeGrower} instances slime saplings grow into.
 * <p>
 * 1.20's {@code AbstractTreeGrower} was a subclassable abstract class with a {@code getConfiguredFeature} hook;
 * 1.21 replaced it with a single {@code final} {@link TreeGrower} that is constructed directly rather than
 * extended, and picks between a primary and secondary {@link net.minecraft.world.level.levelgen.feature.ConfiguredFeature}
 * by a flat {@code secondaryChance} baked into the instance rather than a per-call random check. The old ender
 * slime tree's "85% tall, 15% short" split maps onto that chance directly: the secondary variant is drawn
 * {@code secondaryChance} of the time, so the tall tree - the common case - is the secondary.
 */
public final class SlimeTree {
  private SlimeTree() {}

  /**
   * Builds the tree grower for the given foliage type.
   * @param foliageType  Foliage type; must be {@link FoliageType#EARTH}, {@link FoliageType#SKY} or {@link FoliageType#ENDER} -
   *                      the other two types grow via {@link slimeknights.tconstruct.world.block.SlimeFungusBlock}, which
   *                      takes its configured feature directly and has no tree grower.
   */
  public static TreeGrower create(FoliageType foliageType) {
    return switch (foliageType) {
      case EARTH -> new TreeGrower("tconstruct_earth_slime", Optional.empty(), Optional.of(TinkerStructures.earthSlimeTree), Optional.empty());
      case SKY -> new TreeGrower("tconstruct_sky_slime", Optional.empty(), Optional.of(TinkerStructures.skySlimeTree), Optional.empty());
      case ENDER -> new TreeGrower(
        "tconstruct_ender_slime", 0.85f,
        Optional.empty(), Optional.empty(),
        Optional.of(TinkerStructures.enderSlimeTree), Optional.of(TinkerStructures.enderSlimeTreeTall),
        Optional.empty(), Optional.empty());
      case BLOOD, ICHOR -> throw new IllegalArgumentException("No tree grower for foliage type " + foliageType);
    };
  }
}
