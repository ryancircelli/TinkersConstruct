package slimeknights.tconstruct.plugin.jsonthings;

import dev.gigaherz.jsonthings.things.IFlexBlock;
import dev.gigaherz.jsonthings.things.serializers.FlexBlockType;
import dev.gigaherz.jsonthings.things.serializers.FlexBlockType.DefaultTypeProperties;
import dev.gigaherz.jsonthings.things.serializers.IBlockSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.common.util.Lazy;
import slimeknights.mantle.data.loadable.Loadables;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.plugin.jsonthings.block.FlexBurningLiquidBlock;
import slimeknights.tconstruct.plugin.jsonthings.block.FlexMobEffectLiquidBlock;

import java.util.List;
import java.util.Objects;

/** Collection of custom block types added by Tinkers */
public class FlexBlockTypes {
  /**
   * Resolves the fluid for a fluid block
   * @apiNote  Resolved eagerly rather than through a {@link Lazy}: 1.21's {@code LiquidBlock} takes the fluid in its
   *           constructor, and JSON Things' {@code FlexLiquidBlock} followed it, so there is nothing left to defer.
   *           JSON Things builds its fluids before its blocks, matching the order vanilla registers the two.
   */
  private static FlowingFluid fluid(ResourceLocation name) {
    if (Loadables.FLUID.fromKey(name, "fluid") instanceof FlowingFluid flowing) {
      return flowing;
    }
    throw new RuntimeException("LiquidBlock requires a flowing fluid");
  }

  /** Initializes the block types */
  public static void init() {
    register("burning_liquid", data -> {
      ResourceLocation fluidField = Loadables.RESOURCE_LOCATION.getOrDefault(data, "fluid", null);
      int burnTime = GsonHelper.getAsInt(data, "burn_time");
      float damage = GsonHelper.getAsFloat(data, "damage");
      return (props, builder) -> {
        final List<Property<?>> _properties = builder.getProperties();
        return new FlexBurningLiquidBlock(props, builder.getPropertyDefaultValues(), fluid(Objects.requireNonNullElse(fluidField, builder.getRegistryName())), burnTime, damage) {
          @Override
          protected void createBlockStateDefinition(Builder<Block,BlockState> stateBuilder) {
            super.createBlockStateDefinition(stateBuilder);
            _properties.forEach(stateBuilder::add);
          }
        };
      };
    });
    register("mob_effect_liquid", data -> {
      ResourceLocation fluidField = Loadables.RESOURCE_LOCATION.getOrDefault(data, "fluid", null);
      ResourceLocation effectName = Loadables.RESOURCE_LOCATION.getIfPresent(data, "effect");
      int effectLevel = GsonHelper.getAsInt(data, "burn_time");
      return (props, builder) -> {
        final List<Property<?>> _properties = builder.getProperties();
        // MobEffectInstance takes a Holder in 1.21; the effect registry is the one to wrap it with, since a JSON
        // Things effect is a plain registry entry rather than something datapack-scoped
        Lazy<Holder<MobEffect>> effect = Lazy.of(() -> BuiltInRegistries.MOB_EFFECT.wrapAsHolder(Loadables.MOB_EFFECT.fromKey(effectName, "effect")));
        return new FlexMobEffectLiquidBlock(props, builder.getPropertyDefaultValues(), fluid(Objects.requireNonNullElse(fluidField, builder.getRegistryName())), () -> new MobEffectInstance(effect.get(), 5*20, effectLevel - 1)) {
          @Override
          protected void createBlockStateDefinition(Builder<Block,BlockState> stateBuilder) {
            super.createBlockStateDefinition(stateBuilder);
            _properties.forEach(stateBuilder::add);
          }
        };
      };
    });
  }

  /** Local helper to register our stuff */
  private static <T extends Block & IFlexBlock> void register(String name, IBlockSerializer<T> factory) {
    // JSON Things collapsed register's trailing layer/flag arguments into a DefaultTypeProperties builder; the values
    // are the same ones, and the two it does not name (ignited by lava, ticks randomly) already default to false.
    FlexBlockType.register(TConstruct.resourceString(name), factory,
                           DefaultTypeProperties.builder().defaultLayer("translucent").defaultSeeThrough(true).defaultReplaceable(true));
  }
}
