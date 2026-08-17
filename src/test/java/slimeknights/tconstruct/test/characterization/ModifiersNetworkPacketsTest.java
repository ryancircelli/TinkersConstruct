package slimeknights.tconstruct.test.characterization;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.fluid.FluidEffects;
import slimeknights.tconstruct.library.modifiers.fluid.UpdateFluidEffectsPacket;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Object -> encode -> decode -> object field round trip for the fluid effect packet.
 * <p>
 * The modifier packet's half of this moved to {@code UpdateModifiersPacketTest}, which sits beside its subject and
 * pins the decode ordering rules the 1.21 packet gained.
 */
class ModifiersNetworkPacketsTest extends BaseMcTest {
  @BeforeAll
  static void registerModuleTypes() {
    ModuleTypeRegistrations.ensureRegistered();
  }

  @Test
  void updateFluidEffectsPacket_roundTrips() {
    ResourceLocation name = TConstruct.getResource("characterization_test_fluid");
    FluidIngredient ingredient = FluidIngredient.of(net.minecraft.world.level.material.Fluids.WATER, 100);
    FluidEffects effects = new FluidEffects(ingredient, List.of(), List.of(), false);
    FluidEffects.Entry entry = new FluidEffects.Entry(name, effects);

    UpdateFluidEffectsPacket packet = new UpdateFluidEffectsPacket(List.of(entry));
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    packet.encode(buffer);
    UpdateFluidEffectsPacket decoded = new UpdateFluidEffectsPacket(buffer);

    assertThat(decoded.fluids()).hasSize(1);
    FluidEffects.Entry decodedEntry = decoded.fluids().get(0);
    assertThat(decodedEntry.name()).isEqualTo(name);
    assertThat(FluidEffects.LOADABLE.serialize(decodedEntry.effects())).isEqualTo(FluidEffects.LOADABLE.serialize(effects));
  }

}
