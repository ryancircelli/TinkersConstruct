package slimeknights.tconstruct.tools.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// TOOD: can this be removed?
public class RayTracer {
  /**
   * Creates a BlockRayTraceEvent with a block mode of COLLIDER and whatever fluid mode you pass to it.
   * Uses the corrected player's starting vector and the corrected ending vector. (Takes into account the players eye level)
   *
   * @param player the player
   * @param fluidMode the raytracing fluid mode
   * @return a BlockRayTraceResult
   */
  public static BlockHitResult retrace(Player player, ClipContext.Fluid fluidMode) {
    return retrace(player, ClipContext.Block.COLLIDER, fluidMode);
  }

  /**
   * Creates a BlockRayTraceEvent with whatever block and fluid mode you pass to it.
   * Uses the corrected player's starting vector and the corrected ending vector. (Takes into account the players eye level)
   *
   * @param player the player
   * @param blockMode the raytracing block mode to use
   * @param fluidMode the raytracing fluid mode to use
   * @return a BlockRayTraceResult
   */
  public static BlockHitResult retrace(Player player, ClipContext.Block blockMode, ClipContext.Fluid fluidMode) {
    return player.level().clip(new ClipContext(getStartVector(player), getEndVector(player), blockMode, fluidMode, player));
  }

  /**
   * Gets the starting vector
   *
   * @param player the player
   * @return the start vector
   */
  public static Vec3 getStartVector(Player player) {
    return getCorrectedHeadVector(player);
  }

  /**
   * Gets the ending vector
   *
   * @param player the player
   * @return the end vector
   */
  public static Vec3 getEndVector(Player player) {
    Vec3 headVec = getCorrectedHeadVector(player);
    Vec3 lookVec = player.getViewVector(1.0F);
    double reach = getBlockReachDistance(player);
    return headVec.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
  }

  /**
   * Gets the corrected head vector
   *
   * @param player the player
   * @return the corrected head vector
   */
  public static Vec3 getCorrectedHeadVector(Player player) {
    return new Vec3(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
  }

  /**
   * Gets the block reach distance to use for the raytrace
   *
   * @param player the player
   * @return the block reach distance
   * @implNote  This was a three way split - the client asked {@code MultiPlayerGameMode#getPickRange()}, the server
   *            read {@code ForgeMod.BLOCK_REACH}, and anything else got a hardcoded 5 - held apart by a
   *            {@code ClientOnly} holder class so the server never loaded {@link Minecraft}. The split existed because
   *            Forge's reach attribute was not synced to the client. It is vanilla's
   *            {@link net.minecraft.world.entity.ai.attributes.Attributes#BLOCK_INTERACTION_RANGE} in 1.21 and it is
   *            synced, so {@link Player#blockInteractionRange()} is the right answer on both sides and the holder
   *            class has nothing left to hold.
   */
  public static double getBlockReachDistance(Player player) {
    return player.blockInteractionRange();
  }
}
