package com.petey.addon.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.petey.addon.PeteyAddon;
import com.petey.addon.hud.DragBox;
import com.petey.addon.map.DonutRegions;
import imgui.ImDrawList;
import imgui.ImFont;
import net.anubis.client.gui.GuiPalette;
import net.anubis.client.gui.HudLayoutStore;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.setting.NumberSetting;
import net.minecraft.world.World;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class PeteysRegionMap extends ClientModule implements HudOverlayProvider {
   private static final float MARGIN = 10.0F;
   private static final float PAD = 8.0F;
   private static final float CLEAR = 6.0F;
   private float[] coordsBox;
   private long coordsReadAt;
   private final NumberSetting size = (NumberSetting)this.addSetting("size", new NumberSetting("Size", 200.0F, 80.0F, 480.0F).step(10.0F));
   private final NumberSetting arrowSize = (NumberSetting)this.addSetting("arrow-size", new NumberSetting("Arrow Size", 6.0F, 2.0F, 30.0F).step(1.0F));
   private final DragBox drag = new DragBox(
      (NumberSetting)this.addSetting("pos-x", DragBox.coord("X")), (NumberSetting)this.addSetting("pos-y", DragBox.coord("Y"))
   );

   public PeteysRegionMap() {
      super(
         "peteys-region-map",
         "Petey's Region Map",
         "Shows only the DonutSMP region you're in, highlights your square and points an arrow where you're looking. Made by Petey.",
         PeteyAddon.CATEGORY
      );
   }

   public boolean isHudOverlayEnabled() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return this.isEnabled()
         && mc.player != null
         && mc.world != null
         && (mc.world.getRegistryKey() == World.OVERWORLD || mc.world.getRegistryKey() == World.NETHER);
   }

   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity p = mc.player;
      if (p != null && mc.world != null) {
         double scale = mc.world.getRegistryKey() == World.NETHER ? 8.0 : 1.0;
         double x = p.getX() * scale;
         double z = p.getZ() * scale;
         DonutRegions.Spot spot = DonutRegions.at(x, z);
         if (spot != null) {
            int r = spot.region();
            int[][] cells = DonutRegions.CELLS[r];
            int[] b = DonutRegions.BOUNDS[r];
            ImFont font = ImGuiManager.getInstance().hudLabelFont();
            float k = this.size.get() / Math.max(b[2] - b[0], b[3] - b[1]);
            float mapW = (b[2] - b[0]) * k;
            float mapH = (b[3] - b[1]) * k;
            int headPx = 16;
            float headH = headPx + 6.0F;
            float panelW = Math.max(mapW, font.calcTextSizeAX(headPx, Float.MAX_VALUE, 0.0F, header(r, spot.cell()))) + 16.0F;
            float panelH = mapH + headH + 16.0F;
            float[] cb = this.coordsBox();
            float[] at = this.drag.place(dl, panelW, panelH, screenW, screenH, 10.0F, cb != null ? cb[1] + cb[3] + 6.0F : 10.0F);
            float px = at[0];
            float py = at[1];
            dl.addRectFilled(px, py, px + panelW, py + panelH, abgr(-938470890), 8.0F);
            text(dl, font, headPx, px + 8.0F, py + 8.0F - 2.0F, -1, header(r, spot.cell()));
            float mx = px + (panelW - mapW) / 2.0F;
            float my = py + 8.0F + headH;
            float gap = Math.max(1.0F, k * 0.06F);
            int color = DonutRegions.COLORS[r];

            for (int i = 0; i < cells.length; i++) {
               int[] c = cells[i];
               float x1 = mx + (c[0] - b[0]) * k + gap;
               float y1 = my + (c[1] - b[1]) * k + gap;
               float x2 = mx + (c[2] - b[0]) * k - gap;
               float y2 = my + (c[3] - b[1]) * k - gap;
               boolean here = i == spot.cell();
               dl.addRectFilled(x1, y1, x2, y2, abgr(here ? mix(color, -1, 0.35F) : color & 16777215 | -805306368), 2.0F);
               if (here) {
                  dl.addRect(x1, y1, x2, y2, abgr(-1), 2.0F, 0, 2.0F);
               }

               int numPx = (int)Math.min(16.0F, Math.min(x2 - x1, y2 - y1) * 0.5F);
               if (numPx >= 7) {
                  String n = String.valueOf(i + 1);
                  float tw = font.calcTextSizeAX(numPx, Float.MAX_VALUE, 0.0F, n);
                  text(dl, font, numPx, (x1 + x2 - tw) / 2.0F, (y1 + y2 - numPx) / 2.0F, -1, n);
               }
            }

            float ax = mx + (float)((x + 225000.0) / 12500.0 - b[0]) * k;
            float ay = my + (float)((z + 225000.0) / 12500.0 - b[1]) * k;
            double yaw = Math.toRadians(p.getYaw());
            double dx = -Math.sin(yaw);
            double dz = Math.cos(yaw);
            float len = this.arrowSize.get();
            float tipX = ax + (float)(dx * len);
            float tipY = ay + (float)(dz * len);
            float lx = ax + (float)(rotX(dx, dz, 140.0) * len * 0.75);
            float ly = ay + (float)(rotZ(dx, dz, 140.0) * len * 0.75);
            float rx = ax + (float)(rotX(dx, dz, -140.0) * len * 0.75);
            float ry = ay + (float)(rotZ(dx, dz, -140.0) * len * 0.75);
            dl.addTriangleFilled(tipX, tipY, lx, ly, rx, ry, abgr(-50384));
            dl.addTriangle(tipX, tipY, lx, ly, rx, ry, abgr(-16777216), 1.0F);
         }
      }
   }

   private float[] coordsBox() {
      long now = System.currentTimeMillis();
      if (now - this.coordsReadAt < 1000L) {
         return this.coordsBox;
      }

      this.coordsReadAt = now;
      this.coordsBox = null;

      try {
         for (JsonElement e : HudLayoutStore.getInstance().profileSnapshot().getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if ("coordinates".equals(o.get("id").getAsString()) && o.get("visible").getAsBoolean()) {
               this.coordsBox = new float[]{o.get("x").getAsFloat(), o.get("y").getAsFloat(), o.get("width").getAsFloat(), o.get("height").getAsFloat()};
            }
         }
      } catch (RuntimeException var6) {
      }

      return this.coordsBox;
   }

   private static String header(int region, int cell) {
      return DonutRegions.NAMES[region] + "  " + (cell + 1);
   }

   private static void text(ImDrawList dl, ImFont font, int px, float x, float y, int argb, String s) {
      dl.addText(font, px, x + 1.0F, y + 1.0F, abgr(-1610612736), s);
      dl.addText(font, px, x, y, abgr(argb), s);
   }

   private static double rotX(double dx, double dz, double deg) {
      double a = Math.toRadians(deg);
      return dx * Math.cos(a) - dz * Math.sin(a);
   }

   private static double rotZ(double dx, double dz, double deg) {
      double a = Math.toRadians(deg);
      return dx * Math.sin(a) + dz * Math.cos(a);
   }

   private static int mix(int argb, int with, float t) {
      int r = (int)((argb >> 16 & 0xFF) * (1.0F - t) + (with >> 16 & 0xFF) * t);
      int g = (int)((argb >> 8 & 0xFF) * (1.0F - t) + (with >> 8 & 0xFF) * t);
      int b = (int)((argb & 0xFF) * (1.0F - t) + (with & 0xFF) * t);
      return argb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int abgr(int argb) {
      return GuiPalette.abgr(argb);
   }
}
