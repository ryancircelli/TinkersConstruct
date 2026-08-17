package slimeknights.tconstruct.common.data;

import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRequirements.Strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Requirements strategy splitting the criteria into groups of the given sizes.
 * @apiNote  1.21 replaced {@code RequirementsStrategy} with {@link Strategy}, which builds an
 *           {@link AdvancementRequirements} record from nested lists rather than a raw {@code String[][]}.
 */
public class CountRequirementsStrategy implements Strategy {
  private final int[] sizes;
  public CountRequirementsStrategy(int... sizes) {
    this.sizes = sizes;
  }

  @Override
  public AdvancementRequirements create(Collection<String> strings) {
    List<List<String>> requirements = new ArrayList<>(sizes.length);
    List<String> list = new ArrayList<>(strings);
    int nextIndex = 0;
    for (int size : sizes) {
      List<String> group = new ArrayList<>(size);
      for (int j = 0; j < size; j++) {
        group.add(list.get(nextIndex));
        nextIndex++;
      }
      requirements.add(List.copyOf(group));
    }
    return new AdvancementRequirements(List.copyOf(requirements));
  }
}
