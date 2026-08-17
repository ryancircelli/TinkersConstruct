package slimeknights.tconstruct.tools.logic;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.tools.entity.ModifiableArrow;

/**
 * Dispenser behavior for a modifiable arrow item.
 * @apiNote  {@code AbstractProjectileDispenseBehavior}, the 1.20 parent, is gone: 1.21 builds a dispensed projectile
 * through {@link ProjectileItem#asProjectile(Level, Position, ItemStack, Direction)} and dispenses it with
 * {@link ProjectileDispenseBehavior}. That constructor rejects any item not implementing {@link ProjectileItem} and
 * {@code ModifiableArrowItem} does not, so this stays a hand written behavior. The body below is
 * {@code ProjectileDispenseBehavior#execute} with {@link ProjectileItem.DispenseConfig#DEFAULT}'s position, power and
 * uncertainty inlined, which is what a vanilla arrow gets, so a dispensed Tinkers arrow flies like one and keeps the
 * tool data {@link ModifiableArrow#onCreate(ItemStack, net.minecraft.world.entity.LivingEntity)} reads off the stack.
 */
public class ModifiableArrowDispenserBehavior extends DefaultDispenseItemBehavior {
  public static final ModifiableArrowDispenserBehavior INSTANCE = new ModifiableArrowDispenserBehavior();

  private ModifiableArrowDispenserBehavior() {}

  @Override
  public ItemStack execute(BlockSource source, ItemStack stack) {
    Level level = source.level();
    Direction direction = source.state().getValue(DispenserBlock.FACING);
    Position position = DispenserBlock.getDispensePosition(source, 0.7, new Vec3(0, 0.1, 0));
    ModifiableArrow arrow = new ModifiableArrow(level, position.x(), position.y(), position.z());
    arrow.onCreate(stack, null);
    arrow.pickup = Pickup.ALLOWED;
    arrow.shoot(direction.getStepX(), direction.getStepY(), direction.getStepZ(), 1.1F, 6.0F);
    level.addFreshEntity(arrow);
    stack.shrink(1);
    return stack;
  }

  @Override
  protected void playSound(BlockSource source) {
    source.level().levelEvent(1002, source.pos(), 0);
  }
}
