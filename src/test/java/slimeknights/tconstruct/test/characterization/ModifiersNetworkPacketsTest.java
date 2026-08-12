package slimeknights.tconstruct.test.characterization;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;
import slimeknights.tconstruct.library.modifiers.UpdateModifiersPacket;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffects;
import slimeknights.tconstruct.library.modifiers.fluid.UpdateFluidEffectsPacket;
import slimeknights.tconstruct.library.modifiers.impl.ComposableModifier;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Object -> encode -> decode -> object field round trips for the two packets registered under {@code modifiers} in {@code TinkerNetwork}. */
class ModifiersNetworkPacketsTest extends BaseMcTest {
  @BeforeAll
  static void registerModuleTypes() {
    ModuleTypeRegistrations.ensureRegistered();
  }

  @Test
  void updateModifiersPacket_roundTrips() {
    ModifierId id = new ModifierId("tconstruct", "characterization_test_modifier");
    // an empty-object ComposableModifier is valid: every one of its fields (level_display, tooltip_display,
    // priority, modules) defaults - only the id (set below, mirroring how ModifierManager assigns ids on load)
    // needs to match the map key for UpdateModifiersPacket to treat it as a real (non-redirect) modifier
    Modifier modifier = ComposableModifier.LOADER.deserialize(new JsonObject());
    setId(modifier, id);
    Map<ModifierId, Modifier> allModifiers = ImmutableMap.of(id, modifier);

    UpdateModifiersPacket packet = new UpdateModifiersPacket(allModifiers, Map.of(), Map.of(), Map.of());
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    UpdateModifiersPacket decoded = new UpdateModifiersPacket(buffer);

    @SuppressWarnings("unchecked")
    Map<ModifierId, Modifier> decodedModifiers = (Map<ModifierId, Modifier>) getField(decoded, "allModifiers");
    assertThat(decodedModifiers).containsOnlyKeys(id);
    Modifier decodedModifier = decodedModifiers.get(id);
    assertThat(decodedModifier.getId()).isEqualTo(id);
    assertThat(decodedModifier).isInstanceOf(ComposableModifier.class);
  }

  @Test
  void updateFluidEffectsPacket_roundTrips() {
    ResourceLocation name = TConstruct.getResource("characterization_test_fluid");
    FluidIngredient ingredient = FluidIngredient.of(net.minecraft.world.level.material.Fluids.WATER, 100);
    FluidEffects effects = new FluidEffects(ingredient, List.of(), List.of(), false);
    FluidEffects.Entry entry = new FluidEffects.Entry(name, effects);

    UpdateFluidEffectsPacket packet = new UpdateFluidEffectsPacket(List.of(entry));
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    packet.encode(buffer);
    UpdateFluidEffectsPacket decoded = UpdateFluidEffectsPacket.decode(buffer);

    assertThat(decoded.fluids()).hasSize(1);
    FluidEffects.Entry decodedEntry = decoded.fluids().get(0);
    assertThat(decodedEntry.name()).isEqualTo(name);
    assertThat(FluidEffects.LOADABLE.serialize(decodedEntry.effects())).isEqualTo(FluidEffects.LOADABLE.serialize(effects));
  }

  /** {@code Modifier#setId} is package-private; reach it via reflection (mirrors {@code ModifierFixture}, a different package). */
  private static void setId(Modifier modifier, ModifierId id) {
    try {
      java.lang.reflect.Method method = Modifier.class.getDeclaredMethod("setId", ModifierId.class);
      method.setAccessible(true);
      method.invoke(modifier, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object getField(Object target, String name) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
