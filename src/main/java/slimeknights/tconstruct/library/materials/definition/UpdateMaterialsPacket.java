package slimeknights.tconstruct.library.materials.definition;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import slimeknights.mantle.network.packet.IPacket;
import slimeknights.mantle.network.packet.PacketContext;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.utils.GenericTagUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
/**
 * Packet syncing the material list on login.
 * <p>
 * Its decode reads IDs and primitives and resolves nothing outside its own payload, which is what makes it safe to
 * handle in any order against the other three sync packets. See {@link slimeknights.tconstruct.library.utils.LazyDecode}
 * for the failure that rule exists to prevent.
 */
public class UpdateMaterialsPacket implements IPacket.Threadsafe {
  private final Map<MaterialId,IMaterial> materials;
  private final Map<MaterialId,MaterialId> redirects;
  private final Map<TagKey<IMaterial>,List<IMaterial>> tags;

  public UpdateMaterialsPacket(RegistryFriendlyByteBuf buffer) {
    int materialCount = buffer.readInt();
    ImmutableMap.Builder<MaterialId,IMaterial> materials = ImmutableMap.builder();

    for (int i = 0; i < materialCount; i++) {
      MaterialId id = new MaterialId(buffer.readResourceLocation());
      int tier = buffer.readVarInt();
      int sortOrder = buffer.readVarInt();
      boolean craftable = buffer.readBoolean();
      boolean hidden = buffer.readBoolean();
      materials.put(id, new Material(id, tier, sortOrder, craftable, hidden));
    }
    this.materials = materials.build();
    // process redirects
    int redirectCount = buffer.readVarInt();
    if (redirectCount == 0) {
      this.redirects = Collections.emptyMap();
    } else {
      this.redirects = new HashMap<>(redirectCount);
      for (int i = 0; i < redirectCount; i++) {
        this.redirects.put(new MaterialId(buffer.readUtf()), new MaterialId(buffer.readUtf()));
      }
    }
    this.tags = GenericTagUtil.decodeTags(buffer, MaterialManager.REGISTRY_KEY, id -> this.materials.get(new MaterialId(id)));
  }

  @Override
  public void encode(RegistryFriendlyByteBuf buffer) {
    buffer.writeInt(this.materials.size());
    this.materials.values().forEach(material -> {
      buffer.writeResourceLocation(material.getIdentifier());
      buffer.writeVarInt(material.getTier());
      buffer.writeVarInt(material.getSortOrder());
      buffer.writeBoolean(material.isCraftable());
      buffer.writeBoolean(material.isHidden());
    });
    buffer.writeVarInt(this.redirects.size());
    this.redirects.forEach((key, value) -> {
      buffer.writeUtf(key.toString());
      buffer.writeUtf(value.toString());
    });
    GenericTagUtil.encodeTags(buffer, IMaterial::getIdentifier, this.tags);
  }

  @Override
  public void handleThreadsafe(PacketContext context) {
    MaterialRegistry.updateMaterialsFromServer(this);
  }
}
