package slimeknights.tconstruct.library.tools.definition.module.mining;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.Collection;

/** Hook for changing the mining speed beyond effectiveness and tier. */
public interface MiningSpeedToolHook {
  /** Updates the mining speed for the tool against the given state */
  float modifyDestroySpeed(IToolStackView tool, BlockState state, float speed);

  /**
   * Gets the mining speed for the tool against the given state
   * @apiNote The guard was {@code !tool.hasTag()} in 1.20, meaning "this stack has no data at all, so do not try to
   * read stats off it". A component era stack always answers that question about the stats specifically, so it asks
   * exactly that rather than about the whole stack: a tool whose stats never rebuilt has no mining speed to report.
   */
  static float getDestroySpeed(ItemStack tool, BlockState state) {
    if (!ToolStack.isInitialized(tool)) {
      return 1;
    }
    return getDestroySpeed(ToolStack.from(tool), state);
  }

  /** Gets the mining speed for the tool against the given state */
  static float getDestroySpeed(IToolStackView tool, BlockState state) {
    if (tool.isBroken()) {
      return 0.3f;
    }
    float speed = IsEffectiveToolHook.isEffective(tool, state) ? tool.getStats().get(ToolStats.MINING_SPEED) : 1;
    return Math.max(1, tool.getHook(ToolHooks.MINING_SPEED).modifyDestroySpeed(tool, state, speed));
  }

  /** Merger that runs each hook after the previous */
  record ComposeMerger(Collection<MiningSpeedToolHook> hooks) implements MiningSpeedToolHook {
    @Override
    public float modifyDestroySpeed(IToolStackView tool, BlockState state, float speed) {
      for (MiningSpeedToolHook hook : hooks) {
        speed = hook.modifyDestroySpeed(tool, state, speed);
      }
      return speed;
    }
  }
}
