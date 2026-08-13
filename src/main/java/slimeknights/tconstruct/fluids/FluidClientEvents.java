package slimeknights.tconstruct.fluids;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;
import slimeknights.mantle.fluid.TextureFluidType;
import slimeknights.mantle.fluid.texture.ClientTextureFluidType;
import slimeknights.mantle.registration.object.FlowingFluidObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.fluids.fluids.ClientPotionFluidType;
import slimeknights.tconstruct.fluids.fluids.PotionFluidType;
import slimeknights.tconstruct.library.client.model.FluidContainerModel;

@EventBusSubscriber(modid = TConstruct.MOD_ID, value = Dist.CLIENT, bus = Bus.MOD)
public class FluidClientEvents extends ClientEventBase {
  @SubscribeEvent
  static void clientSetup(final FMLClientSetupEvent event) {
    setTranslucent(TinkerFluids.honey);
    // slime
    setTranslucent(TinkerFluids.earthSlime);
    setTranslucent(TinkerFluids.skySlime);
    setTranslucent(TinkerFluids.enderSlime);
    // molten
    setTranslucent(TinkerFluids.moltenDiamond);
    setTranslucent(TinkerFluids.moltenEmerald);
    setTranslucent(TinkerFluids.moltenGlass);
    setTranslucent(TinkerFluids.moltenGlass);
    setTranslucent(TinkerFluids.liquidSoul);
    setTranslucent(TinkerFluids.moltenSoulsteel);
    setTranslucent(TinkerFluids.moltenAmethyst);
  }

  /**
   * Installs the potion fluid's client extensions.
   * <p>
   * {@code FluidType#initializeClient} is deprecated and unused in 1.21; every extension is registered here instead,
   * and {@link net.neoforged.neoforge.client.extensions.common.ClientExtensionsManager} throws if one fluid type is
   * registered twice. Mantle's {@link ClientTextureFluidType#registerExtensions} already claims every
   * {@link TextureFluidType} in the registry, and {@link PotionFluidType} extends that class, so the guard below is
   * what keeps the two listeners from colliding: Mantle is a dependency, so its mod bus runs first and wins the type
   * as things stand. For the potion tint to actually apply, {@link PotionFluidType} has to stop extending
   * {@link TextureFluidType} - it is a bare marker class with no members, and {@link ClientPotionFluidType} already
   * inherits every texture behaviour it marks for.
   */
  @SubscribeEvent
  static void registerClientExtensions(RegisterClientExtensionsEvent event) {
    // PotionFluidType is deliberately not a Mantle TextureFluidType, so Mantle's blanket registration skips it and
    // this is the only place it is bound - see PotionFluidType's javadoc
    FluidType potion = TinkerFluids.potion.getType();
    event.registerFluidType(new ClientPotionFluidType(potion), potion);
  }

  @SubscribeEvent
  static void itemColors(final RegisterColorHandlersEvent.Item event) {
    // the potion is a data component now; PotionContents#getColor is the whole of PotionUtils#getColor
    event.register((stack, index) -> index > 0 ? -1 : stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor(), TinkerFluids.potion.asItem());
  }

  @SubscribeEvent
  static void registerModelLoaders(RegisterGeometryLoaders event) {
    // model loader IDs are a ResourceLocation rather than a bare string in 1.21
    event.register(TConstruct.getResource("fluid_container"), FluidContainerModel.LOADER);
  }

  private static void setTranslucent(FlowingFluidObject<?> fluid) {
    ItemBlockRenderTypes.setRenderLayer(fluid.getStill(), RenderType.translucent());
    ItemBlockRenderTypes.setRenderLayer(fluid.getFlowing(), RenderType.translucent());
  }
}
