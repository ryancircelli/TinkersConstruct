package slimeknights.tconstruct.gadgets.capability;

import lombok.RequiredArgsConstructor;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import slimeknights.tconstruct.common.network.TinkerNetwork;

import java.util.List;

/**
 * Piggyback passenger-sync state for a player, stored as a {@link PiggybackCapability#PIGGYBACK} attachment.
 * <p>
 * Does not serialize, as the world saves the entities riding along already; they just dismount on logout.
 */
@RequiredArgsConstructor
public class PiggybackHandler {
  /** Player this attachment is on */
  private final Player riddenPlayer;
  /** Last found list of passengers, used to detect a change worth syncing */
  private List<Entity> lastPassengers;

  /** Updates the passengers on the back, syncing to the client if they changed */
  public void updatePassengers() {
    if (!this.riddenPlayer.getPassengers().equals(this.lastPassengers)) {
      if (this.riddenPlayer instanceof ServerPlayer) {
        TinkerNetwork.getInstance().sendVanillaPacket(this.riddenPlayer, new ClientboundSetPassengersPacket(this.riddenPlayer));
      }
    }
    this.lastPassengers = this.riddenPlayer.getPassengers();
  }
}
