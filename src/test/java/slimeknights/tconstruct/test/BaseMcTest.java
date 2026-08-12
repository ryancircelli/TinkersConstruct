package slimeknights.tconstruct.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class BaseMcTest {

  @SuppressWarnings("unused")
  @BeforeAll
  static void setUpRegistries() {
    SharedConstants.setVersion(TestWorldVersion.INSTANCE);
    try (MockedStatic<NetworkHooks> mockNetwork = Mockito.mockStatic(NetworkHooks.class)) {
      Bootstrap.bootStrap();
    }
    ModLoadingContext.get().setActiveContainer(new TestModContainer(TestModInfo.INSTANCE));
  }
}
