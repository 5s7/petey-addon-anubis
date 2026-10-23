package com.petey.addon.modules;

import com.petey.addon.PeteyAddon;
import com.petey.addon.hud.DragBox;
import com.petey.addon.map.BiomeColors;
import imgui.ImDrawList;
import imgui.ImFont;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.anubis.client.gui.GuiPalette;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.setting.NumberSetting;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PeteysSeedMap extends ClientModule implements HudOverlayProvider {
   private static final Logger LOG = LoggerFactory.getLogger("Petey Addon");
   private static final long SEED = 6608149111735331168L;
   private static final int CELLS = 64;
   private static final int SAMPLE_Y = 63;
   private static final int MAX_CACHE = 250000;
   private final NumberSetting size = (NumberSetting)this.addSetting("size", new NumberSetting("Size", 160.0F, 80.0F, 400.0F).step(10.0F));
   private final NumberSetting range = (NumberSetting)this.addSetting("range", new NumberSetting("Range", 512.0F, 128.0F, 4096.0F).step(64.0F));
   private final NumberSetting arrowSize = (NumberSetting)this.addSetting("arrow-size", new NumberSetting("Arrow Size", 6.0F, 2.0F, 30.0F).step(1.0F));
   private final DragBox drag = new DragBox(
      (NumberSetting)this.addSetting("pos-x", DragBox.coord("X")), (NumberSetting)this.addSetting("pos-y", DragBox.coord("Y"))
   );
   private static volatile PeteysSeedMap.Gen gen;
   private final Map<Long, Integer> colors = new ConcurrentHashMap<>();
   private final Set<Long> queued = ConcurrentHashMap.newKeySet();
   private final LinkedBlockingQueue<long[]> queue = new LinkedBlockingQueue<>();
   private final List<int[]> rings = new ArrayList<>();
   private volatile int step = -1;
   private volatile int centerX;
   private volatile int centerZ;
   private volatile Thread worker;
   private boolean compared;
   private volatile PeteysSeedMap.Check check;

   public PeteysSeedMap() {
      super(
         "peteys-seed-map",
         "Petey's Seed Map",
         "Small biome map around you, generated from the DonutSMP seed. Drag it while chat is open. Made by Petey.",
         PeteyAddon.CATEGORY
      );
      int r = 33;

      for (int dz = -r; dz <= r; dz++) {
         for (int dx = -r; dx <= r; dx++) {
            this.rings.add(new int[]{dx, dz});
         }
      }

      this.rings.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
   }

   protected void onEnable() {
      this.compared = false;
      Thread t = new Thread(this::work, "Petey's Seed Map");
      t.setDaemon(true);
      this.worker = t;
      t.start();
   }

   protected void onDisable() {
      Thread t = this.worker;
      this.worker = null;
      if (t != null) {
         t.interrupt();
      }

      this.queue.clear();
      this.queued.clear();
   }

   private void work() {
      try {
         if (gen == null) {
            long t0 = System.currentTimeMillis();
            WrapperLookup lookup = BuiltinRegistries.createWrapperLookup();
            ChunkGeneratorSettings settings = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.OVERWORLD).value();
            NoiseConfig state = NoiseConfig.create(settings, lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS), 6608149111735331168L);
            MultiNoiseBiomeSource source = MultiNoiseBiomeSource.create(lookup.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            gen = new PeteysSeedMap.Gen(source, state.getMultiNoiseSampler());
            LOG.info("[Petey's Seed Map] biome generator ready in {} ms", System.currentTimeMillis() - t0);
         }

         while (this.worker == Thread.currentThread()) {
            PeteysSeedMap.Check c0 = this.check;
            if (c0 != null) {
               this.check = null;
               LOG.info(
                  "[Petey's Seed Map] at {} {}: seed says {}, server says {}",
                  new Object[]{c0.pos().getX(), c0.pos().getZ(), name(gen.at(c0.pos().getX(), c0.pos().getZ())), c0.server()}
               );
            }

            long[] task = this.queue.poll(250L, TimeUnit.MILLISECONDS);
            if (task != null) {
               long key = task[0];
               int gx = (int)(key >> 32);
               int gz = (int)key;
               int s = (int)task[1];
               this.queued.remove(key);
               if (s == this.step && Math.abs(gx - this.centerX) <= 33 && Math.abs(gz - this.centerZ) <= 33) {
                  int c = BiomeColors.of(name(gen.at(gx * s + s / 2, gz * s + s / 2)));
                  if (s == this.step) {
                     this.colors.put(key, c);
                  }
               }
            }
         }
      } catch (InterruptedException var9) {
      } catch (Throwable e) {
         LOG.error("[Petey's Seed Map] biome generator failed", e);
      }
   }

   private static String name(RegistryEntry<Biome> h) {
      return h.getKey().map(k -> k.getValue().getPath()).orElse("?");
   }

   private static long key(int gx, int gz) {
      return (long)gx << 32 | gz & 4294967295L;
   }

   public boolean isHudOverlayEnabled() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return this.isEnabled() && mc.player != null && mc.world != null && mc.world.getRegistryKey() == World.OVERWORLD;
   }

   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity p = mc.player;
      if (p != null) {
         float sz = this.size.get();
         float[] at = this.drag.place(dl, sz, sz, screenW, screenH, screenW - sz - 160.0F, 10.0F);
         float x0 = at[0];
         float y0 = at[1];
         dl.addRectFilled(x0, y0, x0 + sz, y0 + sz, abgr(-938470890), 6.0F);
         int r = this.range.getInt();
         int s = Math.max(4, 2 * r / 64 / 4 * 4);
         if (s != this.step) {
            this.step = s;
            this.colors.clear();
            this.queued.clear();
            this.queue.clear();
         }

         if (this.colors.size() > 250000) {
            this.colors.clear();
         }

         double px = p.getX();
         double pz = p.getZ();
         int cgx = Math.floorDiv((int)Math.floor(px), s);
         int cgz = Math.floorDiv((int)Math.floor(pz), s);
         this.centerX = cgx;
         this.centerZ = cgz;

         for (int[] o : this.rings) {
            long k = key(cgx + o[0], cgz + o[1]);
            if (!this.colors.containsKey(k) && this.queued.add(k)) {
               this.queue.add(new long[]{k, s});
            }
         }

         double scale = sz / (2.0 * r);
         double left = px - r;
         double top = pz - r;
         dl.pushClipRect(x0 + 2.0F, y0 + 2.0F, x0 + sz - 2.0F, y0 + sz - 2.0F, true);

         for (int gz = Math.floorDiv((int)Math.floor(top), s); gz * s < pz + r; gz++) {
            for (int gx = Math.floorDiv((int)Math.floor(left), s); gx * s < px + r; gx++) {
               Integer c = this.colors.get(key(gx, gz));
               if (c != null) {
                  float sx = x0 + (float)((gx * s - left) * scale);
                  float sy = y0 + (float)((gz * s - top) * scale);
                  float w = (float)(s * scale) + 0.5F;
                  dl.addRectFilled(sx, sy, sx + w, sy + w, abgr(c), 0.0F);
               }
            }
         }

         dl.popClipRect();
         if (gen == null) {
            ImFont font = ImGuiManager.getInstance().hudLabelFont();
            String t = "Loading seed...";
            dl.addText(font, 14, x0 + (sz - font.calcTextSizeAX(14.0F, Float.MAX_VALUE, 0.0F, t)) / 2.0F, y0 + sz / 2.0F - 7.0F, abgr(-1), t);
         } else if (!this.compared) {
            this.compared = true;
            this.compareOnce(mc, p);
         }

         float ax = x0 + sz / 2.0F;
         float ay = y0 + sz / 2.0F;
         double yaw = Math.toRadians(p.getYaw());
         double dx = -Math.sin(yaw);
         double dz = Math.cos(yaw);
         float len = this.arrowSize.get();
         float tipX = ax + (float)(dx * len);
         float tipY = ay + (float)(dz * len);
         float lx = ax + (float)(rot(dx, dz, 140.0, true) * len * 0.75);
         float ly = ay + (float)(rot(dx, dz, 140.0, false) * len * 0.75);
         float rx = ax + (float)(rot(dx, dz, -140.0, true) * len * 0.75);
         float ry = ay + (float)(rot(dx, dz, -140.0, false) * len * 0.75);
         dl.addTriangleFilled(tipX, tipY, lx, ly, rx, ry, abgr(-50384));
         dl.addTriangle(tipX, tipY, lx, ly, rx, ry, abgr(-16777216), 1.0F);
      }
   }

   private void compareOnce(MinecraftClient mc, ClientPlayerEntity p) {
      BlockPos pos = BlockPos.ofFloored(p.getX(), 63.0, p.getZ());
      this.check = new PeteysSeedMap.Check(pos, name(mc.world.getBiome(pos)));
   }

   private static double rot(double dx, double dz, double deg, boolean xAxis) {
      double a = Math.toRadians(deg);
      return xAxis ? dx * Math.cos(a) - dz * Math.sin(a) : dx * Math.sin(a) + dz * Math.cos(a);
   }

   private static int abgr(int argb) {
      return GuiPalette.abgr(argb);
   }

   private record Check(BlockPos pos, String server) {
   }

   private record Gen(MultiNoiseBiomeSource source, MultiNoiseSampler sampler) {
      RegistryEntry<Biome> at(int x, int z) {
         return this.source.getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(63), BiomeCoords.fromBlock(z), this.sampler);
      }
   }
}
