package slimeknights.tconstruct.library.tools.layout;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.fixture.MaterialItemFixture;
import slimeknights.tconstruct.library.recipe.partbuilder.Pattern;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateTinkerSlotLayoutsPacketTest extends BaseMcTest {
  private static RegistryFriendlyByteBuf buffer() {
    return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
  }

  @Test
  void packetReadWrite() {
    StationSlotLayout layout = StationSlotLayout
      .builder()
      .translationKey("slot_name")
      .icon(new Pattern("test:pattern"))
      .sortIndex(3)
      .toolSlot(1, 2)
      .addInputSlot(null, 3, 4)
      .addInputSlot(null, 5, 6)
      .build();
    layout.setName(ResourceLocation.parse("test:main_layout"));
    UpdateTinkerSlotLayoutsPacket packetToEncode = new UpdateTinkerSlotLayoutsPacket(Arrays.asList(StationSlotLayout.EMPTY, layout));
    RegistryFriendlyByteBuf buffer = buffer();
    packetToEncode.encode(buffer);

    UpdateTinkerSlotLayoutsPacket decoded = new UpdateTinkerSlotLayoutsPacket(buffer);
    Collection<StationSlotLayout> layouts = decoded.getLayouts();
    assertThat(layouts).hasSize(2);

    // first should be empty
    Iterator<StationSlotLayout> iterator = layouts.iterator();
    StationSlotLayout next = iterator.next();
    assertThat(next.getName().toString()).isEqualTo("tconstruct:empty");
    assertThat(next.getTranslationKey()).isEqualTo("");
    assertThat(next.getIcon()).isEqualTo(LayoutIcon.EMPTY);
    assertThat(next.getSortIndex()).isEqualTo(255);
    assertThat(next.isMain()).isTrue();
    LayoutSlot slot = next.getToolSlot();
    assertThat(slot.isHidden()).isTrue();
    assertThat(next.getInputSlots()).isEmpty();

    // next is filled with the above data
    next = iterator.next();
    assertThat(next.getName().toString()).isEqualTo("test:main_layout");
    assertThat(next.getTranslationKey()).isEqualTo("slot_name");
    Pattern pattern = next.getIcon().getValue(Pattern.class);
    assertThat(pattern).isNotNull();
    assertThat(pattern.toString()).isEqualTo("test:pattern");
    assertThat(next.getSortIndex()).isEqualTo(3);
    assertThat(next.isMain()).isFalse();
    // slots
    slot = next.getToolSlot();
    assertThat(slot.getX()).isEqualTo(1);
    assertThat(slot.getY()).isEqualTo(2);
    assertThat(next.getInputSlots()).hasSize(2);
    slot = next.getInputSlots().get(0);
    assertThat(slot.getX()).isEqualTo(3);
    assertThat(slot.getY()).isEqualTo(4);
    slot = next.getInputSlots().get(1);
    assertThat(slot.getX()).isEqualTo(5);
    assertThat(slot.getY()).isEqualTo(6);
  }


  /* Decode order */

  /**
   * Stands in for a modifiable tool during the window when the registries its load hook needs are not synced yet.
   * <p>
   * The bug these three tests pin was a {@code DecoderException: Attempted to load a modifier before dynamic modifiers
   * are loaded} thrown out of {@code LayoutSlot.read} on login. Nothing in this packet names a modifier: its icons and
   * slot filters carry item stacks, {@code Item#verifyComponentsAfterLoad} runs on every item stack construction, and
   * for a Tinkers tool that hook rebuilds stats, which asks the modifier registry - which a <em>different</em> sync
   * packet fills in and which, on login, has not necessarily arrived.
   * <p>
   * The full pack join test catches that, at the cost of a full pack join. What is pinned here is the property that
   * makes it impossible: decoding this packet must construct no item stack at all, so it can run no load hook, so it
   * cannot depend on what else has loaded. This item throws from the hook while it is not ready, so a decode that
   * touches it fails loudly and one that defers passes and then produces the right value once the flag flips.
   */
  public static class NotReadyItem extends Item {
    /** Whether the registries this item's load hook needs are loaded. Static, as the hook has no other way in */
    static boolean ready = true;
    /** Number of times the load hook ran, so a test can tell "did not throw" from "was never constructed" */
    static int loads = 0;

    public NotReadyItem() {
      super(new Properties());
    }

    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
      loads++;
      if (!ready) {
        throw new IllegalStateException("Attempted to load a modifier before dynamic modifiers are loaded");
      }
    }
  }

  private static Item notReady;

  @BeforeEach
  void registerNotReadyItem() {
    if (notReady == null) {
      notReady = MaterialItemFixture.register(ResourceLocation.fromNamespaceAndPath("test", "not_ready"), new NotReadyItem());
    }
    NotReadyItem.ready = true;
    NotReadyItem.loads = 0;
  }

  @AfterEach
  void releaseNotReadyItem() {
    NotReadyItem.ready = true;
  }

  /** Encodes a layout on the server side, where everything is loaded, then puts the receiver back in the early state */
  private static RegistryFriendlyByteBuf encodeLayoutThenGoEarly() {
    StationSlotLayout layout = StationSlotLayout
      .builder()
      .translationKey("test.layout")
      .icon(new ItemStack(notReady))
      .toolSlot(new Pattern("test:pattern"), null, 1, 2, Ingredient.of(notReady))
      .addInputSlot(null, "input", 3, 4, Ingredient.of(Items.BOOK))
      .build();
    layout.setName(ResourceLocation.fromNamespaceAndPath("test", "decode_order"));
    RegistryFriendlyByteBuf buffer = buffer();
    new UpdateTinkerSlotLayoutsPacket(List.of(layout)).encode(buffer);
    NotReadyItem.ready = false;
    NotReadyItem.loads = 0;
    return buffer;
  }

  /** The premise the other two rest on: this stand-in really does throw when a stack of it is decoded too early */
  @Test
  void theStandInItemThrowsWhenNotReady() {
    RegistryFriendlyByteBuf buffer = buffer();
    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, new ItemStack(notReady));
    NotReadyItem.ready = false;
    assertThatThrownBy(() -> ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("dynamic modifiers");
  }

  @Test
  void decodesBeforeTheRegistriesAreLoaded() {
    RegistryFriendlyByteBuf buffer = encodeLayoutThenGoEarly();

    // the whole packet decodes with the registries deliberately not ready
    UpdateTinkerSlotLayoutsPacket[] decoded = new UpdateTinkerSlotLayoutsPacket[1];
    assertThatCode(() -> decoded[0] = new UpdateTinkerSlotLayoutsPacket(buffer)).doesNotThrowAnyException();
    assertThat(buffer.readableBytes()).isZero();
    // and it resolved nothing: not one item stack was constructed
    assertThat(NotReadyItem.loads).isZero();

    // the primitive half of the layout is available immediately, which is what the loader sorts and lays out on
    StationSlotLayout layout = decoded[0].getLayouts().iterator().next();
    assertThat(layout.getName().toString()).isEqualTo("test:decode_order");
    assertThat(layout.getTranslationKey()).isEqualTo("test.layout");
    assertThat(layout.getToolSlot().getX()).isEqualTo(1);
    assertThat(layout.getInputSlots()).hasSize(1);
    assertThat(layout.getInputSlots().get(0).getTranslationKey()).isEqualTo("input");

    // once the registries are loaded, the deferred halves resolve, and to the right values
    NotReadyItem.ready = true;
    ItemStack icon = layout.getIcon().getValue(ItemStack.class);
    assertThat(icon).isNotNull();
    assertThat(icon.getItem()).isSameAs(notReady);
    assertThat(layout.getToolSlot().isValid(new ItemStack(notReady))).isTrue();
    assertThat(layout.getToolSlot().isValid(new ItemStack(Items.BOOK))).isFalse();
    assertThat(layout.getInputSlots().get(0).isValid(new ItemStack(Items.BOOK))).isTrue();
  }

  /** A packet decoded early and forwarded on without ever being read must not decode in order to forward */
  @Test
  void reEncodesWithoutDecoding() {
    RegistryFriendlyByteBuf buffer = encodeLayoutThenGoEarly();
    byte[] original = new byte[buffer.readableBytes()];
    buffer.getBytes(buffer.readerIndex(), original);

    RegistryFriendlyByteBuf second = buffer();
    new UpdateTinkerSlotLayoutsPacket(buffer).encode(second);
    assertThat(NotReadyItem.loads).isZero();

    byte[] round = new byte[second.readableBytes()];
    second.readBytes(round);
    assertThat(round).isEqualTo(original);
  }
}
