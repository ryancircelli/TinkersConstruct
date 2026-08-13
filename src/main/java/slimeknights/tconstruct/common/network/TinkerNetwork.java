package slimeknights.tconstruct.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import slimeknights.mantle.network.NetworkWrapper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.definition.UpdateMaterialsPacket;
import slimeknights.tconstruct.library.materials.stats.UpdateMaterialStatsPacket;
import slimeknights.tconstruct.library.materials.traits.UpdateMaterialTraitsPacket;
import slimeknights.tconstruct.library.modifiers.UpdateModifiersPacket;
import slimeknights.tconstruct.library.modifiers.fluid.UpdateFluidEffectsPacket;
import slimeknights.tconstruct.library.tools.definition.UpdateToolDefinitionDataPacket;
import slimeknights.tconstruct.library.tools.layout.UpdateTinkerSlotLayoutsPacket;
import slimeknights.tconstruct.shared.network.GeneratePartTexturesPacket;
import slimeknights.tconstruct.smeltery.network.ChannelFlowPacket;
import slimeknights.tconstruct.smeltery.network.FaucetActivationPacket;
import slimeknights.tconstruct.smeltery.network.FluidUpdatePacket;
import slimeknights.tconstruct.smeltery.network.SmelteryFluidClickedPacket;
import slimeknights.tconstruct.smeltery.network.SmelteryTankUpdatePacket;
import slimeknights.tconstruct.smeltery.network.StructureErrorPositionPacket;
import slimeknights.tconstruct.smeltery.network.StructureUpdatePacket;
import slimeknights.tconstruct.tables.network.StationTabPacket;
import slimeknights.tconstruct.tables.network.TinkerStationRenamePacket;
import slimeknights.tconstruct.tables.network.TinkerStationSelectionPacket;
import slimeknights.tconstruct.tables.network.UpdateCraftingRecipePacket;
import slimeknights.tconstruct.tables.network.UpdateStationScreenPacket;
import slimeknights.tconstruct.tables.network.UpdateTinkerStationRecipePacket;
import slimeknights.tconstruct.tools.network.EntityMovementChangePacket;
import slimeknights.tconstruct.tools.network.InteractWithAirPacket;
import slimeknights.tconstruct.tools.network.PushBlockRowPacket;
import slimeknights.tconstruct.tools.network.SyncProjectileModifiersPacket;
import slimeknights.tconstruct.tools.network.TinkerControlPacket;
import slimeknights.tconstruct.tools.network.ToolContainerFluidUpdatePacket;

import javax.annotation.Nullable;

/**
 * Base network class for all tinkers logic
 * <p>
 * In general, if you need to send packets you should use your own network class
 */
public class TinkerNetwork extends NetworkWrapper {
  private static TinkerNetwork instance = null;

  /*
   * Network versions:
   * 1: 3.10.1 and before
   * 2: 3.10.2 - new material stat type; item removal
   * 3: 3.11.2+ - lost track of how much changed but its a lot
   * 4: 1.21 - a packet is a named payload rather than an index into the list below, and item and fluid stacks are
   *    data components on the wire. Nothing on this channel is byte compatible with a 1.20 build; since a 1.21 client
   *    cannot connect to a 1.20 server at all this is a statement of intent rather than a guard.
   */
  private TinkerNetwork() {
    super(TConstruct.getResource("network"), "4");
  }

  /** Gets the instance of the network */
  public static TinkerNetwork getInstance() {
    if (instance == null) {
      throw new IllegalStateException("Attempt to call network getInstance before network is setup");
    }
    return instance;
  }

  /**
   * Called during mod construction to setup the network.
   * <p>
   * Registration order stopped being the wire format when {@code SimpleChannel} did: a packet is called by an
   * identifier derived from its class name now, so this list may be reordered, or have an entry commented out, with no
   * effect on the packets that remain (M6 SS4).
   */
  public static void setup() {
    if (instance != null) {
      return;
    }
    instance = new TinkerNetwork();

    // shared
    instance.registerPacket(InventorySlotSyncPacket.class, InventorySlotSyncPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(UpdateNeighborsPacket.class, UpdateNeighborsPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(GeneratePartTexturesPacket.class, GeneratePartTexturesPacket::new, PacketFlow.CLIENTBOUND);
    // SyncPersistentDataPacket was here and is deleted rather than ported: NeoForge syncs a serialized data
    // attachment at exactly the three points the 1.20 class hand-wired listeners for, so PersistentDataCapability
    // needs no packet of its own (T10 SS1.2).

    // gadgets
    instance.registerPacket(EntityMovementChangePacket.class, EntityMovementChangePacket::new, PacketFlow.CLIENTBOUND);

    // tables
    instance.registerPacket(StationTabPacket.class, StationTabPacket::new, PacketFlow.SERVERBOUND);
    instance.registerPacket(TinkerStationRenamePacket.class, TinkerStationRenamePacket::new, PacketFlow.SERVERBOUND);
    instance.registerPacket(UpdateCraftingRecipePacket.class, UpdateCraftingRecipePacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(TinkerStationSelectionPacket.class, TinkerStationSelectionPacket::new, PacketFlow.SERVERBOUND);
    instance.registerPacket(UpdateTinkerSlotLayoutsPacket.class, UpdateTinkerSlotLayoutsPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(UpdateStationScreenPacket.class, buf -> UpdateStationScreenPacket.INSTANCE, PacketFlow.CLIENTBOUND);
    instance.registerPacket(UpdateTinkerStationRecipePacket.class, UpdateTinkerStationRecipePacket::new, PacketFlow.CLIENTBOUND);

    // tools
    instance.registerPacket(UpdateMaterialsPacket.class, UpdateMaterialsPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(UpdateMaterialStatsPacket.class, UpdateMaterialStatsPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(UpdateMaterialTraitsPacket.class, UpdateMaterialTraitsPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(UpdateToolDefinitionDataPacket.class, UpdateToolDefinitionDataPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(ToolContainerFluidUpdatePacket.class, ToolContainerFluidUpdatePacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(SyncProjectileModifiersPacket.class, SyncProjectileModifiersPacket::new, PacketFlow.CLIENTBOUND);

    // modifiers
    instance.registerPacket(TinkerControlPacket.class, TinkerControlPacket::read, PacketFlow.SERVERBOUND);
    instance.registerPacket(InteractWithAirPacket.class, InteractWithAirPacket::read, PacketFlow.SERVERBOUND);
    instance.registerPacket(UpdateModifiersPacket.class, UpdateModifiersPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(UpdateFluidEffectsPacket.class, UpdateFluidEffectsPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(PushBlockRowPacket.class, PushBlockRowPacket::new, PacketFlow.CLIENTBOUND);

    // smeltery
    instance.registerPacket(FluidUpdatePacket.class, FluidUpdatePacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(FaucetActivationPacket.class, FaucetActivationPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(ChannelFlowPacket.class, ChannelFlowPacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(SmelteryTankUpdatePacket.class, SmelteryTankUpdatePacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(StructureUpdatePacket.class, StructureUpdatePacket::new, PacketFlow.CLIENTBOUND);
    instance.registerPacket(SmelteryFluidClickedPacket.class, SmelteryFluidClickedPacket::new, PacketFlow.SERVERBOUND);
    instance.registerPacket(StructureErrorPositionPacket.class, StructureErrorPositionPacket::new, PacketFlow.CLIENTBOUND);
  }

  /**
   * Sends a vanilla packet to the given player
   * @param player  Player
   * @param packet  Packet
   */
  public void sendVanillaPacket(Entity player, Packet<?> packet) {
    if (player instanceof ServerPlayer serverPlayer) {
      serverPlayer.connection.send(packet);
    }
  }

  /**
   * Same as {@link #sendToClientsAround(Object, ServerLevel, BlockPos)}, but checks that the world is a serverworld
   * @param msg       Packet to send
   * @param world     World instance
   * @param position  Target position
   */
  public void sendToClientsAround(Object msg, @Nullable LevelAccessor world, BlockPos position) {
    if (world instanceof ServerLevel server) {
      sendToClientsAround(msg, server, position);
    }
  }

  /**
   * Sends a packet to the whole player list
   * @param targetedPlayer  Main player to target, if null uses whole list
   * @param playerList      Player list to use if main player is null
   * @param msg             Message to send
   */
  public void sendToPlayerList(@Nullable ServerPlayer targetedPlayer, PlayerList playerList, Object msg) {
    if (targetedPlayer != null) {
      sendTo(msg, targetedPlayer);
    } else {
      for (ServerPlayer player : playerList.getPlayers()) {
        sendTo(msg, player);
      }
    }
  }
}
