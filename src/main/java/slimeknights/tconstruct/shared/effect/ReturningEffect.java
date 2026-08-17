package slimeknights.tconstruct.shared.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerEffect;
import slimeknights.tconstruct.library.events.teleport.ReturningTeleportEvent;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.utils.TeleportHelper;

import java.util.Optional;

public class ReturningEffect extends TinkerEffect {
  private static final ResourceLocation KEY = TConstruct.getResource("returning");
  /** Key of the stored position within the persistent data compound */
  private static final String POS = "pos";
  public ReturningEffect() {
    super(MobEffectCategory.NEUTRAL, 0xa92dff, true);
    NeoForge.EVENT_BUS.addListener(this::storeReturnPosition);
  }

  /**
   * Called to set the return position when the effect is added.
   * @implNote  Named apart from the event it handles as 1.21 added {@link net.minecraft.world.effect.MobEffect#onEffectAdded},
   *            which would make {@code this::onEffectAdded} an inexact method reference.
   */
  private void storeReturnPosition(MobEffectEvent.Added event) {
    // store entity's current position when the effect is added
    LivingEntity entity = event.getEntity();
    if (!entity.level().isClientSide() && event.getOldEffectInstance() == null && event.getEffectInstance().getEffect().value() == this) {
      ModDataNBT data = PersistentDataCapability.getData(entity);
      // 1.21 writes block positions as a bare int array tag, so it needs a compound of its own to hold the dimension
      CompoundTag pos = new CompoundTag();
      pos.put(POS, NbtUtils.writeBlockPos(entity.blockPosition()));
      pos.putString("dimension", entity.level().dimension().location().toString());
      data.put(KEY, pos);
    }
  }

  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    return duration == 1;
  }

  @Override
  public boolean applyEffectTick(LivingEntity living, int amplifier) {
    ModDataNBT data = PersistentDataCapability.getData(living);
    if (data.contains(KEY, Tag.TAG_COMPOUND)) {
      CompoundTag tag = data.getCompound(KEY);
      ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
      // no teleporting if you switched dimensions
      // TODO: look into cross dimensional teleport, its doable with entity#teleportTo
      if (dimension != null && dimension.equals(living.level().dimension().location())) {
        Optional<BlockPos> pos = NbtUtils.readBlockPos(tag, POS);
        if (pos.isPresent()) {
          BlockPos target = pos.get();
          TeleportHelper.tryTeleport(new ReturningTeleportEvent(living, target.getX(), target.getY(), target.getZ()));
        }
      }
    }
    return true;
  }
}
