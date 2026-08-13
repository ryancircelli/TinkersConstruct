package slimeknights.tconstruct.tools.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.tools.TinkerTools;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Internal item used by crystalshot modifier */
public class CrystalshotItem extends ArrowItem {
  /** Possible variants for a random crystalshot, so addons can register their own if desired */
  public static final List<String> RANDOM_VARIANTS;
  static {
    RANDOM_VARIANTS = new ArrayList<>();
    RANDOM_VARIANTS.add("amethyst");
    RANDOM_VARIANTS.add("earthslime");
    RANDOM_VARIANTS.add("skyslime");
    RANDOM_VARIANTS.add("ichor");
    RANDOM_VARIANTS.add("enderslime");
    RANDOM_VARIANTS.add("quartz");
  }
  /**
   * Key holding the variant on the stack, inside {@code minecraft:custom_data}, and on the entity.
   * @apiNote Item NBT is gone in 1.21 and this key is not a Tinkers-only concern: it is the string
   * {@link slimeknights.tconstruct.library.modifiers.modules.behavior.InfinityModule}'s {@code variant_tag} field
   * names from a datapack, so turning it into a data component type would change that module's JSON for every addon
   * using it. {@code minecraft:custom_data} is where an arbitrary named key lives now, and the hazard T10 recorded for
   * it - a raw data modifier handed the whole compound could clear it - cannot reach here, because the stack it sits
   * on is ammo rather than a tool and no modifier ever runs on it.
   */
  public static final String TAG_VARIANT = "variant";
  public CrystalshotItem(Properties props) {
    super(props);
  }

  @Override
  public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
    CrystalshotEntity arrow = new CrystalshotEntity(level, shooter);
    arrow.setVariant(getVariant(ammo, shooter.getRandom()));
    return arrow;
  }

  @Override
  public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
    CrystalshotEntity arrow = new CrystalshotEntity(TinkerTools.crystalshotEntity.get(), level);
    arrow.setPos(pos.x(), pos.y(), pos.z());
    arrow.pickup = Pickup.ALLOWED;
    arrow.setVariant(getVariant(stack, level.getRandom()));
    return arrow;
  }

  /** Reads the variant off a stack, resolving "random" against the given source */
  private static String getVariant(ItemStack stack, RandomSource random) {
    String variant = "random";
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data != null && data.getUnsafe().contains(TAG_VARIANT, Tag.TAG_STRING)) {
      variant = data.getUnsafe().getString(TAG_VARIANT);
    }
    if ("random".equals(variant)) {
      variant = RANDOM_VARIANTS.get(random.nextInt(RANDOM_VARIANTS.size()));
    }
    return variant;
  }

  @Override
  public boolean isInfinite(ItemStack ammo, ItemStack bow, LivingEntity shooter) {
    // Enchantments are a datapack registry now, so an enchantment is only nameable as a ResourceKey without a level to
    // resolve it against. ItemStack#getEnchantmentLevel takes a Holder, and the shooter is the nearest thing here that
    // knows a registry, so the key is resolved through it.
    return shooter.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                  .get(Enchantments.INFINITY)
                  .map(bow::getEnchantmentLevel)
                  .orElse(0) > 0;
  }

  /** Creates a crystal shot with the given variant */
  public static ItemStack withVariant(String variant, int size) {
    ItemStack stack = new ItemStack(TinkerTools.crystalshotItem, size);
    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_VARIANT, variant));
    return stack;
  }

  public static class CrystalshotEntity extends AbstractArrow {
    private static final EntityDataAccessor<String> SYNC_VARIANT = SynchedEntityData.defineId(CrystalshotEntity.class, EntityDataSerializers.STRING);

    public CrystalshotEntity(EntityType<? extends CrystalshotEntity> type, Level level) {
      super(type, level);
      setSoundEvent(Sounds.CRYSTALSHOT.getSound());
    }

    public CrystalshotEntity(Level level, LivingEntity shooter) {
      // the pickup stack is the arrow's own item in 1.21 rather than something the entity computes on demand; the
      // variant is not known yet at this point, so getDefaultPickupItem's answer stands in and getPickupItem overrides
      super(TinkerTools.crystalshotEntity.get(), shooter, level, new ItemStack(TinkerTools.crystalshotItem), null);
      setSoundEvent(Sounds.CRYSTALSHOT.getSound());
    }

    @Override
    public void setSoundEvent(SoundEvent sound) {
      if (sound != SoundEvents.ARROW_HIT && sound != SoundEvents.CROSSBOW_HIT) {
        super.setSoundEvent(sound);
      }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(SYNC_VARIANT, "");
    }

    /**
     * @apiNote 1.21 stores an arrow's pickup stack in a field set at construction instead of asking the entity for it,
     * and the variant is chosen after the constructor returns, so both halves are overridden: this one for the stack
     * the field is seeded with, {@link #getPickupItem()} for what a player actually picks up.
     */
    @Override
    protected ItemStack getDefaultPickupItem() {
      return withVariant(getVariant(), 1);
    }

    /** Gets the texture variant of this shot */
    public String getVariant() {
      String variant = this.entityData.get(SYNC_VARIANT);
      if (variant.isEmpty()) {
        return "amethyst";
      }
      return variant;
    }

    /** Sets the arrow's variant */
    public void setVariant(String variant) {
      this.entityData.set(SYNC_VARIANT, variant);
    }

    @Override
    public ItemStack getPickupItem() {
      return getDefaultPickupItem();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putString(TAG_VARIANT, getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      setVariant(tag.getString(TAG_VARIANT));
    }
  }
}
