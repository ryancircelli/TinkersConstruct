/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package slimeknights.tconstruct.library.client.model;

import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.data.loadable.Loadable;
import slimeknights.mantle.data.loadable.common.FluidStackLoadable;
import slimeknights.mantle.data.loadable.mapping.CompactLoadable;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Extension of {@link DynamicFluidContainerModel} with two additional features: baked tints and fluid stack sensitive models.
 * Does not handle covers as I have never seen a need for them, and it means less code duplication (plus the forge model does the whole cover is mask thing wrong compared to 1.18).
 */
public record FluidContainerModel(FluidStack fluid, boolean flipGas) implements IUnbakedGeometry<FluidContainerModel> {
  public static final IGeometryLoader<FluidContainerModel> LOADER = FluidContainerModel::deserialize;

  /** Clone of same named field from {@link DynamicFluidContainerModel} */
  public static final Transformation FLUID_TRANSFORM = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1.002f), new Quaternionf());

  /**
   * Reads this model's fluid: either a bare fluid id, or an object naming the fluid alongside the components that
   * differ from its defaults.
   * <p>
   * 1.20 spelled the object form {@code {"name": ..., "nbt": {...}}} and read the tag through Forge's
   * {@code CraftingHelper.getNBT}. A {@link FluidStack} has no tag in 1.21 - its extra data is a
   * {@link net.minecraft.core.component.DataComponentPatch} - so the {@code nbt} key could not have survived under any
   * spelling. Reading through Mantle's own fluid stack loadable rather than reinventing the object form puts this model
   * on the same {@code {"fluid": ..., "components": {...}}} shape as every other fluid Tinkers reads from JSON, and
   * keeps the bare-id form working exactly as it did.
   */
  private static final Loadable<FluidStack> FLUID = CompactLoadable.of(
    FluidStackLoadable.OPTIONAL_BUCKET_NBT,
    FluidStackLoadable.OPTIONAL_BUCKET,
    FluidStack::isComponentsPatchEmpty);

  /** Deserializes this model from JSON */
  public static FluidContainerModel deserialize(JsonObject json, JsonDeserializationContext context) {
    FluidStack fluidStack = FluidStack.EMPTY;
    if (json.has("fluid")) {
      fluidStack = FLUID.convert(json.get("fluid"), "fluid");
    }
    boolean flipGas = GsonHelper.getAsBoolean(json, "flip_gas", true);
    return new FluidContainerModel(fluidStack, flipGas);
  }

  /** Gets the given sprite, or null if the texture is not present in the model */
  @Nullable
  private static TextureAtlasSprite getSprite(IGeometryBakingContext context, Function<Material,TextureAtlasSprite> spriteGetter, String key) {
    if (context.hasMaterial(key)) {
      return spriteGetter.apply(context.getMaterial(key));
    }
    return null;
  }

  private static BakedModel bakeInternal(IGeometryBakingContext context, Function<Material,TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides, FluidStack fluid, boolean flipGas) {
    // get basic sprites
    IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluid.getFluid());
    TextureAtlasSprite baseSprite = getSprite(context, spriteGetter, "base");
    TextureAtlasSprite fluidSprite = !fluid.isEmpty() ? spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, clientFluid.getStillTexture(fluid))) : null;

    // determine particle
    TextureAtlasSprite particleSprite = getSprite(context, spriteGetter, "particle");
    if (particleSprite == null) particleSprite = fluidSprite;
    if (particleSprite == null) particleSprite = baseSprite;
    if (particleSprite == null) {
      TConstruct.LOG.error("No valid particle sprite for fluid container model, you should supply either 'base' or 'particle'");
      particleSprite = spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, MissingTextureAtlasSprite.getLocation()));
    }

    // if its a gas and we flipping, flip it
    if (flipGas && !fluid.isEmpty() && fluid.getFluid().getFluidType().isLighterThanAir()) {
      modelState = new SimpleModelState(modelState.getRotation().compose(new Transformation(null, new Quaternionf(0, 0, 1, 0), null, null)));
    }

    // start building the mode
    CompositeModel.Baked.Builder modelBuilder = CompositeModel.Baked.builder(context, particleSprite, overrides, context.getTransforms());
    RenderTypeGroup renderTypes = DynamicFluidContainerModel.getLayerRenderTypes(false);

    // add in the base
    if (baseSprite != null) {
      // createUnbakedItemElements takes the sprite itself rather than its contents in 1.21, and bakeElements no longer
      // takes a location to name in its errors
      modelBuilder.addQuads(renderTypes, UnbakedGeometryHelper.bakeElements(
        UnbakedGeometryHelper.createUnbakedItemElements(0, baseSprite),
        $ -> baseSprite, modelState
      ));
    }

    // add in fluid
    if (fluidSprite != null) {
      List<BakedQuad> quads = UnbakedGeometryHelper.bakeElements(
        UnbakedGeometryHelper.createUnbakedItemMaskElements(1, spriteGetter.apply(context.getMaterial("fluid"))),
        $ -> fluidSprite,
        new SimpleModelState(modelState.getRotation().compose(FLUID_TRANSFORM), modelState.isUvLocked())
      );

      // apply light
      RenderTypeGroup fluidRenderTypes = renderTypes;
      int light = fluid.getFluid().getFluidType().getLightLevel(fluid);
      if (light > 0) {
        fluidRenderTypes = DynamicFluidContainerModel.getLayerRenderTypes(true);
        QuadTransformers.settingEmissivity(light).processInPlace(quads);
      }
      // apply color
      int color = clientFluid.getTintColor(fluid);
      if (color != -1) {
        ColoredBlockModel.applyColorQuadTransformer(color).processInPlace(quads);
      }
      modelBuilder.addQuads(fluidRenderTypes, quads);
    }
    return modelBuilder.build();
  }

  @Override
  public BakedModel bake(IGeometryBakingContext context, ModelBaker bakery, Function<Material,TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
    // We need to disable GUI 3D and block lighting for this to render properly.
    // The standalone context still wants a location for its own getModelName(), but bake no longer receives one and
    // nothing here reads the name back, so NeoForge's own "no meaningful location" sentinel is what it gets.
    context = StandaloneGeometryBakingContext.builder(context).withGui3d(false).withUseBlockLight(false).build(StandaloneGeometryBakingContext.LOCATION);
    // only do contained fluid if we did not set the fluid in the model properties
    if (fluid.isEmpty()) {
      overrides = new ContainedFluidOverrideHandler(context, overrides, modelState, flipGas);
    }
    return bakeInternal(context, spriteGetter, modelState, overrides, fluid, flipGas);
  }

  /** Handles swapping the model based on the contained fluid */
  @RequiredArgsConstructor
  private static final class ContainedFluidOverrideHandler extends ItemOverrides {
    private final Map<FluidStack,BakedModel> cache = Maps.newHashMap(); // contains all the baked models since they'll never change

    private final IGeometryBakingContext context;
    private final ItemOverrides nested;
    private final ModelState modelState;
    private final boolean flipGas;


    /** Gets the model directly, for creating the cached models */
    private BakedModel getUncahcedModel(FluidStack fluid) {
      return bakeInternal(context, Material::sprite, modelState, ItemOverrides.EMPTY, fluid, flipGas);
    }

    @Override
    public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
      BakedModel overriden = nested.resolve(originalModel, stack, world, entity, seed);
      if (overriden != originalModel) return overriden;
      Optional<FluidStack> optional = FluidUtil.getFluidContained(stack);
      if (optional.isPresent()) {
        FluidStack fluid = optional.get();
        fluid.setAmount(FluidType.BUCKET_VOLUME); // cache considers amount, so ensure its consistent
        return cache.computeIfAbsent(fluid, this::getUncahcedModel);
      }
      return originalModel;
    }
  }
}
