package slimeknights.tconstruct.tools.modifiers.upgrades.ranged;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.interaction.EntityInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

public class SinistralModifier extends Modifier implements GeneralInteractionModifierHook, EntityInteractionModifierHook {
  @Override
  protected void registerHooks(Builder hookBuilder) {
    super.registerHooks(hookBuilder);
    hookBuilder.addHook(this, ModifierHooks.GENERAL_INTERACT, ModifierHooks.ENTITY_INTERACT);
  }

  @Override
  public InteractionResult afterEntityUse(IToolStackView tool, ModifierEntry modifier, Player player, LivingEntity target, InteractionHand hand, InteractionSource source) {
    return onToolUse(tool, modifier, player, hand, source);
  }

  @Override
  public InteractionResult onToolUse(IToolStackView tool, ModifierEntry modifier, Player player, InteractionHand hand, InteractionSource source) {
    if (source == InteractionSource.LEFT_CLICK && hand == InteractionHand.MAIN_HAND && !tool.isBroken()) {
      // 1.21 keeps the loaded ammo in the crossbow's own charged projectiles component instead of the tool's
      // persistent data, so the ammo is read off the stack rather than the tool
      ItemStack bow = player.getItemInHand(hand);
      ItemStack heldAmmo = ModifiableCrossbowItem.getChargedAmmo(bow);
      if (!heldAmmo.isEmpty()) {
        ModifiableCrossbowItem.fireCrossbow(tool, bow, player, hand, heldAmmo);
        return InteractionResult.CONSUME;
      }
    }
    return InteractionResult.PASS;
  }
}
