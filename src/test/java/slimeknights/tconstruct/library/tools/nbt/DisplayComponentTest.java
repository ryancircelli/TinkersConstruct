package slimeknights.tconstruct.library.tools.nbt;

import com.mojang.serialization.DynamicOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.fixture.MaterialFixture;
import slimeknights.tconstruct.fixture.MaterialItemFixture;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.item.ToolItemTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ToolComponents#DISPLAY}, the home 1.20's {@code tic_display} tag key finally got, against the three
 * way argument its javadoc makes for being its own {@code DataComponentType<Unit>}:
 * <ul>
 *   <li>not a field on {@link ToolDataComponent}, because a tool part carries no {@code tconstruct:tool} component
 *       for a field to live in - {@link #display_needsNoToolComponent()};</li>
 *   <li>not an entry in {@code minecraft:custom_data}, because that compound is handed whole to raw data modifiers
 *       and an internal flag there is one of them away from being cleared - {@link #display_isNotInCustomData()};</li>
 *   <li>a component, which has to survive the encode/decode both halves of a component do -
 *       {@link #display_survivesSaveAndLoad()} and {@link #display_survivesTheNetwork()}.</li>
 * </ul>
 * Plus the behaviour the flag exists to buy: {@link ToolStack#verifyComponents} leaves a display stack alone
 * ({@link #verifyComponents_takesTheFastExitOnADisplayStack()}).
 * <p>
 * The round trips follow {@code RoundTripAssertions}: a fixed point assertion over the whole serialized form rather
 * than a read of the one key under test, and the network half also asserts decode consumes exactly the bytes encode
 * wrote. The registry access is built the way that class builds one, since there is no live game to draw one from.
 */
class DisplayComponentTest extends ToolItemTest {
  private static final RegistryAccess ACCESS = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

  /** Round trips a stack through the persistent codec, the way a save file does */
  private static ItemStack saveAndLoad(ItemStack stack) {
    DynamicOps<Tag> ops = ACCESS.createSerializationContext(NbtOps.INSTANCE);
    Tag saved = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
    ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
    assertThat(ItemStack.CODEC.encodeStart(ops, loaded).getOrThrow())
      .as("re-saving a loaded stack should be idempotent")
      .isEqualTo(saved);
    return loaded;
  }

  /** Round trips a stack through the network codec, the way the server sends one to a client */
  private static ItemStack sendAndReceive(ItemStack stack) {
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), ACCESS);
    ItemStack.STREAM_CODEC.encode(buffer, stack);
    int written = buffer.readableBytes();
    ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
    assertThat(written - buffer.readableBytes())
      .as("decode should consume exactly the bytes written by encode")
      .isEqualTo(written);
    return decoded;
  }

  /** The display stack a recipe viewer or a station result slot gets handed */
  private static ItemStack displayTool() {
    return ToolBuildHandler.buildToolForRendering(tool, tool.getToolDefinition());
  }

  @Test
  void display_absentByDefault() {
    // a real tool is not a prop, which is the whole distinction the flag draws
    assertThat(TooltipUtil.isDisplay(testItemStack)).isFalse();
    assertThat(testItemStack.has(ToolComponents.DISPLAY)).isFalse();
  }

  @Test
  void display_survivesSaveAndLoad() {
    ItemStack stack = displayTool();
    assertThat(TooltipUtil.isDisplay(stack)).isTrue();
    assertThat(TooltipUtil.isDisplay(saveAndLoad(stack))).isTrue();
  }

  @Test
  void display_survivesTheNetwork() {
    ItemStack stack = displayTool();
    assertThat(TooltipUtil.isDisplay(sendAndReceive(stack))).isTrue();
  }

  @Test
  void display_needsNoToolComponent() {
    // a tool part is a material item, not a tool: ToolBuildHandler.getDisplayPart marks one for the part tooltip,
    // and there is no tconstruct:tool component on it for a field to have lived in
    ItemStack part = ToolBuildHandler.getDisplayPart(MaterialItemFixture.MATERIAL_ITEM_HEAD, 0);
    assertThat(part.has(ToolComponents.TOOL)).isFalse();
    assertThat(TooltipUtil.isDisplay(part)).isTrue();

    ItemStack loaded = saveAndLoad(part);
    assertThat(loaded.has(ToolComponents.TOOL)).isFalse();
    assertThat(TooltipUtil.isDisplay(loaded)).isTrue();
  }

  @Test
  void display_isNotInCustomData() {
    // minecraft:custom_data is what RawDataNBT hands a modifier, whole, with plain string keys. The flag must not
    // be in there, or clearing raw data would clear it
    ItemStack stack = displayTool();
    assertThat(stack.has(DataComponents.CUSTOM_DATA)).isFalse();

    // and the other direction: a modifier replacing the raw data compound outright leaves the flag standing
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
    assertThat(TooltipUtil.isDisplay(stack)).isTrue();
    stack.remove(DataComponents.CUSTOM_DATA);
    assertThat(TooltipUtil.isDisplay(stack)).isTrue();
  }

  @Test
  void verifyComponents_takesTheFastExitOnADisplayStack() {
    // control: an uninitialized stack that has materials gets its stats rebuilt
    ItemStack control = ToolBuildHandler.buildItemFromMaterials(tool, MaterialNBT.of(MaterialFixture.MATERIAL_WITH_HEAD, MaterialFixture.MATERIAL_WITH_HANDLE, MaterialFixture.MATERIAL_WITH_EXTRA));
    control.remove(ToolComponents.TOOL_STATS);
    ToolStack.verifyComponents(control, tool.getToolDefinition());
    assertThat(control.has(ToolComponents.TOOL_STATS))
      .overridingErrorMessage("verifyComponents did not rebuild an uninitialized tool, so the display case below proves nothing")
      .isTrue();

    // the same stack marked display is left exactly as it was, half built on purpose
    ItemStack stack = ToolBuildHandler.buildItemFromMaterials(tool, MaterialNBT.of(MaterialFixture.MATERIAL_WITH_HEAD, MaterialFixture.MATERIAL_WITH_HANDLE, MaterialFixture.MATERIAL_WITH_EXTRA));
    stack.remove(ToolComponents.TOOL_STATS);
    TooltipUtil.setDisplay(stack);
    ToolStack.verifyComponents(stack, tool.getToolDefinition());
    assertThat(stack.has(ToolComponents.TOOL_STATS))
      .overridingErrorMessage("verifyComponents rebuilt a display stack instead of leaving it alone")
      .isFalse();
  }
}
