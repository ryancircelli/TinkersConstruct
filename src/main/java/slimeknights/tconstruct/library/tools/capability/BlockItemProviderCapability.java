package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.ApiStatus;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;

/**
 * A capability that provides block items to things that place blocks, such as the Exchanging modifier or some place block fluid effects like Ichor.
 * Providers of this capability are encouraged to use a single instance for all objects that use the same logic, as the stack and more context are provided in the relevant methods.
 * <p>
 * This one stays a capability rather than becoming a data attachment, unlike the entity data in this package: it is a
 * behaviour another mod implements for its own items, which is exactly what a capability is for, and 1.21 registers
 * item capabilities per {@link net.minecraft.world.item.Item} rather than per stack, which is the granularity the
 * implementations already had.
 */
public interface BlockItemProviderCapability {

  /** Capability ID */
  ResourceLocation ID = TConstruct.getResource("block_provider");
  /** Capability type */
  ItemCapability<BlockItemProviderCapability,Void> CAPABILITY = ItemCapability.createVoid(ID, BlockItemProviderCapability.class);

  /**
   * Registers this capability and its default implementation.
   * <p>
   * The listener is added at {@link EventPriority#LOW} so that a mod registering its own provider for a block item at
   * the default priority is asked first: 1.21 consults an item's providers in registration order and takes the first
   * non-null, so registering late is what "do not override other mods" means now. In 1.20 the same intent was spelled
   * as a low priority on {@code AttachCapabilitiesEvent}.
   */
  @ApiStatus.Internal
  static void register(IEventBus bus) {
    bus.addListener(EventPriority.LOW, BlockItemProviderCapability::registerCapabilities);
  }

  /** Registers the default implementation for every block item */
  private static void registerCapabilities(RegisterCapabilitiesEvent event) {
    ItemLike[] blockItems = BuiltInRegistries.ITEM.stream().filter(BlockItem.class::isInstance).toArray(ItemLike[]::new);
    if (blockItems.length > 0) {
      event.registerItem(CAPABILITY, (stack, context) -> SimpleBlockItem.INSTANCE, blockItems);
    }
  }

  /**
   * Utility to fetch a BlockProvider or null from a given stack.
   * @return The block provider for this stack, or null if this stack cannot provide block items.
   */
  @Nullable
  static BlockItemProviderCapability getBlockProvider(ItemStack stack) {
    return stack.getCapability(CAPABILITY);
  }

  /**
   * Utility to verify that a given stack does indeed contain a BlockItem
   * @param stack The stack to check
   * @param blockProvider The provider that provided this item, used in case it fails as debugging information
   * @return the contained BlockItem, or null if it was not a BlockItem
   */
  @Nullable
  static BlockItem verifyBlockItem(ItemStack stack, BlockItemProviderCapability blockProvider) {
    if (stack.getItem() instanceof BlockItem bItem) {
      return bItem;
    } else {
      TConstruct.LOG.warn("BlockItemProviderCapability implementation tried to return a non-empty, non-blockitem stack! Cap: {}, Cap Class: {}, Provided Item: {}", blockProvider, blockProvider.getClass().getName(), BuiltInRegistries.ITEM.getId(stack.getItem()));
      return null;
    }
  }

  /**
   * Get a {@link BlockItem} to provide, wrapped as an ItemStack with any required placement NBT data. Can be randomised, if desired.
   * <br>
   * <br>
   * <b>The returned stack must have {@link ItemStack#getItem} return an instance of {@link BlockItem}, or be {@link ItemStack#EMPTY}!</b>
   * @param stack The {@link ItemStack} that this capability was attached to.
   * @param entity The {@link LivingEntity} (usually a {@link Player}) that is requesting a block.
   * @return the {@link ItemStack} that this provides, or {@link ItemStack#EMPTY} if this cannot provide more block items (for example if the stack has been depleted)
   */
  ItemStack getBlockItemStack(ItemStack stack, @Nullable LivingEntity entity);

  /**
   * Consume one item from this provider.
   * @param stack The {@link ItemStack} that this capability was attached to.
   * @param backingStack The stack returned by {@link #getBlockItemStack} that was placed and is now being consumed. It is unmodified and the same instance so can use == for comparisons.
   * @param entity The {@link LivingEntity} (usually a {@link Player}) that has just consumed a block.
   * Consume a block from this provider. For example may decrease a contained stacks size or remove fluid from the stack's tank.
   */
  void consume(ItemStack stack, ItemStack backingStack, @Nullable LivingEntity entity);

  /**
   * A simple implementation of {@link BlockItemProviderCapability} that provides from an ItemStack holding a BlockItem
   */
  final class SimpleBlockItem implements BlockItemProviderCapability {
    public static final SimpleBlockItem INSTANCE = new SimpleBlockItem();

    @Override
    public ItemStack getBlockItemStack(ItemStack capStack, @Nullable LivingEntity entity) {
      return capStack.isEmpty() ? ItemStack.EMPTY : capStack;
    }

    @Override
    public void consume(ItemStack capStack, ItemStack backingStack, @Nullable LivingEntity entity) {
      capStack.shrink(1);
    }
  }
}
