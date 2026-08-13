package slimeknights.tconstruct.library.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;

@AllArgsConstructor
@Getter
public abstract class TinkerToolEvent extends Event {
  private final ItemStack stack;
  private final IToolStackView tool;
  public TinkerToolEvent(ItemStack stack) {
    this.stack = stack;
    this.tool = ToolStack.from(stack);
  }

  /**
   * Base class for tool events where a listener may perform the action in place of the tool's built in behavior.
   * Replaces the tri-state {@code Event.Result} which no longer exists in the NeoForge event bus.
   */
  public static abstract class Overridable extends TinkerToolEvent {
    /** Override set by the listeners, see {@link #getOverride()} */
    private TriState override = TriState.DEFAULT;

    protected Overridable(ItemStack stack, IToolStackView tool) {
      super(stack, tool);
    }

    /**
     * Gets the override set by the listeners.
     * {@link TriState#DEFAULT} means no listener handled the action, so the tool should run its built in behavior.
     * {@link TriState#TRUE} means a listener performed the action successfully, {@link TriState#FALSE} means a listener determined the action is impossible.
     * In both non-default cases the tool's built in behavior must be skipped.
     */
    public TriState getOverride() {
      return override;
    }

    /**
     * Marks this action as handled by the listener, preventing the tool's built in behavior from running.
     * @param override  {@link TriState#TRUE} if the action succeeded, {@link TriState#FALSE} if the action is impossible.
     *                  {@link TriState#DEFAULT} restores the tool's built in behavior, notably letting a listener undo an earlier listener's override.
     */
    public void setOverride(TriState override) {
      this.override = override;
    }

    /** Fires this event on {@link NeoForge#EVENT_BUS} and returns the resulting override */
    public TriState fire() {
      return NeoForge.EVENT_BUS.post(this).getOverride();
    }
  }

  /**
   * Event fired when a kama tries to harvest a crop.
   * Set the override to {@link TriState#TRUE} if you handled the harvest yourself. Set it to {@link TriState#FALSE} if the block cannot be harvested.
   */
  @Getter
  public static class ToolHarvestEvent extends Overridable {
    /** Item context, note this is the original context, so some information (such as position) may not be accurate */
    private final UseOnContext context;
    private final ServerLevel world;
    private final BlockState state;
    private final BlockPos pos;
    private final InteractionSource source;

    public ToolHarvestEvent(IToolStackView tool, UseOnContext context, ServerLevel world, BlockState state, BlockPos pos, InteractionSource source) {
      super(getItem(context, source), tool);
      this.context = context;
      this.world = world;
      this.state = state;
      this.pos = pos;
      this.source = source;
    }

    /** Gets the item for the event */
    private static ItemStack getItem(UseOnContext context, InteractionSource source) {
      Player player = context.getPlayer();
      if (player != null) {
        return player.getItemBySlot(source.getSlot(context.getHand()));
      }
      return context.getItemInHand();
    }

    /** Gets the item for the event */
    private static ItemStack getItem(UseOnContext context, EquipmentSlot slotType) {
      Player player = context.getPlayer();
      if (player != null) {
        return player.getItemBySlot(slotType);
      }
      return context.getItemInHand();
    }

    @Nullable
    public Player getPlayer() {
      return context.getPlayer();
    }
  }

  /**
   * Event fired when a kama or scythe tries to shear an entity.
   * Set the override to {@link TriState#TRUE} if you handled the shearing yourself. Set it to {@link TriState#FALSE} if the entity cannot be sheared.
   */
  @Getter
  public static class ToolShearEvent extends Overridable {
    private final Level world;
    private final Player player;
    private final Entity target;
    private final int fortune;
    public ToolShearEvent(ItemStack stack, IToolStackView tool, Level world, Player player, Entity target, int fortune) {
      super(stack, tool);
      this.world = world;
      this.player = player;
      this.target = target;
      this.fortune = fortune;
    }
  }
}
