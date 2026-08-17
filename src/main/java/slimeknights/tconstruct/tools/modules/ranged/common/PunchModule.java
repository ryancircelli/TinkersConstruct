package slimeknights.tconstruct.tools.modules.ranged.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.json.LevelingValue;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.entity.ProjectileWithKnockback;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileLaunchModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition.ConditionalModule;
import slimeknights.tconstruct.library.module.HookProvider;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

import javax.annotation.Nullable;
import java.util.List;

/** Module implementing the punch modifier */
public record PunchModule(LevelingValue amount, ModifierCondition<IToolStackView> condition) implements ModifierModule, ProjectileLaunchModifierHook.NoShooter, ConditionalModule<IToolStackView> {
  private static final List<ModuleHook<?>> DEFAULT_HOOKS = HookProvider.<PunchModule>defaultHooks(ModifierHooks.PROJECTILE_LAUNCH, ModifierHooks.PROJECTILE_SHOT);
  public static final RecordLoadable<PunchModule> LOADER = RecordLoadable.create(LevelingValue.LOADABLE.directField(PunchModule::amount), ModifierCondition.TOOL_FIELD, PunchModule::new);

  @Override
  public RecordLoadable<PunchModule> getLoader() {
    return LOADER;
  }

  @Override
  public List<ModuleHook<?>> getDefaultHooks() {
    return DEFAULT_HOOKS;
  }

  /**
   * Adds knockback to an arrow.
   * @apiNote  1.21 deleted {@code AbstractArrow#setKnockback} along with the field behind it; an arrow's knockback is
   * read at hit time from the punch enchantment on the weapon it remembers being fired from
   * ({@code AbstractArrow#doKnockback} calls {@code EnchantmentHelper#modifyKnockback} with a base of zero). Punch adds
   * exactly 1 knockback per level, the same unit the old field used and fed to the same push formula, so writing the
   * level onto the arrow's own copy of the weapon reproduces the 1.20 number. The copy belongs to the arrow and is
   * never given back to the shooter, so the enchantment is not visible on the tool.
   * <p>
   * An arrow with no weapon stack - one that was not fired from an item at all - has nowhere to record this, which is
   * the one case 1.20 could reach and 1.21 cannot.
   */
  private static void addArrowKnockback(AbstractArrow arrow, float amount) {
    ItemStack weapon = arrow.getWeaponItem();
    if (weapon != null && !weapon.isEmpty()) {
      weapon.enchant(arrow.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH), (int) amount);
    }
  }

  @Override
  public void onProjectileShoot(IToolStackView tool, ModifierEntry modifier, @Nullable LivingEntity shooter, ItemStack ammo, Projectile projectile, @Nullable AbstractArrow arrow, ModDataNBT persistentData, boolean primary) {
    if (condition.matches(tool, modifier)) {
      float amount = this.amount.compute(modifier.getEffectiveLevel());
      if (amount > 0) {
        // our own projectiles keep taking the float directly, only vanilla arrows have to round to an enchantment level
        if (projectile instanceof ProjectileWithKnockback withKnockback) {
          withKnockback.addKnockback(amount);
        } else if (arrow != null) {
          addArrowKnockback(arrow, amount);
        }
      }
    }
  }
}
