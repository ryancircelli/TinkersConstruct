package slimeknights.tconstruct.library.tools.item.armor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModelDispatcher;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;

import java.util.function.Consumer;

/**
 * Armor model that applies multiple texture layers in order.
 * <p>
 * The {@code getArmorTexture} override this class carried is gone. It returned a blank dummy texture so that vanilla's
 * armor layer drew nothing on top of Tinkers' own model; in 1.21 the layers a material renders are
 * {@link net.minecraft.world.item.ArmorMaterial#layers()}, and {@link DummyArmorMaterial} has none, so vanilla's layer
 * renderer never reaches the texture lookup at all. The dummy texture had nothing left to suppress.
 */
public class MultilayerArmorItem extends ModifiableArmorItem {
  private final ResourceLocation name;
  public MultilayerArmorItem(ModifiableArmorMaterial material, ArmorItem.Type slot, Properties properties) {
    this(material, slot, properties, material.getId());
  }

  public MultilayerArmorItem(ModifiableArmorMaterial material, ArmorItem.Type slot, Properties properties, ResourceLocation name) {
    super(material, slot, properties);
    this.name = name;
  }

  public MultilayerArmorItem(DummyArmorMaterial material, ArmorItem.Type slot, Properties properties, ToolDefinition toolDefinition) {
    this(material, slot, properties, toolDefinition, material.getId());
  }

  public MultilayerArmorItem(DummyArmorMaterial material, ArmorItem.Type slot, Properties properties, ToolDefinition toolDefinition, ResourceLocation name) {
    super(material, slot, properties, toolDefinition);
    this.name = name;
  }

  @Override
  public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(new ArmorModelDispatcher() {
      @Override
      protected ResourceLocation getName() {
        return name;
      }
    });
  }
}
