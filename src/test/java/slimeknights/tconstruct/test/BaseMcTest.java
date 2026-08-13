package slimeknights.tconstruct.test;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for a test that needs the vanilla registries populated: items, blocks, attributes, particles and the
 * rest of what {@link Bootstrap} fills in. Extend it rather than calling {@link Bootstrap#bootStrap()} by hand, as
 * the version has to be set first and the bootstrap is only allowed to run once.
 * <p>
 * The version is asked for with {@link SharedConstants#tryDetectVersion()} rather than set from a stub. 1.20 ran
 * this suite on a bare classpath where nothing had a version and nothing could detect one, so the test invented a
 * {@code TestWorldVersion} and pushed it in. Since T13 the suite runs through NeoForge's test launcher, which has
 * already set the real 1.21.1 version before any test class loads - and {@code SharedConstants.setVersion} throws
 * {@code Cannot override the current game version!} rather than accepting a second, different one.
 * {@code tryDetectVersion} is a no-op when a version is present, reads the real one out of {@code /version.json}
 * when it is not, and falls back to {@code DetectedVersion.BUILT_IN} when even that is missing. It is therefore
 * correct in all three cases, and the stub has no remaining caller.
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
 *       id as an argument, saying so in its own javadoc. {@code TestModContainer} and {@code TestModInfo} were the
 *       stubs that answered it; they implemented loader SPI interfaces that all changed shape, nothing would have
 *       used the result, and they had no caller left in either source set, so T22 deleted them rather than finish
 *       porting them.</li>
 * </ul>
 */
public class BaseMcTest {
  @BeforeAll
  static void setUpRegistries() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    // Every test class starts from unfrozen built-in registries, rather than each one that writes to a registry
    // arranging it for itself. Two things in 1.21 write where 1.20 did not - constructing an Item asks the registry
    // for an intrusive holder, and building a HolderLookup over the built-ins re-freezes them - so whether a given
    // class found them frozen depended on which other class had run first, and JUnit does not promise an order.
    TestRegistries.unfreezeBuiltIns();
  }

  /**
   * Creates an empty play buffer.
   * @apiNote  Every play payload is written to a {@link RegistryFriendlyByteBuf} in 1.21, because a stack's data
   *           components may name a datapack registry. The access comes from {@link TestRegistries}, which layers
   *           the datapack registries a fixture can name over the built-in ones; it is the same access
   *           {@code RoundTripAssertions} uses. This replaced a bare
   *           {@code RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)}, which carried the static
   *           registries only and so could not encode a holder of anything datapack-scoped.
   */
  protected static RegistryFriendlyByteBuf networkBuffer() {
    return new RegistryFriendlyByteBuf(Unpooled.buffer(), TestRegistries.access());
  }
}
