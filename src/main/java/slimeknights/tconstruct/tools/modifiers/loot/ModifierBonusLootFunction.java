package slimeknights.tconstruct.tools.modifiers.loot;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Boosts drop rates based on modifier level */
public class ModifierBonusLootFunction extends LootItemConditionalFunction {
  /**
   * Codec for this function.
   * @apiNote  1.21 replaced the {@code LootItemConditionalFunction.Serializer} pair with a single {@link MapCodec}
   *           composed on top of {@link LootItemConditionalFunction#commonFields}, which reads the shared
   *           {@code conditions} list. The JSON is unchanged; see {@link Formula} for the formula half.
   */
  public static final MapCodec<ModifierBonusLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance).and(instance.group(
    ModifierId.PARSER.codec().fieldOf("modifier").forGetter(function -> function.modifier),
    Formula.CODEC.forGetter(function -> function.formula),
    Codec.BOOL.optionalFieldOf("include_base", true).forGetter(function -> function.includeBase)
  )).apply(instance, ModifierBonusLootFunction::new));
  /**
   * Loot function type, registered by {@code TinkerModifiers}.
   * @apiNote  Owned here rather than fetched from the registry object as {@link LootItemFunctionType} is generic in
   *           1.21, and {@link #getType()} must return the type parameterized with this class.
   */
  public static final LootItemFunctionType<ModifierBonusLootFunction> TYPE = new LootItemFunctionType<>(CODEC);

  /** Modifier ID to use for multiplier bonus */
  private final ModifierId modifier;
  /** Formula to apply */
  private final Formula formula;
  /** If true, considers level 1 as bonus, if false considers level 1 as no bonus */
  private final boolean includeBase;

  protected ModifierBonusLootFunction(List<LootItemCondition> conditions, ModifierId modifier, Formula formula, boolean includeBase) {
    super(conditions);
    this.modifier = modifier;
    this.formula = formula;
    this.includeBase = includeBase;
  }

  /** Creates a generic builder */
  public static Builder<?> builder(ModifierId modifier, Formula formula, boolean includeBase) {
    return simpleBuilder(conditions -> new ModifierBonusLootFunction(conditions, modifier, formula, includeBase));
  }

  /** Creates a builder for the binomial with bonus formula */
  public static Builder<?> binomialWithBonusCount(ModifierId modifier, float probability, int extra, boolean includeBase) {
    return builder(modifier, new Formula.BinomialWithBonusCount(extra, probability), includeBase);
  }

  /** Creates a builder for the ore drops formula */
  public static Builder<?> oreDrops(ModifierId modifier, boolean includeBase) {
    return builder(modifier, new Formula.OreDrops(), includeBase);
  }

  /** Creates a builder for the uniform bonus count */
  public static Builder<?> uniformBonusCount(ModifierId modifier, int bonusMultiplier, boolean includeBase) {
    return builder(modifier, new Formula.UniformBonusCount(bonusMultiplier), includeBase);
  }

  @Override
  public LootItemFunctionType<ModifierBonusLootFunction> getType() {
    return TYPE;
  }

  @Override
  public Set<LootContextParam<?>> getReferencedContextParams() {
    return ImmutableSet.of(LootContextParams.TOOL);
  }

  @Override
  protected ItemStack run(ItemStack stack, LootContext context) {
    int level = ModifierUtil.getModifierLevel(context.getParam(LootContextParams.TOOL), modifier);
    if (!includeBase) {
      level--;
    }
    if (level > 0) {
      stack.setCount(formula.calculateNewCount(context.getRandom(), stack.getCount(), level));
    }
    return stack;
  }

  /**
   * Formula boosting a stack count based on a level, shared with {@link ChrysophiliteBonusFunction}.
   * @apiNote  Copy of {@link ApplyBonusCount}'s {@code Formula}. Before 1.21 both bonus functions reused vanilla's
   *           interface and its {@code FORMULAS} registry directly, feeding it a modifier level in place of an
   *           enchantment level. 1.21 made the interface, all three implementations and the registry package
   *           private, and replaced the Gson {@code FormulaDeserializer} with a codec on a type record, so none of
   *           it can be named from outside {@code net.minecraft.world.level.storage.loot.functions} anymore. It is
   *           nested here for the same reason vanilla nests its own, and read by the other function the way that
   *           one used to read vanilla's.
   *           <p>
   *           The three formulas keep vanilla's IDs, parameter names and arithmetic, and the dispatch is vanilla's
   *           own {@link ExtraCodecs#dispatchOptionalValue}, the same one {@link ApplyBonusCount} uses. So the JSON
   *           is unchanged from 1.20 in both directions, including omitting {@code parameters} when the formula has
   *           none.
   */
  public interface Formula {
    /** Known formula types by ID, the replacement for {@code ApplyBonusCount.FORMULAS} */
    Map<ResourceLocation,Type> FORMULAS = Stream.of(BinomialWithBonusCount.TYPE, OreDrops.TYPE, UniformBonusCount.TYPE)
                                                .collect(Collectors.toMap(Type::id, Function.identity()));

    /** Codec for a formula type, looking it up by ID */
    Codec<Type> TYPE_CODEC = ResourceLocation.CODEC.comapFlatMap(
      id -> {
        Type type = FORMULAS.get(id);
        return type != null ? DataResult.success(type) : DataResult.error(() -> "No formula type with id: '" + id + "'");
      }, Type::id);

    /** Codec reading the formula ID from {@code formula} and its arguments from {@code parameters} */
    MapCodec<Formula> CODEC = ExtraCodecs.dispatchOptionalValue("formula", "parameters", TYPE_CODEC, Formula::getType, Type::codec);

    /**
     * Calculates the new count for the stack
     * @param random         Random instance
     * @param originalCount  Original stack size
     * @param level          Level of the bonus source, a modifier level rather than vanilla's enchantment level
     * @return  New stack size
     */
    int calculateNewCount(RandomSource random, int originalCount, int level);

    /** Gets the type of this formula, used for serializing */
    Type getType();

    /** Type of a formula, pairing its ID with the codec for its parameters */
    record Type(ResourceLocation id, Codec<? extends Formula> codec) {}

    /** Applies a bonus based on a binomial distribution with {@code n = level + extraRounds} and {@code p = probability} */
    record BinomialWithBonusCount(int extraRounds, float probability) implements Formula {
      private static final Codec<BinomialWithBonusCount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("extra").forGetter(BinomialWithBonusCount::extraRounds),
        Codec.FLOAT.fieldOf("probability").forGetter(BinomialWithBonusCount::probability)
      ).apply(instance, BinomialWithBonusCount::new));
      public static final Type TYPE = new Type(ResourceLocation.withDefaultNamespace("binomial_with_bonus_count"), CODEC);

      @Override
      public int calculateNewCount(RandomSource random, int originalCount, int level) {
        for (int i = 0; i < level + this.extraRounds; i++) {
          if (random.nextFloat() < this.probability) {
            originalCount++;
          }
        }
        return originalCount;
      }

      @Override
      public Type getType() {
        return TYPE;
      }
    }

    /** Applies a bonus count with the special formula used for fortune ore drops */
    record OreDrops() implements Formula {
      private static final Codec<OreDrops> CODEC = Codec.unit(OreDrops::new);
      public static final Type TYPE = new Type(ResourceLocation.withDefaultNamespace("ore_drops"), CODEC);

      @Override
      public int calculateNewCount(RandomSource random, int originalCount, int level) {
        if (level > 0) {
          int i = random.nextInt(level + 2) - 1;
          if (i < 0) {
            i = 0;
          }
          return originalCount * (i + 1);
        }
        return originalCount;
      }

      @Override
      public Type getType() {
        return TYPE;
      }
    }

    /** Adds a bonus count based on the level scaled by a constant multiplier */
    record UniformBonusCount(int bonusMultiplier) implements Formula {
      private static final Codec<UniformBonusCount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("bonusMultiplier").forGetter(UniformBonusCount::bonusMultiplier)
      ).apply(instance, UniformBonusCount::new));
      public static final Type TYPE = new Type(ResourceLocation.withDefaultNamespace("uniform_bonus_count"), CODEC);

      @Override
      public int calculateNewCount(RandomSource random, int originalCount, int level) {
        return originalCount + random.nextInt(this.bonusMultiplier * level + 1);
      }

      @Override
      public Type getType() {
        return TYPE;
      }
    }
  }
}
