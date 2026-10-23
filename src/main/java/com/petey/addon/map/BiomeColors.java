package com.petey.addon.map;

import java.util.Map;

public final class BiomeColors {
   public static final int UNKNOWN = -8355712;
   private static final Map<String, Integer> COLORS = Map.ofEntries(
      Map.entry("ocean", -16777104),
      Map.entry("deep_ocean", -16777168),
      Map.entry("warm_ocean", -16777044),
      Map.entry("lukewarm_ocean", -16777072),
      Map.entry("deep_lukewarm_ocean", -16777152),
      Map.entry("cold_ocean", -14671760),
      Map.entry("deep_cold_ocean", -14671816),
      Map.entry("frozen_ocean", -9408298),
      Map.entry("deep_frozen_ocean", -12566384),
      Map.entry("river", -16776961),
      Map.entry("frozen_river", -6250241),
      Map.entry("beach", -336299),
      Map.entry("snowy_beach", -331584),
      Map.entry("stony_shore", -6118780),
      Map.entry("plains", -7490720),
      Map.entry("sunflower_plains", -4859000),
      Map.entry("snowy_plains", -1),
      Map.entry("ice_spikes", -4924196),
      Map.entry("desert", -355304),
      Map.entry("swamp", -16254542),
      Map.entry("mangrove_swamp", -13841266),
      Map.entry("forest", -16423391),
      Map.entry("flower_forest", -13791671),
      Map.entry("birch_forest", -13601724),
      Map.entry("old_growth_birch_forest", -10970004),
      Map.entry("dark_forest", -12562150),
      Map.entry("pale_garden", -5853789),
      Map.entry("taiga", -16030119),
      Map.entry("snowy_taiga", -13544118),
      Map.entry("old_growth_pine_taiga", -10918319),
      Map.entry("old_growth_spruce_taiga", -8286599),
      Map.entry("savanna", -4345249),
      Map.entry("savanna_plateau", -5792412),
      Map.entry("windswept_savanna", -1713529),
      Map.entry("jungle", -11306231),
      Map.entry("sparse_jungle", -10319081),
      Map.entry("bamboo_jungle", -9007596),
      Map.entry("badlands", -2538219),
      Map.entry("eroded_badlands", -37571),
      Map.entry("wooded_badlands", -5204123),
      Map.entry("meadow", -10443707),
      Map.entry("cherry_grove", -874300),
      Map.entry("grove", -12094868),
      Map.entry("snowy_slopes", -3881788),
      Map.entry("frozen_peaks", -6250296),
      Map.entry("jagged_peaks", -2302776),
      Map.entry("stony_peaks", -8679564),
      Map.entry("windswept_hills", -10461088),
      Map.entry("windswept_gravelly_hills", -8873864),
      Map.entry("windswept_forest", -11505584),
      Map.entry("mushroom_fields", -65281),
      Map.entry("dripstone_caves", -8692662),
      Map.entry("lush_caves", -8674511),
      Map.entry("deep_dark", -16113877)
   );

   private BiomeColors() {
   }

   public static int of(String path) {
      return COLORS.getOrDefault(path, -8355712);
   }
}
