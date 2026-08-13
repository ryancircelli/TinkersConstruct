package slimeknights.tconstruct.smeltery.block.entity.component;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import slimeknights.mantle.block.entity.IRetexturedBlockEntity;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.tconstruct.common.multiblock.IMasterLogic;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

import static slimeknights.mantle.util.RetexturedHelper.TAG_TEXTURE;

/**
 * Shared logic between drains and ducts.
 * <p>
 * This is the one cached-neighbor class in this package that is not built on
 * {@link slimeknights.tconstruct.library.utils.NeighborCapabilityCache}, and the reason is worth stating: the block
 * being watched is the multiblock's master, and for the fluid half the master exposes nothing at its own position -
 * {@link ISmelteryTankHandler#getFluidHandler()} is read off the block entity directly, precisely so a pipe cannot
 * talk to a smeltery controller. There is no registered capability there for a cache to watch. So the handler is
 * memoized with the lifetime 1.20's LazyOptional had - dropped whenever the master changes - and dropping it also
 * invalidates this block's own capability, which is what the old wrapper optional's invalidation did for whoever was
 * caching the drain.
 */
public abstract class SmelteryInputOutputBlockEntity<T> extends SmelteryComponentBlockEntity implements IRetexturedBlockEntity {
  /** Handler fetched from the master, null when the master has none */
  @Nullable
  private T handler;
  /** Separate from a null handler, which means we looked and the master had nothing */
  private boolean fetched = false;

  /* Retexturing */
  @Nonnull
  @Getter
  private Block texture = Blocks.AIR;

  protected SmelteryInputOutputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
    super(type, pos, state);
  }

  /** Clears the cached handler and tells anyone caching ours that it changed */
  private void clearHandler() {
    handler = null;
    fetched = false;
    if (level != null) {
      invalidateCapabilities();
    }
  }

  @Override
  public void onMasterLoad(IMasterLogic master) {
    clearHandler();
  }

  @Override
  protected void setMaster(@Nullable BlockPos master, @Nullable Block block) {
    assert level != null;

    // if we have a new master, invalidate handlers
    boolean masterChanged = false;
    if (!Objects.equals(getMasterPos(), master)) {
      clearHandler();
      masterChanged = true;
    }
    super.setMaster(master, block);
    // notify neighbors of the change (state change skips the notify flag)
    if (masterChanged) {
      level.blockUpdated(worldPosition, getBlockState().getBlock());
    }
  }

  /**
   * Gets the handler to expose from this IO block, given the master block entity.
   * @param parent  Parent tile entity
   * @return  Handler from the parent, or null if it has none
   */
  @Nullable
  protected abstract T fetchHandler(BlockEntity parent);

  /**
   * Gets the handler this block hands out as its capability, fetching it from the master if needed.
   * @return  Handler, or null if there is no master or the master has none
   */
  @Nullable
  public T getHandler() {
    if (!fetched) {
      fetched = true;
      if (validateMaster()) {
        BlockPos master = getMasterPos();
        if (master != null && this.level != null) {
          BlockEntity te = level.getBlockEntity(master);
          if (te != null) {
            handler = fetchHandler(te);
          }
        }
      }
    }
    return handler;
  }


  /* Retexturing */

  @Override
  @Nonnull
  public ModelData getModelData() {
    return RetexturedHelper.getModelData(getTexture());
  }

  @Override
  public String getTextureName() {
    return RetexturedHelper.getTextureName(texture);
  }

  @Override
  public void updateTexture(String name) {
    Block oldTexture = texture;
    texture = RetexturedHelper.getBlock(name);
    if (oldTexture != texture) {
      setChangedFast();
      RetexturedHelper.onTextureUpdated(this);
    }
  }


  /* NBT */

  @Override
  protected boolean shouldSyncOnUpdate() {
    return true;
  }

  @Override
  protected void saveSynced(CompoundTag tags, HolderLookup.Provider registries) {
    super.saveSynced(tags, registries);
    if (texture != Blocks.AIR) {
      tags.putString(TAG_TEXTURE, getTextureName());
    }
  }

  @Override
  protected void loadAdditional(CompoundTag tags, HolderLookup.Provider registries) {
    super.loadAdditional(tags, registries);
    if (tags.contains(TAG_TEXTURE, Tag.TAG_STRING)) {
      texture = RetexturedHelper.getBlock(tags.getString(TAG_TEXTURE));
      RetexturedHelper.onTextureUpdated(this);
    }
  }


  /** Fluid implementation of smeltery IO */
  public static abstract class SmelteryFluidIO extends SmelteryInputOutputBlockEntity<IFluidHandler> {
    protected SmelteryFluidIO(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
    }

    /** Wraps the master's handler before handing it out */
    protected IFluidHandler makeWrapper(IFluidHandler handler) {
      return handler;
    }

    @Nullable
    @Override
    protected IFluidHandler fetchHandler(BlockEntity parent) {
      // fluid capability is not exposed directly in the smeltery
      if (parent instanceof ISmelteryTankHandler tankHandler) {
        IFluidHandler handler = tankHandler.getFluidHandler();
        if (handler != null) {
          return makeWrapper(handler);
        }
      }
      return null;
    }
  }

  /** Item implementation of smeltery IO */
  public static class ChuteBlockEntity extends SmelteryInputOutputBlockEntity<IItemHandler> {
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
      this(TinkerSmeltery.chute.get(), pos, state);
    }

    protected ChuteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
    }

    @Nullable
    @Override
    protected IItemHandler fetchHandler(BlockEntity parent) {
      assert level != null;
      return level.getCapability(Capabilities.ItemHandler.BLOCK, parent.getBlockPos(), null);
    }
  }

}
