package slimeknights.tconstruct.library.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

/**
 * Holds another block's capability across ticks, refetching it when that block says it changed.
 * <p>
 * In 1.20 this was a stored {@code LazyOptional} plus a {@code WeakConsumerWrapper} listener added to it: the neighbor
 * owned the optional, invalidated it when its handler went away, and the listener nulled the stored field.
 * {@link BlockCapabilityCache} is the 1.21 replacement for that whole pairing - the level tracks the cache by position
 * and clears it whenever {@link Level#invalidateCapabilities(BlockPos)} runs there, so nothing subscribes or
 * unsubscribes by hand, and the level's reference to the cache is weak, so the caching block entity is not kept alive
 * by having asked. That is why no site using this class needs a {@code WeakConsumerWrapper} anymore.
 * <p>
 * Two gaps in {@link BlockCapabilityCache} are what this class exists to fill:
 * <ul>
 *   <li><b>It is bound to one {@link BlockPos} for its lifetime.</b> Several callers watch a position that moves - a
 *       multiblock servant following its master, a faucet following its facing - so {@link #get} takes the position and
 *       side per call and rebuilds the underlying cache when either changes.</li>
 *   <li><b>It only exists on a {@link ServerLevel}.</b> There is nothing to subscribe to on the client, where
 *       capabilities are never invalidated by the level. Rather than return nothing there, a client call falls through
 *       to an uncached {@link Level#getCapability} query, which is what the display paths that run on both sides
 *       (fuel gauges, tank contents) need to keep working.</li>
 * </ul>
 * <p>
 * The invalidation callback exists only for callers with state <i>derived</i> from the handler - a tank count, a
 * selected fuel position - which has to be dropped when the handler does. It must not query anything: NeoForge
 * dispatches it while the neighbor is mid-invalidation and {@link BlockCapabilityCache#getCapability()} throws if
 * called from inside it. A caller that only reads the handler through {@link #get} needs no callback at all, since the
 * next {@link #get} already returns the new answer.
 * @param <T>  Capability type
 */
public class NeighborCapabilityCache<T> {
  /** Capability to fetch */
  private final BlockCapability<T,Direction> capability;
  /** Returns false once the owner should stop listening, typically {@code () -> !isRemoved()} */
  private final BooleanSupplier valid;
  /** Run when the watched position invalidates; see the class docs for what it may not do */
  private final Runnable onInvalidate;

  /** Cache for {@link #cachePos}, null when never built or dropped */
  @Nullable
  private BlockCapabilityCache<T,Direction> cache;
  /** Position {@link #cache} watches */
  @Nullable
  private BlockPos cachePos;
  /** Side {@link #cache} queries from */
  @Nullable
  private Direction cacheSide;

  public NeighborCapabilityCache(BlockCapability<T,Direction> capability, BooleanSupplier valid, Runnable onInvalidate) {
    this.capability = capability;
    this.valid = valid;
    this.onInvalidate = onInvalidate;
  }

  public NeighborCapabilityCache(BlockCapability<T,Direction> capability, BooleanSupplier valid) {
    this(capability, valid, () -> {});
  }

  /**
   * Gets the handler at the given position, building or rebuilding the cache as needed.
   * @param level  Level containing the position
   * @param pos    Position to query
   * @param side   Side to query from, or null for the sideless handler
   * @return  Handler, or null if the position has none right now
   */
  @Nullable
  public T get(Level level, BlockPos pos, @Nullable Direction side) {
    // once the owner is gone the cache is unregistered and must not be queried again
    if (!valid.getAsBoolean()) {
      clear();
      return null;
    }
    if (!(level instanceof ServerLevel server)) {
      return level.getCapability(capability, pos, side);
    }
    if (cache == null || !pos.equals(cachePos) || side != cacheSide) {
      cachePos = pos.immutable();
      cacheSide = side;
      cache = BlockCapabilityCache.create(capability, server, cachePos, side, valid, onInvalidate);
    }
    return cache.getCapability();
  }

  /**
   * Drops the cache, so the next {@link #get} builds a fresh one.
   * Only needed when something other than the neighbor's own invalidation makes the answer stale, as the level already
   * refreshes the cache for the neighbor's sake.
   */
  public void clear() {
    cache = null;
    cachePos = null;
    cacheSide = null;
  }
}
