package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.ApiStatus;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry of the capabilities a modifier may add to a tool, and the one place they are attached to every
 * {@link IModifiable} item.
 * <p>
 * In 1.20 this was an {@code ICapabilityProvider} instance per item stack, created from
 * {@code Item#initCapabilities} and asked for any capability at all. 1.21's capabilities are not attached to a stack;
 * they are registered per item <i>and per capability type</i> against {@link RegisterCapabilitiesEvent}, and there is
 * no longer a generic "give me capability X" entry point to dispatch from. So the registry is keyed by
 * {@link ItemCapability} and an addon registers what it provides rather than a provider that decides for itself,
 * which is also what lets an addon add a capability type Tinkers has never heard of.
 * <p>
 * Two consequences fall out of the shape and are worth stating, because they were bugs waiting to happen in 1.20:
 * <ul>
 *   <li>A provider is consulted on every {@link ItemStack#getCapability(ItemCapability)} rather than once per stack, so
 *       the {@link ToolStack} handed to it is freshly read every time. That is what {@code clearCache()} and the
 *       {@code tool.refresh(stack)} call in the old provider were emulating by hand, and both are gone.</li>
 *   <li>The tool is a {@link ToolStack#mutable(ItemStack) mutable} one bound to the queried stack, because all three of
 *       Tinkers' tool capabilities write to it. Since T5 a write is local until {@link ToolStack#updateStack()} runs,
 *       so every capability implementation here commits at the end of the methods that mutate.</li>
 * </ul>
 */
public class ToolCapabilityProvider {
  private ToolCapabilityProvider() {}

  /** Every capability registered for modifiable tools */
  private static final List<Registration<?>> REGISTRATIONS = new ArrayList<>();

  /**
   * Registers a capability provider for every modifiable item.
   * Call before {@link RegisterCapabilitiesEvent} fires, which in practice means from your mod constructor.
   * @param capability  Capability to provide
   * @param provider    Provider, returning null for a tool that does not currently have the capability
   * @param <T>         Capability type
   */
  public static <T> void register(ItemCapability<T,Void> capability, IToolCapabilityProvider<T> provider) {
    REGISTRATIONS.add(new Registration<>(capability, provider));
  }

  /**
   * Attaches every registered capability to every modifiable item.
   * <p>
   * The item list is read out of the item registry rather than named, so an addon's tools are covered without the
   * addon doing anything; the event fires after registries freeze, so the registry is complete by then. This is the
   * direct replacement for the {@code Item#initCapabilities} override the five modifiable item classes used to carry.
   */
  @ApiStatus.Internal
  public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    ItemLike[] items = BuiltInRegistries.ITEM.stream().filter(IModifiable.class::isInstance).toArray(ItemLike[]::new);
    // registerItem throws on an empty item list, and a Tinkers with no tools is a broken install rather than a crash
    if (items.length == 0) {
      return;
    }
    for (Registration<?> registration : REGISTRATIONS) {
      registration.register(event, items);
    }
  }

  /** One registered capability, existing to keep {@code T} bound across the event call */
  private record Registration<T>(ItemCapability<T,Void> capability, IToolCapabilityProvider<T> provider) {
    void register(RegisterCapabilitiesEvent event, ItemLike[] items) {
      event.registerItem(capability, (stack, context) -> provider.getCapability(stack, ToolStack.mutable(stack)), items);
    }
  }

  /** Interface to get a capability on a tool */
  @FunctionalInterface
  public interface IToolCapabilityProvider<T> {
    /**
     * Gets the capability instance for the given tool.
     * @param stack  Stack being queried
     * @param tool   Mutable tool bound to {@code stack}. An implementation that writes to it must call
     *               {@link ToolStack#updateStack()}, or the write is lost.
     * @return  Capability instance, or null if this tool does not have the capability right now
     */
    @Nullable
    T getCapability(ItemStack stack, ToolStack tool);
  }
}
