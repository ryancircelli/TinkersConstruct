package slimeknights.tconstruct.fluids;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import slimeknights.tconstruct.TConstruct;

/**
 * Event subscriber for fluid events on the game bus.
 * <p>
 * 1.20 also attached a fluid handler to the vanilla powdered snow bucket from {@code AttachCapabilitiesEvent}. That
 * event is gone in 1.21; capabilities are registered per item against {@code RegisterCapabilitiesEvent}, which is a
 * mod bus event, so that registration moved to {@link TinkerFluids#registerCapabilities}.
 */
@SuppressWarnings("unused")
@EventBusSubscriber(modid = TConstruct.MOD_ID, bus = Bus.GAME)
public class FluidEvents {
  @SubscribeEvent
  static void onFurnaceFuel(FurnaceFuelBurnTimeEvent event) {
    if (event.getItemStack().getItem() == TinkerFluids.blazingBlood.asItem()) {
      // 150% efficiency compared to lava bucket, compare to casting blaze rods, which cast into 120%
      event.setBurnTime(30000);
    }
  }
}
