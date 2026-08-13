package slimeknights.tconstruct.shared;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing.Builder;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import slimeknights.mantle.registration.deferred.PotionDeferredRegister;
import slimeknights.mantle.registration.deferred.PotionDeferredRegister.PotionType;
import slimeknights.mantle.registration.object.EnumObject;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerEffect;
import slimeknights.tconstruct.common.TinkerModule;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.shared.effect.AntigravityEffect;
import slimeknights.tconstruct.shared.effect.ReturningEffect;
import slimeknights.tconstruct.tools.modifiers.effect.BleedingEffect;
import slimeknights.tconstruct.tools.modifiers.effect.MagneticEffect;
import slimeknights.tconstruct.tools.modifiers.effect.RepulsiveEffect;
import slimeknights.tconstruct.tools.modifiers.traits.skull.SelfDestructiveModifier.SelfDestructiveEffect;
import slimeknights.tconstruct.world.TinkerWorld;

import javax.annotation.Nullable;

/** Handles registration for all status effects and potions in the mod */
public class TinkerEffects extends TinkerModule {
  private static final PotionDeferredRegister POTIONS = new PotionDeferredRegister(TConstruct.MOD_ID);

  /**
   * Attribute modifier ids for the effects below. 1.21 keys an attribute modifier by a {@link ResourceLocation}
   * rather than the random UUID string 1.20 wrote, so the id is now legible in a tooltip and stable by construction.
   */
  private static ResourceLocation effectModifier(String name) {
    return TConstruct.getResource("effect/" + name);
  }

  // slimy potions
  public static final DeferredHolder<MobEffect,TinkerEffect> experienced = MOB_EFFECTS.register("experienced", () -> new TinkerEffect(MobEffectCategory.BENEFICIAL, 0x82c873, true).addAttributeModifier(TinkerAttributes.EXPERIENCE_MULTIPLIER, effectModifier("experienced"), 0.25f, Operation.ADD_MULTIPLIED_BASE));
  public static final DeferredHolder<MobEffect,TinkerEffect> ricochet = MOB_EFFECTS.register("ricochet", () -> new TinkerEffect(MobEffectCategory.NEUTRAL, 0x01cbcd, true).addAttributeModifier(TinkerAttributes.KNOCKBACK_MULTIPLIER, effectModifier("ricochet"), 0.5f, Operation.ADD_MULTIPLIED_BASE));
  public static final DeferredHolder<MobEffect,TinkerEffect> enderference = MOB_EFFECTS.register("enderference", () -> new TinkerEffect(MobEffectCategory.HARMFUL, 0xD37CFF, true));
  /** Projectile persistent data key to allow ranged modifiers to hit endermen. */
  public static final ResourceLocation ENDERFERENCE_KEY = enderference.getId();

  // slimy cakes
  public static final DeferredHolder<MobEffect,TinkerEffect> bouncy = MOB_EFFECTS.register("bouncy", () -> new TinkerEffect(MobEffectCategory.BENEFICIAL, 0x71AC63, true).addAttributeModifier(TinkerAttributes.BOUNCY, effectModifier("bouncy"), 1, Operation.ADD_VALUE));
  public static final DeferredHolder<MobEffect,TinkerEffect> doubleJump = MOB_EFFECTS.register("double_jump", () -> new TinkerEffect(MobEffectCategory.BENEFICIAL, 0xA99B87, true).addAttributeModifier(TinkerAttributes.JUMP_COUNT, effectModifier("double_jump"), 1, Operation.ADD_VALUE));
  public static final DeferredHolder<MobEffect,AntigravityEffect> antigravity = MOB_EFFECTS.register("antigravity", AntigravityEffect::new);
  public static final DeferredHolder<MobEffect,ReturningEffect> returning = MOB_EFFECTS.register("returning", ReturningEffect::new);

  // modifier effects
  public static final DeferredHolder<MobEffect,BleedingEffect> bleeding = MOB_EFFECTS.register("bleeding", BleedingEffect::new);
  public static final DeferredHolder<MobEffect,MagneticEffect> magnetic = MOB_EFFECTS.register("magnetic", MagneticEffect::new);
  public static final DeferredHolder<MobEffect,TinkerEffect> selfDestructing = MOB_EFFECTS.register("self_destructing", SelfDestructiveEffect::new);
  public static final DeferredHolder<MobEffect,RepulsiveEffect> repulsive = MOB_EFFECTS.register("repulsive", RepulsiveEffect::new);
  public static final DeferredHolder<MobEffect,TinkerEffect> pierce = MOB_EFFECTS.register("pierce", () -> new TinkerEffect(MobEffectCategory.HARMFUL, 0xD1D37A, true).addAttributeModifier(Attributes.ARMOR, effectModifier("pierce"), -1, Operation.ADD_VALUE));
  // damage boost
  public static final DeferredHolder<MobEffect,TinkerEffect> conductive = MOB_EFFECTS.register("conductive", () -> new TinkerEffect(MobEffectCategory.HARMFUL, 0xF2D500, true));
  public static final DeferredHolder<MobEffect,TinkerEffect> venom = MOB_EFFECTS.register("venom", () -> new TinkerEffect(MobEffectCategory.HARMFUL, 0xA2935E, true));

  // potions
  public static final EnumObject<PotionType,Potion> experiencedPotion = POTIONS.registerTypes(experienced).withStrong().withLong().build();
  public static final EnumObject<PotionType,Potion> ricochetPotion = POTIONS.registerTypes(ricochet).withStrong().withLong().build();
  public static final EnumObject<PotionType,Potion> levitationPotion = POTIONS.registerTypes("levitation", () -> MobEffects.LEVITATION, 15 * 20, 0).withStrong().withLong(40 * 20, 0).build();
  public static final EnumObject<PotionType,Potion> enderferencePotion = POTIONS.registerTypes(enderference, 90 * 20, 0).withLong().build();

  /**
   * Initializes the effect and potion registers.
   * <p>
   * Unlike its neighbours this is a static call rather than {@code bus.register(new TinkerEffects())}: 1.20's
   * instance had one mod bus listener, common setup, and that listener's whole body was the brewing mixes, which are
   * a game bus event now. Nothing is left for the mod bus to call.
   * @param bus  Mod event bus, passed down from {@link TConstruct}'s constructor.
   */
  public static void init(IEventBus bus) {
    POTIONS.register(bus);
    // brewing is not a static list any more, see registerBrewing
    NeoForge.EVENT_BUS.addListener(TinkerEffects::registerBrewing);
  }

  /**
   * Registers our brewing recipes.
   * <p>
   * 1.20 mutated {@code PotionBrewing.POTION_MIXES}, a static list, from common setup. 1.21 builds an immutable
   * {@code PotionBrewing} per server from a {@code Builder}, and NeoForge fires this event while that builder is open;
   * the mixes are therefore rebuilt on every reload rather than added once, which is what makes them respond to a
   * datapack changing the potions they name.
   */
  private static void registerBrewing(RegisterBrewingRecipesEvent event) {
    Builder builder = event.getBuilder();
    brewing(builder, experiencedPotion,  Potions.AWKWARD, TinkerWorld.congealedSlime.get(SlimeType.EARTH).asItem());
    brewing(builder, ricochetPotion,     Potions.AWKWARD, TinkerWorld.congealedSlime.get(SlimeType.SKY).asItem());
    brewing(builder, levitationPotion,   Potions.AWKWARD, TinkerWorld.congealedSlime.get(SlimeType.ICHOR).asItem());
    brewing(builder, enderferencePotion, Potions.AWKWARD, TinkerWorld.congealedSlime.get(SlimeType.ENDER).asItem());
  }

  /**
   * Registers recipes for brewing, longer and stronger potions for the given object.
   * <p>
   * The ingredient is an {@link Item} rather than 1.20's {@code Ingredient}: {@code Builder#addMix} takes one item,
   * and all four of ours were a single congealed slime block anyway.
   */
  private static void brewing(Builder builder, EnumObject<PotionType,Potion> potion, Holder<Potion> base, Item ingredient) {
    Holder<Potion> normal = holderOf(potion.get(PotionType.NORMAL));
    builder.addMix(base, ingredient, normal);
    Potion longer = potion.getOrNull(PotionType.LONG);
    if (longer != null) {
      builder.addMix(normal, Items.REDSTONE, holderOf(longer));
    }
    Potion strong = potion.getOrNull(PotionType.STRONG);
    if (strong != null) {
      builder.addMix(normal, Items.GLOWSTONE_DUST, holderOf(strong));
    }
  }

  /** Wraps a potion in its registry holder, which is what every brewing method wants in 1.21 */
  private static Holder<Potion> holderOf(Potion potion) {
    return BuiltInRegistries.POTION.wrapAsHolder(potion);
  }

  /** Checks if the given entity can be hit considering enderman enderference */
  public static boolean canHitWithProjectile(@Nullable LivingEntity living) {
    return living == null || living.getType() != EntityType.ENDERMAN || living.hasEffect(enderference);
  }

  /** Checks if the given entity needs special casing for enderference */
  public static boolean needsEnderferenceOverride(@Nullable Entity entity) {
    return entity != null && entity.getType() == EntityType.ENDERMAN && entity instanceof LivingEntity living && living.hasEffect(enderference);
  }

  /** Checks if the given entity needs special casing for enderference */
  public static boolean needsEnderferenceOverride(@Nullable LivingEntity living) {
    return living != null && living.getType() == EntityType.ENDERMAN && living.hasEffect(enderference);
  }
}
