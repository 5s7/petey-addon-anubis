package com.petey.addon.hud;

import imgui.ImDrawList;
import net.anubis.client.gui.GuiPalette;
import net.anubis.client.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

public final class DragBox {
   private final NumberSetting x;
   private final NumberSetting y;
   private boolean dragging;
   private boolean wasDown;
   private float grabX;
   private float grabY;

   public DragBox(NumberSetting x, NumberSetting y) {
      this.x = x;
      this.y = y;
      x.visibleWhen(() -> false);
      y.visibleWhen(() -> false);
   }

   public static NumberSetting coord(String name) {
      return new NumberSetting(name, -1.0F, -1.0F, 10000.0F).step(1.0F);
   }

   public float[] place(ImDrawList dl, float w, float h, float screenW, float screenH, float defX, float defY) {
      MinecraftClient mc = MinecraftClient.getInstance();
      float px = this.x.get() < 0.0F ? defX : this.x.get();
      float py = this.y.get() < 0.0F ? defY : this.y.get();
      boolean chat = mc.currentScreen instanceof ChatScreen;
      boolean down = chat && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 0) == 1;
      float mx = (float)(mc.mouse.getX() * screenW / Math.max(1, mc.getWindow().getWidth()));
      float my = (float)(mc.mouse.getY() * screenH / Math.max(1, mc.getWindow().getHeight()));
      if (down && !this.wasDown && mx >= px && mx <= px + w && my >= py && my <= py + h) {
         this.dragging = true;
         this.grabX = mx - px;
         this.grabY = my - py;
      }

      if (this.dragging) {
         px = mx - this.grabX;
         py = my - this.grabY;
      }

      px = Math.max(0.0F, Math.min(px, screenW - w));
      py = Math.max(0.0F, Math.min(py, screenH - h));
      if (this.dragging && !down) {
         this.dragging = false;
         this.x.set(Math.round(px));
         this.y.set(Math.round(py));
      }

      this.wasDown = down;
      if (chat) {
         dl.addRect(px - 1.0F, py - 1.0F, px + w + 1.0F, py + h + 1.0F, GuiPalette.abgr(this.dragging ? -1056964609 : 1627389951), 8.0F, 0, 1.0F);
      }

      return new float[]{px, py};
   }
}
