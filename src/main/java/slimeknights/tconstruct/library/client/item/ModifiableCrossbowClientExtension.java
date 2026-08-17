package slimeknights.tconstruct.library.client.item;

import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem;

import javax.annotation.Nullable;

/** Client extensions for modifiable crossbows. Adds in the arm pose when charged. */
public class ModifiableCrossbowClientExtension extends ModifiableItemClientExtension {
  public static final ModifiableCrossbowClientExtension INSTANCE = new ModifiableCrossbowClientExtension();

  protected ModifiableCrossbowClientExtension() {}

  @Nullable
  @Override
  public ArmPose getArmPose(LivingEntity living, InteractionHand hand, ItemStack stack) {
    // 1.21: the loaded ammo is `minecraft:charged_projectiles` rather than a compound in the tool's persistent data,
    // so this asks the item instead of digging through NBT that no longer exists
    if (!living.swinging && ModifiableCrossbowItem.isCharged(stack)) {
      return ArmPose.CROSSBOW_HOLD;
    }
    return ArmPose.ITEM;
  }
}
