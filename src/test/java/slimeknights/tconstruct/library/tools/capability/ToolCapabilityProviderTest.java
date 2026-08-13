package slimeknights.tconstruct.library.tools.capability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import slimeknights.tconstruct.fixture.MaterialItemFixture;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Pins the two things {@link ToolCapabilityProvider} promises now that it is a registry consulted on
 * {@code RegisterCapabilitiesEvent} rather than one provider object per stack: who it attaches to, and what a
 * provider is handed when it is asked.
 * <p>
 * The event is mocked, because constructing one means owning the capability tables a launched game builds. That is
 * enough for both claims: {@code registerCapabilities} does its whole job in the arguments it passes to
 * {@code registerItem}, and the provider it wraps can be pulled back out of the call and invoked directly. What a
 * mock cannot reach is whether NeoForge then honours the registration, which is a game test.
 */
class ToolCapabilityProviderTest extends ToolItemTest {
  /**
   * Registering the same capability twice would leave two entries in the provider's static list, so one per test.
   * <p>
   * That list is not empty to begin with any more, which is why both tests below verify {@code atLeastOnce()}
   * rather than the default exactly-once. {@code ToolCapabilityProvider.REGISTRATIONS} is static and Tinkers' own
   * capabilities are in it: NeoForge's JUnit launcher runs full FML mod loading before any test class loads, so the
   * mod has registered its four by the time a test adds a fifth. 1.20's plain-JUnit run never loaded the mod, so
   * the list held only what the test put there and exactly-once happened to hold.
   */
  private static final ItemCapability<String,Void> ATTACHED_CAPABILITY = ItemCapability.createVoid(ResourceLocation.fromNamespaceAndPath("test", "attached_capability"), String.class);
  private static final ItemCapability<String,Void> PROVIDED_CAPABILITY = ItemCapability.createVoid(ResourceLocation.fromNamespaceAndPath("test", "provided_capability"), String.class);

  @Test
  void registerCapabilities_attachesToEveryModifiableItem() {
    // the item list is read out of the item registry rather than named, which is what covers an addon's tools;
    // "read out of the registry" also has to mean modifiable items only, not every item in the game
    ToolCapabilityProvider.register(ATTACHED_CAPABILITY, (stack, tool) -> "present");
    RegisterCapabilitiesEvent event = mock(RegisterCapabilitiesEvent.class);
    ToolCapabilityProvider.registerCapabilities(event);

    // registerItem takes ItemLike... - the captor is of the array type so it takes the whole varargs run. A
    // captor of the component type matches an invocation passing exactly one item, which this never does.
    ArgumentCaptor<ItemLike[]> items = ArgumentCaptor.forClass(ItemLike[].class);
    verify(event, atLeastOnce()).registerItem(eq(ATTACHED_CAPABILITY), any(), items.capture());
    assertThat(items.getValue())
      .contains(tool)
      .doesNotContain(MaterialItemFixture.MATERIAL_ITEM_HEAD);
  }

  @SuppressWarnings("unchecked")  // ArgumentCaptor cannot name a generic interface
  @Test
  void provider_isHandedAMutableToolBoundToTheStack() {
    // 1.20 built one provider per stack and had to refresh the tool it held by hand; a 1.21 provider is called per
    // query, so the tool is freshly read. It is also mutable and bound, since all three tool capabilities write
    AtomicReference<ToolStack> seen = new AtomicReference<>();
    ToolCapabilityProvider.register(PROVIDED_CAPABILITY, (stack, tool) -> {
      seen.set(tool);
      return "present";
    });
    RegisterCapabilitiesEvent event = mock(RegisterCapabilitiesEvent.class);
    ToolCapabilityProvider.registerCapabilities(event);

    ArgumentCaptor<ICapabilityProvider<ItemStack,Void,String>> provider = ArgumentCaptor.forClass(ICapabilityProvider.class);
    // any(ItemLike[].class) rather than any(): the items parameter is varargs, and a bare any() stands for a
    // single one, so it matches no invocation of a method that always passes the whole modifiable item list.
    verify(event, atLeastOnce()).registerItem(eq(PROVIDED_CAPABILITY), provider.capture(), any(ItemLike[].class));

    assertThat(provider.getValue().getCapability(testItemStack, null)).isEqualTo("present");
    assertThat(seen.get().isSameStack(testItemStack))
      .overridingErrorMessage("a capability provider was handed a tool that writes nowhere")
      .isTrue();

    // and a second query reads the stack again rather than reusing the first tool
    ToolStack first = seen.get();
    provider.getValue().getCapability(testItemStack, null);
    assertThat(seen.get()).isNotSameAs(first);
  }
}
