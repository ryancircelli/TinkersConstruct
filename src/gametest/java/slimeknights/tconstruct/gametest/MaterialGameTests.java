package slimeknights.tconstruct.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;

import java.util.concurrent.CompletableFuture;

/**
 * In-game behavior test for the materials pseudo-registry surviving a real datapack reload.
 * Runs in its own batch (rather than the default batch shared by every other test in this suite) since
 * {@link MinecraftServer#reloadResources} reloads every reload listener on the shared test server, not just
 * the material registry; isolating it in its own batch avoids racing other tests' recipe manager lookups
 * against the reload. This is the one flaky-risk case called out in the design notes.
 */
@GameTestHolder(TConstruct.MOD_ID)
@PrefixGameTestTemplate(false)
public class MaterialGameTests {
  /** Material count is unchanged and a known material still resolves after a simulated datapack reload */
  @GameTest(template = GameTestFixtures.TEMPLATE, batch = "material_sync", timeoutTicks = 600)
  public static void materialCountUnchangedAfterReload(GameTestHelper helper) {
    int countBefore = MaterialRegistry.getInstance().getAllMaterials().size();
    helper.assertTrue(countBefore > 0, "no materials were loaded before the reload");
    IMaterial ironBefore = MaterialRegistry.getMaterial(GameTestFixtures.IRON);
    helper.assertTrue(ironBefore != IMaterial.UNKNOWN, "iron material did not resolve before the reload");

    MinecraftServer server = helper.getLevel().getServer();
    CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());

    helper.startSequence()
          .thenWaitUntil(() -> helper.assertTrue(reload.isDone(), "datapack reload did not complete"))
          .thenExecute(() -> {
            int countAfter = MaterialRegistry.getInstance().getAllMaterials().size();
            helper.assertTrue(countAfter == countBefore, "material count changed after a datapack reload (" + countBefore + " -> " + countAfter + ")");
            IMaterial ironAfter = MaterialRegistry.getMaterial(GameTestFixtures.IRON);
            helper.assertTrue(ironAfter != IMaterial.UNKNOWN, "iron material no longer resolves after a datapack reload");
          })
          .thenSucceed();
  }
}
