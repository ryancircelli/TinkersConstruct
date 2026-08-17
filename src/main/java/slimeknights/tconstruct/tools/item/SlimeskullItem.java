package slimeknights.tconstruct.tools.item;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.armor.ArmorModelManager.ArmorModelDispatcher;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.tools.client.SlimeskullArmorModel;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/** This item is mainly to return the proper model for a slimeskull */
public class SlimeskullItem extends ModifiableArmorItem {
  /** Model ID for our slimeskull. You may want your own for a custom slimeskull */
  public static final ResourceLocation MODEL_LOCATION = TConstruct.getResource("slimeskull");

  private final ResourceLocation name;

  public SlimeskullItem(ModifiableArmorMaterial material, ResourceLocation name, Properties properties) {
    super(material, ArmorItem.Type.HELMET, properties);
    this.name = name;
  }

  public SlimeskullItem(ModifiableArmorMaterial material, Properties properties) {
    this(material, material.getId(), properties);
  }

  /*
   * The getArmorTexture override is gone, for the reason MultilayerArmorItem's javadoc gives: it returned a blank
   * dummy texture to stop vanilla's armor layer drawing over Tinkers' own model, and a 1.21 material with no
   * ArmorMaterial#layers never reaches a texture lookup for vanilla's layer to make.
   */

  @Override
  public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(new ArmorModelDispatcher() {
      @Override
      protected ResourceLocation getName() {
        return name;
      }

      @Nonnull
      @Override
      public Model getGenericArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
        return SlimeskullArmorModel.INSTANCE.setup(living, stack, slot, original, getModel(stack));
      }
    });
  }
}
