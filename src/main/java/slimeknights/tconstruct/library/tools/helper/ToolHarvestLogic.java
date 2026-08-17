package slimeknights.tconstruct.library.tools.helper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.mining.HarvestEnchantmentsModifierHook;
import slimeknights.tconstruct.library.tools.context.ToolHarvestContext;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.aoe.AreaOfEffectIterator.AOEMatchType;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveToolHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.BlockSideHitListener;
import slimeknights.tconstruct.library.utils.Util;
import slimeknights.tconstruct.tools.TinkerToolActions;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Objects;

/**
 * External logic for the ToolCore that handles mining calculations and breaking blocks.
 * TODO: needs big updates
 */
public class ToolHarvestLogic {
  private ToolHarvestLogic() {}

  /**
   * Gets the amount of damage this tool should take for the given block state.
   * TODO 1.21: remove in favor of {@link #getDamage(IToolStackView, Level, BlockPos, BlockState)}
   * @param tool   Tool to check
   * @param state  State to check
   * @return  Damage to deal
   */
  public static int getDamage(ToolStack tool, Level world, BlockPos pos, BlockState state) {
    return getDamage((IToolStackView) tool, world, pos, state);
  }

  /**
   * Gets the amount of damage this tool should take for the given block state
   * @param tool   Tool to check
   * @param state  State to check
   * @return  Damage to deal
   */
  public static int getDamage(IToolStackView tool, Level world, BlockPos pos, BlockState state) {
    if (state.getDestroySpeed(world, pos) == 0 || !tool.hasTag(TinkerTags.Items.HARVEST)) {
      // tools that can shear take damage from instant break for non-fire
      return (!state.is(BlockTags.FIRE) && ModifierUtil.canPerformAction(tool, TinkerToolActions.SHEARS_DIG)) ? 1 : 0;
    }
    // if it lacks the harvest tag, it takes double damage (swords for instance)
    return tool.hasTag(TinkerTags.Items.HARVEST_PRIMARY) ? 1 : 2;
  }

  /**
   * Actually removes a block from the world. Cloned from {@link net.minecraft.server.level.ServerPlayerGameMode}
   * @param tool     Tool used in breaking
   * @param context  Harvest context
   * @return  True if the block was removed
   */
  private static boolean removeBlock(IToolStackView tool, ToolHarvestContext context) {
    Boolean removed = null;
    if (!tool.isBroken()) {
      for (ModifierEntry entry : tool.getModifierList()) {
        removed = entry.getHook(ModifierHooks.REMOVE_BLOCK).removeBlock(tool, entry, context);
        if (removed != null) {
          break;
        }
      }
    }
    // if not removed by any modifier, remove with normal forge hook
    BlockState state = context.getState();
    ServerLevel world = context.getWorld();
    BlockPos pos = context.getPos();
    if (removed == null) {
      removed = state.onDestroyedByPlayer(world, pos, context.getPlayer(), context.canHarvest(), world.getFluidState(pos));
    }
    // if removed by anything, finally destroy it
    if (removed) {
      state.getBlock().destroy(world, pos, state);
    }
    return removed;
  }

  /** @deprecated use {@link #breakBlock(ToolStack, ItemStack, ToolHarvestContext, boolean)}*/
  @Deprecated(forRemoval = true)
  protected static boolean breakBlock(ToolStack tool, ItemStack stack, ToolHarvestContext context) {
    return breakBlock(tool, stack, context, false);
  }

  /** @deprecated use {@link #breakBlock(IToolStackView, ItemStack, ToolHarvestContext, boolean)} */
  @Deprecated(forRemoval = true)
  protected static boolean breakBlock(ToolStack tool, ItemStack stack, ToolHarvestContext context, boolean eventFired) {
    return breakBlock((IToolStackView) tool, stack, context, eventFired);
  }

  /**
   * Called to break a block using this tool
   * @param tool        Tool instance
   * @param stack       Stack instance for vanilla functions
   * @param context     Harvest context
   * @param eventFired  If true, {@link net.neoforged.neoforge.event.level.BlockEvent.BreakEvent} has already been fired for
   *                    this position and must not be fired a second time.
   * @return  True if broken
   * @apiNote  The parameter used to be {@code useLastXP} and did double duty: the 1.20 break event carried the block's
   * experience, so a caller that had already fired it needed the cached value back from {@link BlockSideHitListener}.
   * {@code BlockEvent.BreakEvent} has no experience in 1.21 - a block drops its own experience from
   * {@code Block#playerDestroy}, through {@code BlockDropsEvent} - so nothing is cached and nothing is popped by hand
   * here. All that survives is "do not fire the event twice".
   */
  protected static boolean breakBlock(IToolStackView tool, ItemStack stack, ToolHarvestContext context, boolean eventFired) {
    ServerPlayer player = Objects.requireNonNull(context.getPlayer());
    ServerLevel world = context.getWorld();
    BlockPos pos = context.getPos();
    GameType type = player.gameMode.getGameModeForPlayer();
    // ensures extra blocks broken get their own break event
    if (!eventFired && CommonHooks.fireBlockBreak(world, type, player, pos, context.getState()).isCanceled()) {
      return false;
    }
    // checked after the NeoForge hook, so we have to recheck
    // TODO: is this needed? Seems its called inside CommonHooks.fireBlockBreak
    if (player.blockActionRestricted(world, pos, type)) {
      return false;
    }

    // creative just removes the block
    if (player.isCreative()) {
      removeBlock(tool, context);
      return true;
    }

    // determine damage to do
    BlockState state = context.getState();
    int damage = getDamage(tool, world, pos, state);

    // remove the block
    boolean canHarvest = context.canHarvest();
    BlockEntity te = canHarvest ? world.getBlockEntity(pos) : null; // ensures tile entity is fetched so it's around for afterBlockBreak
    boolean removed = removeBlock(tool, context);

    // harvest drops. Also drops the block's experience in 1.21, so there is no separate popExperience call
    Block block = state.getBlock();
    if (removed && canHarvest) {
      block.playerDestroy(world, player, pos, state, te, stack);
    }

    // handle modifiers if not broken
    // broken means we are using "empty hand"
    if (removed && !tool.isBroken()) {
      for (ModifierEntry entry : tool.getModifierList()) {
        entry.getHook(ModifierHooks.BLOCK_BREAK).afterBlockBreak(tool, entry, context);
      }
      ToolDamageUtil.damageAnimated(tool, damage, player, EquipmentSlot.MAINHAND);
    }

    return removed;
  }

  /**
   * Breaks a secondary block.
   * TODO 1.21: remove this header in favor of {@link #breakExtraBlock(IToolStackView, ItemStack, ToolHarvestContext)}
   * @param tool      Tool instance
   * @param stack     Stack instance for vanilla functions
   * @param context   Tool harvest context
   * @return true if a block was broken.
   */
  public static boolean breakExtraBlock(ToolStack tool, ItemStack stack, ToolHarvestContext context) {
    return breakExtraBlock((IToolStackView) tool, stack, context);
  }

  /**
   * Breaks a secondary block
   * @param tool      Tool instance
   * @param stack     Stack instance for vanilla functions
   * @param context   Tool harvest context
   * @return true if a block was broken.
   */
  public static boolean breakExtraBlock(IToolStackView tool, ItemStack stack, ToolHarvestContext context) {
    // break the actual block
    if (breakBlock(tool, stack, context, false)) {
      Level world = context.getWorld();
      BlockPos pos = context.getPos();
      // need to send the event to tell the client a block was broken
      // normally this is sent within one of the block breaking hooks that is called on both sides, suppressing the packet being sent to the breaking player
      // we only break the center block client side, so need to send the event directly
      // TODO: in theory, we can use this to reduce the number of sounds playing on breaking a lot of blocks, would require sending a custom packet if we want the particles still
      world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(context.getState()));
      TinkerNetwork.getInstance().sendVanillaPacket(Objects.requireNonNull(context.getPlayer()), new ClientboundBlockUpdatePacket(world, pos));
      return true;
    }
    return false;
  }

  /**
   * Call on block break to break a block.
   * See also {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#destroyBlock(BlockPos)} (client)
   * and {@link net.minecraft.server.level.ServerPlayerGameMode#destroyBlock(BlockPos)} (server)
   * @param stack   Stack instance
   * @param pos     Position to break
   * @param player  Player instance
   * @return  True if the block break is overridden.
   * @apiNote  {@code IForgeItem#onBlockStartBreak}, the hook this used to implement, has no counterpart in NeoForge
   * 21.1 - it was not renamed, it was deleted. A caller must therefore take over the break from
   * {@link net.neoforged.neoforge.event.level.BlockEvent.BreakEvent} instead, cancelling it and calling this. That is
   * also why the calls below still pass {@code eventFired = true}: any such entry point is downstream of the event.
   */
  public static boolean handleBlockBreak(ItemStack stack, BlockPos pos, Player player) {
    // TODO: offhand harvest reconsidering
    /* this is a really dumb hack.
    // Basically when something with silktouch harvests a block from the offhand
    // the game can't detect that. so we have to switch around the items in the hands for the break call
    // it's switched back in onBlockDestroyed
    if (DualToolHarvestUtil.shouldUseOffhand(player, pos, player.getHeldItemMainhand())) {
      ItemStack off = player.getHeldItemOffhand();

      this.switchItemsInHands(player);
      // remember, off is in the mainhand now
      CompoundNBT tag = off.getOrCreateTag();
      tag.putLong(TAG_SWITCHED_HAND_HAX, player.getEntityWorld().getGameTime());
      off.setTag(tag);
    }*/

    // client can run normal block breaking
    if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
      return false;
    }

    // if broken, clear the item stack temporarily then break
    ToolStack tool = ToolStack.mutable(stack);
    Direction sideHit = BlockSideHitListener.getSideHit(player);
    ServerLevel world = serverPlayer.serverLevel();
    BlockState state = world.getBlockState(pos);
    if (tool.isBroken()) {
      // no harvest context
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      ToolHarvestContext context = new ToolHarvestContext(world, serverPlayer, state, pos, sideHit,
        !player.isCreative() && state.canHarvestBlock(world, pos, player), false);
      breakBlock(tool, ItemStack.EMPTY, context, true);
      player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    } else {
      // run standard breaking logic
      runBlockBreak(stack, tool, state, pos, sideHit, serverPlayer, null);
    }
    // both branches damage the tool through the mutable instance above, so commit once they are done
    tool.updateStack();
    return true;
  }

  /**
   * True while the extra blocks of an area of effect harvest are being broken.
   * <p>
   * Those breaks fire {@code BlockEvent.BreakEvent} individually and deliberately, so another mod's protection can
   * veto each position. On 1.20 that was harmless, because Tinkers entered block breaking through the separate
   * {@code IForgeItem#onBlockStartBreak} hook and never saw its own extra-block events. NeoForge deleted that hook
   * and {@code BreakEvent} is now the only pre-break entry point, so without this flag
   * {@link slimeknights.tconstruct.tools.logic.ToolEvents#startBlockBreak} takes each extra block as a fresh player
   * swing, runs a whole nested area of effect harvest for it, and cancels the event - which
   * {@link #breakBlock} reads as "the break was vetoed", so every extra block reported as unbroken and an AoE hammer
   * harvested exactly its centre block.
   * <p>
   * Server thread only, which is where every caller of {@link #runBlockBreak} already is.
   */
  private static boolean breakingExtraBlocks = false;

  /** @see #breakingExtraBlocks */
  public static boolean isBreakingExtraBlocks() {
    return breakingExtraBlocks;
  }

  /**
   * Called serverside to break a block and run all relevant hooks
   * @param stack    Stack used for breaking
   * @param tool     Tool for the stack
   * @param pos      Position being broken
   * @param sideHit  Side of the block being broken
   * @param player   Player breaking the block
   * @return Number of blocks broken
   */
  public static int runBlockBreak(ItemStack stack, IToolStackView tool, BlockState state, BlockPos pos, Direction sideHit, ServerPlayer player, @Nullable Projectile projectile) {
    // create contexts
    ServerLevel world = player.serverLevel();

    // add in harvest info
    // must not be broken, and the tool definition must be effective
    ToolHarvestContext context = new ToolHarvestContext(world, player, projectile, state, pos, sideHit,
                                                        !player.isCreative() && state.canHarvestBlock(world, pos, player),
                                                        IsEffectiveToolHook.isEffective(tool, state));
    // tell modifiers we are about to harvest, lets them add for instance modifiers conditioned on harvesting
    for (ModifierEntry entry : tool.getModifierList()) {
      entry.getHook(ModifierHooks.BLOCK_HARVEST).startHarvest(tool, entry, context);
    }
    // let armor change enchantments
    // TODO: should we have a hook for non-enchantment armor responses?
    ItemEnchantments originalEnchantments = HarvestEnchantmentsModifierHook.updateHarvestEnchantments(tool, stack, context);
    // need to calculate the iterator before we break the block, as we need the reference hardness from the center
    UseOnContext useContext = new UseOnContext(world, player, InteractionHand.MAIN_HAND, stack, Util.createTraceResult(pos, sideHit, false));
    Iterable<BlockPos> extraBlocks = context.isEffective() ? tool.getHook(ToolHooks.AOE_ITERATOR).getBlocks(tool, useContext, state, AOEMatchType.BREAKING) : Collections.emptyList();

    // actually break the block, run AOE if successful
    int harvested = 0;
    if (breakBlock(tool, stack, context, true)) {
      harvested += 1;
      boolean wasBreakingExtra = breakingExtraBlocks;
      breakingExtraBlocks = true;
      try {
        for (BlockPos extraPos : extraBlocks) {
          BlockState extraState = world.getBlockState(extraPos);
          // prevent calling that stuff for air blocks, could lead to unexpected behaviour since it fires events
          // this should never actually happen, but just in case some AOE is odd
          if (!extraState.isAir()) {
            // prevent mutable position leak, breakBlock has a few places wanting immutable
            if (breakExtraBlock(tool, stack, context.forPosition(extraPos.immutable(), extraState))) {
              harvested += 1;
            }
          }
        }
      } finally {
        breakingExtraBlocks = wasBreakingExtra;
      }
    }
    // restore the enchantments harvest changed
    if (originalEnchantments != null) {
      HarvestEnchantmentsModifierHook.restoreEnchantments(stack, originalEnchantments);
    }
    // alert modifiers we finished harvesting. Always run even if we broke nothing as it's important for cleanup
    for (ModifierEntry entry : tool.getModifierList()) {
      entry.getHook(ModifierHooks.BLOCK_HARVEST).finishHarvest(tool, entry, context, harvested);
    }
    return harvested;
  }

  /** Handles {@link net.minecraft.world.item.Item#mineBlock(net.minecraft.world.item.ItemStack, net.minecraft.world.level.Level, net.minecraft.world.level.block.state.BlockState, net.minecraft.core.BlockPos, net.minecraft.world.entity.LivingEntity)} for modifiable items */
  public static boolean mineBlock(ItemStack stack, Level worldIn, BlockState state, BlockPos pos, LivingEntity entityLiving) {
    if (!stack.is(TinkerTags.Items.HARVEST)) {
      return false;
    }
    ToolStack tool = ToolStack.mutable(stack);
    if (tool.isBroken()) {
      return false;
    }

    if (!worldIn.isClientSide && worldIn instanceof ServerLevel) {
      // the only branch that edits the tool, so it also owns the commit
      // must not be broken, and the tool definition must be effective
      boolean isEffective = IsEffectiveToolHook.isEffective(tool, state);
      ToolHarvestContext context = new ToolHarvestContext((ServerLevel) worldIn, entityLiving, state, pos, Direction.UP, true, isEffective);
      for (ModifierEntry entry : tool.getModifierList()) {
        entry.getHook(ModifierHooks.BLOCK_BREAK).afterBlockBreak(tool, entry, context);
      }
      ToolDamageUtil.damageAnimated(tool, ToolHarvestLogic.getDamage(tool, worldIn, pos, state), entityLiving, EquipmentSlot.MAINHAND);
      tool.updateStack();
    }

    return true;
  }
}
