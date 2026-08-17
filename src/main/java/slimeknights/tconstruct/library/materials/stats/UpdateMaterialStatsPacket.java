package slimeknights.tconstruct.library.materials.stats;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;
import slimeknights.mantle.data.registry.IdAwareComponentRegistry;
import slimeknights.mantle.network.packet.IPacket;
import slimeknights.mantle.network.packet.PacketContext;
import slimeknights.mantle.util.typed.TypedMapBuilder;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.utils.Util;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Packet syncing the material stats on login.
 * <p>
 * Each stat's payload is length prefixed, which is what lets a stat type the receiver does not know be skipped rather
 * than taken as a decoder exception. Without the prefix an unknown type also leaves the buffer at the wrong offset, so
 * "this client is missing an addon" costs the whole packet and therefore the connection. Stat types themselves are
 * registered during mod setup rather than by a datapack, so looking one up here is not the ordering problem the other
 * three sync packets have; see {@link slimeknights.tconstruct.library.utils.LazyDecode}.
 */
@Getter
@AllArgsConstructor
public class UpdateMaterialStatsPacket implements IPacket.Threadsafe {
  private static final Logger log = Util.getLogger("NetworkSync");

  protected final Map<MaterialId, Collection<IMaterialStats>> materialToStats;

  public UpdateMaterialStatsPacket(RegistryFriendlyByteBuf buffer) {
    this(buffer, MaterialRegistry.getInstance().getStatTypeLoader());
  }

  public UpdateMaterialStatsPacket(RegistryFriendlyByteBuf buffer, IdAwareComponentRegistry<MaterialStatType<?>> statTypes) {
    int materialCount = buffer.readVarInt();
    materialToStats = new HashMap<>(materialCount);
    for (int i = 0; i < materialCount; i++) {
      MaterialId id = new MaterialId(buffer.readResourceLocation());
      int statCount = buffer.readVarInt();
      List<IMaterialStats> statList = new ArrayList<>(statCount);
      for (int j = 0; j < statCount; j++) {
        ResourceLocation statId = buffer.readResourceLocation();
        int length = buffer.readVarInt();
        MaterialStatType<?> statType = statTypes.getValue(statId);
        if (statType == null) {
          // an unregistered stat type is a client missing an addon the server has, not a broken packet
          log.debug("Skipping unregistered material stat type '{}' for material '{}'", statId, id);
          buffer.skipBytes(length);
          continue;
        }
        IMaterialStats stats = decodeStat(buffer, length, statType, id);
        if (stats != null) {
          statList.add(stats);
        }
      }
      materialToStats.put(id, statList);
    }
  }

  /** Reads a single stat out of the length prefixed block it was written into, leaving the buffer past the block either way */
  @Nullable
  private static <T extends IMaterialStats> T decodeStat(RegistryFriendlyByteBuf buffer, int length, MaterialStatType<T> type, MaterialId material) {
    int end = buffer.readerIndex() + length;
    try {
      return type.getLoadable().decode(buffer, TypedMapBuilder.builder().put(MaterialStatType.CONTEXT_KEY, type).build());
    } catch (RuntimeException e) {
      log.error("Could not deserialize stat {} for material {}. Are client and server in sync?", type.getId(), material, e);
      return null;
    } finally {
      // the block's length is what the reader trusts, not how far the stat's own loadable happened to get
      buffer.readerIndex(end);
    }
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeVarInt(materialToStats.size());
    materialToStats.forEach((materialId, stats) -> {
      buffer.writeResourceLocation(materialId);
      buffer.writeVarInt(stats.size());
      for (IMaterialStats stat : stats) {
        encodeStat(buffer, stat, stat.getType(), materialId);
      }
    });
  }

  /**
   * Encodes a single material stat as an ID and a length prefixed block
   *
   * @param buffer     Buffer instance
   * @param stat       Stat to encode
   * @param material   Material being encoded
   */
  @SuppressWarnings("unchecked")
  private <T extends IMaterialStats> void encodeStat(RegistryFriendlyByteBuf buffer, IMaterialStats stat, MaterialStatType<T> type, MaterialId material) {
    buffer.writeResourceLocation(type.getId());
    ByteBuf scratch = Unpooled.buffer();
    try {
      type.getLoadable().encode(new RegistryFriendlyByteBuf(scratch, buffer.registryAccess(), buffer.getConnectionType()), (T) stat);
      buffer.writeVarInt(scratch.readableBytes());
      buffer.writeBytes(scratch);
    } catch (RuntimeException e) {
      TConstruct.LOG.error("Could not encode stat {} for material {}", stat.getIdentifier(), material, e);
      throw e;
    } finally {
      scratch.release();
    }
  }

  @Override
  public void handleThreadsafe(PacketContext context) {
    MaterialRegistry.updateMaterialStatsFromServer(this);
  }
}
