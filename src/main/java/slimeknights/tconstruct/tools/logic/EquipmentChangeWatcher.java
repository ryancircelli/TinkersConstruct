package slimeknights.tconstruct.tools.logic;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.events.ToolEquipmentChangeEvent;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.ComputableDataKey;
import slimeknights.tconstruct.library.tools.context.EquipmentChangeContext;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.EnumMap;
import java.util.Map;

/**
 * Runs the equipment change modifier hooks. The server gets told about equipment changes by
 * {@link LivingEquipmentChangeEvent}; the client is not, so it diffs the player's own slots every tick instead.
 */
public class EquipmentChangeWatcher {
  private EquipmentChangeWatcher() {}

  /**
   * Client side cache of the last seen equipment, per player.
   * @apiNote  1.20 attached this to the player as its own capability, with a provider, a {@code LazyOptional} and an
   * invalidate/revive dance. It is scratch data on a living entity that is never saved and never sent, which is
   * precisely what {@link TinkerDataCapability} already is, so it lives there as a key rather than growing a second
   * attachment of its own. That also keeps {@link #register()} free of the mod event bus, which it has no way to reach.
   */
  private static final ComputableDataKey<PlayerLastEquipment> LAST_EQUIPMENT = TConstruct.createKey("last_equipment", PlayerLastEquipment::new);

  /** Registers this watcher's listeners */
  public static void register() {
    // equipment change is used on both sides
    NeoForge.EVENT_BUS.addListener(EquipmentChangeWatcher::onEquipmentChange);

    // only need the tick based diff on the client, the server has the event above
    if (FMLEnvironment.dist == Dist.CLIENT) {
      NeoForge.EVENT_BUS.addListener(EquipmentChangeWatcher::onPlayerTick);
    }
  }


  /* Events */

  /** Serverside modifier hooks */
  private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
    runModifierHooks(event.getEntity(), event.getSlot(), event.getFrom(), event.getTo());
  }

  /**
   * Client side modifier hooks.
   * @apiNote  {@code TickEvent.PlayerTickEvent} with a phase field became a pair of events; {@code Phase.END} is
   * {@link PlayerTickEvent.Post}. Both phases fire on either side, so the side check stays for the integrated server.
   */
  private static void onPlayerTick(PlayerTickEvent.Post event) {
    Player player = event.getEntity();
    if (player.level().isClientSide) {
      TinkerDataCapability.getData(player).computeIfAbsent(LAST_EQUIPMENT).update(player);
    }
  }


  /* Helpers */

  /** Shared modifier hook logic */
  private static void runModifierHooks(LivingEntity entity, EquipmentSlot changedSlot, ItemStack original, ItemStack replacement) {
    EquipmentChangeContext context = new EquipmentChangeContext(entity, changedSlot, original, replacement);

    // first, fire event to notify an item was removed
    IToolStackView tool = context.getOriginalTool();
    if (tool != null && ModifierUtil.validArmorSlot(tool, changedSlot)) {
      for (ModifierEntry entry : tool.getModifierList()) {
        entry.getHook(ModifierHooks.EQUIPMENT_CHANGE).onUnequip(tool, entry, context);
      }
    }

    // next, fire event to notify an item was added
    tool = context.getReplacementTool();
    if (tool != null && ModifierUtil.validArmorSlot(tool, changedSlot)) {
      for (ModifierEntry entry : tool.getModifierList()) {
        entry.getHook(ModifierHooks.EQUIPMENT_CHANGE).onEquip(tool, entry, context);
      }
    }

    // finally, fire events on all other slots to say something changed
    for (EquipmentSlot otherSlot : EquipmentSlot.values()) {
      if (otherSlot != changedSlot) {
        tool = context.getValidTool(otherSlot);
        if (tool != null) {
          for (ModifierEntry entry : tool.getModifierList()) {
            entry.getHook(ModifierHooks.EQUIPMENT_CHANGE).onEquipmentChange(tool, entry, context, otherSlot);
          }
        }
      }
    }
    // fire event for modifiers that want to watch equipment when not equipped
    NeoForge.EVENT_BUS.post(new ToolEquipmentChangeEvent(context));
  }

  /* Required methods */

  /** Data class that runs actual update logic */
  protected static class PlayerLastEquipment {
    private final Map<EquipmentSlot,ItemStack> lastItems = new EnumMap<>(EquipmentSlot.class);

    private PlayerLastEquipment() {
      for (EquipmentSlot slot : EquipmentSlot.values()) {
        lastItems.put(slot, ItemStack.EMPTY);
      }
    }

    /**
     * Called on player tick to update the stacks and run the event.
     * @param player  Owning player, passed in as the data holder builds this without knowing who it is for
     */
    public void update(Player player) {
      for (EquipmentSlot slot : EquipmentSlot.values()) {
        ItemStack newStack = player.getItemBySlot(slot);
        ItemStack oldStack = lastItems.get(slot);
        if (!ItemStack.matches(oldStack, newStack)) {
          lastItems.put(slot, newStack.copy());
          runModifierHooks(player, slot, oldStack, newStack);
        }
      }
    }
  }
}
