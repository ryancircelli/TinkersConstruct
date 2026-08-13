package slimeknights.tconstruct.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for a test that needs the vanilla registries populated: items, blocks, attributes, particles and the
 * rest of what {@link Bootstrap} fills in. Extend it rather than calling {@link Bootstrap#bootStrap()} by hand, as
 * the version has to be set first and the bootstrap is only allowed to run once.
 * <p>
 * Both of 1.20's other two lines are gone, and neither has a 1.21 replacement to write:
 * <ul>
 *   <li>The bootstrap ran inside a {@code Mockito.mockStatic(NetworkHooks.class)}. {@code NetworkHooks} does not
 *       exist in NeoForge - a payload is registered on the mod bus and sent through {@code PacketDistributor} - so
 *       there is no static left to stub out.</li>
 *   <li>{@code ModLoadingContext.get().setActiveContainer(new TestModContainer(TestModInfo.INSTANCE))} answered
 *       Forge's ambient "which mod is loading right now" question. NeoForge has no such context for anything on
 *       this path to read: registration is explicit about its namespace, and Mantle's {@code RegistryAdapter} - the
 *       one caller in either mod that used to auto-detect a mod id from it - dropped that constructor and takes the
 *       id as an argument, saying so in its own javadoc. {@code TestModContainer} and {@code TestModInfo} are left
 *       behind on the test frontier with no caller; they implement loader SPI interfaces that all changed shape,
 *       and nothing would use the result.</li>
 * </ul>
 */
public class BaseMcTest {
  @BeforeAll
  static void setUpRegistries() {
    SharedConstants.setVersion(TestWorldVersion.INSTANCE);
    Bootstrap.bootStrap();
  }
}
