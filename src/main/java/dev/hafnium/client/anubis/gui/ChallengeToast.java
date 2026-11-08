/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

public class ChallengeToast implements Toast {
   public static final String RAINBOW = "hafnium:rainbow";
   private static final long RAINBOW_MILLIS = 2500L;
   private static final int BACKGROUND = -266921698;
   private static final int TITLE_COLOR = -986891;
   private static final int MESSAGE_COLOR = -5656902;
   private static final int COUNT_COLOR = -6643540;
   private static final int TIMER_TRACK = 654311423;
   private static final int TIMER_FILL = -1711276033;
   private static final int TEXT_X = 30;
   private static final int TEXT_PAD = 7;
   private static final int RIGHT_GAP = 3;
   private static final int MAX_WIDTH = 240;
   private static final int LINE_HEIGHT = 10;
   private static final int MAX_LINES = 5;
   private static final long DISPLAY_MILLIS = 5000L;
   private final Object token;
   private final ItemStack icon;
   private final boolean sound;
   private Component title;
   private Component message;
   private int width;
   private int height = 32;
   private List<FormattedCharSequence> lines = List.of();
   private int count = 1;
   private boolean changed;
   private boolean hiding;
   private long changedAt;
   private Visibility wanted = Visibility.HIDE;

   public ChallengeToast(ItemStack icon, Component title, Component message, boolean sound) {
      this(NO_TOKEN, icon, title, message, sound);
   }

   private ChallengeToast(Object token, ItemStack icon, Component title, Component message, boolean sound) {
      this.token = token;
      this.icon = icon.copy();
      this.title = title;
      this.message = message;
      this.sound = sound;
      this.fit();
   }

   public static void addOrUpdate(ItemStack icon, String title, Component message, boolean sound) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isSameThread()) {
         mc.execute(() -> addOrUpdate(icon, title, message, sound));
      } else {
         ToastManager toasts = mc.getToastManager();
         ChallengeToast toast = toasts.getToast(ChallengeToast.class, title);
         if (toast == null) {
            toasts.addToast(new ChallengeToast(title, icon, Component.literal(title), message, sound));
         } else {
            toast.count++;
            toast.message = message;
            toast.changed = true;
            toast.fit();
         }
      }
   }

   private String countLabel() {
      return this.count > 1 ? " x" + this.count : "";
   }

   private void fit() {
      Font font = Minecraft.getInstance().font;
      int text = Math.max(font.width(this.title) + font.width(this.countLabel()), font.width(this.message));
      this.width = Mth.clamp(30 + text + 7, 160, 240);
      List<FormattedCharSequence> split = this.message.getString().isEmpty() ? List.of() : font.split(this.message, this.width - 30 - 7);
      this.lines = split.size() > 5 ? List.copyOf(split.subList(0, 5)) : split;
      this.height = Math.max(32, 18 + this.lines.size() * 10 + 4);
   }

   @Override
   public Object getToken() {
      return this.token;
   }

   @Override
   public SoundEvent getSoundEvent() {
      return this.sound ? SoundEvents.NOTE_BLOCK_BELL.value() : null;
   }

   @Override
   public Visibility getWantedVisibility() {
      return this.wanted;
   }

   @Override
   public int width() {
      return this.width;
   }

   @Override
   public int height() {
      return this.height;
   }

   @Override
   public float yPos(int firstSlotIndex) {
      // Anubis moved toasts below its ImGui info bar; Hafnium has no info bar, so the vanilla slot is used.
      return firstSlotIndex * 32.0F;
   }

   protected long displayMillis() {
      return 5000L;
   }

   protected final int cardRight() {
      return this.width - 3;
   }

   protected final int cardBottom() {
      return this.height - 1;
   }

   @Override
   public void update(ToastManager toasts, long visibleFor) {
      if (!this.hiding) {
         if (this.changed) {
            this.changedAt = visibleFor;
            this.changed = false;
         }

         this.hiding = visibleFor - this.changedAt >= this.displayMillis() * toasts.getNotificationDisplayTimeMultiplier();
         this.wanted = this.hiding ? Visibility.HIDE : Visibility.SHOW;
      }
   }

   @Override
   public void onFinishedRendering() {
      if (this.changed) {
         this.hiding = false;
         Minecraft.getInstance().getToastManager().addToast(this);
      }
   }

   @Override
   public void render(GuiGraphics graphics, Font font, long visibleFor) {
      int right = this.width - 3;
      int bottom = this.height - 1;
      rounded(graphics, 0, 1, right, bottom, -266921698);
      this.drawTimer(graphics, visibleFor, right, bottom);
      boolean single = this.lines.isEmpty();
      this.drawTitle(graphics, font, single ? 12 : 7);

      for (int i = 0; i < this.lines.size(); i++) {
         graphics.drawString(font, rainbowed(this.lines.get(i)), 30, 18 + i * 10, -5656902, true);
      }

      this.renderIcon(graphics, this.icon);
   }

   protected void renderIcon(GuiGraphics graphics, ItemStack icon) {
      graphics.renderFakeItem(icon, 8, 8);
   }

   private void drawTimer(GuiGraphics graphics, long visibleFor, int right, int bottom) {
      double duration = this.displayMillis() * Minecraft.getInstance().getToastManager().getNotificationDisplayTimeMultiplier();
      float left = 1.0F - (float)Mth.clamp((visibleFor - this.changedAt) / duration, 0.0, 1.0);
      int x0 = 4;
      int x1 = right - 4;
      int y = bottom - 2;
      graphics.fill(x0, y, x1, y + 1, 654311423);
      graphics.fill(x0, y, x0 + Math.round((x1 - x0) * left), y + 1, -1711276033);
   }

   private static void rounded(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
      graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
      graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
      graphics.fill(x0, y0 + 2, x1, y1 - 2, color);
      graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
      graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
   }

   private void drawTitle(GuiGraphics graphics, Font font, int y) {
      String suffix = this.countLabel();
      int room = this.width - 30 - 7 - font.width(suffix);
      FormattedCharSequence shown = this.title.getVisualOrderText();
      if (font.width(this.title) > room) {
         Component ellipsis = Component.literal("…");
         FormattedText cut = font.substrByWidth(this.title, room - font.width(ellipsis));
         shown = Language.getInstance().getVisualOrder(FormattedText.composite(cut, ellipsis));
      }

      graphics.drawString(font, rainbowed(shown), 30, y, -986891, true);
      if (!suffix.isEmpty()) {
         graphics.drawString(font, suffix, 30 + font.width(shown), y, -6643540, true);
      }
   }

   private static FormattedCharSequence rainbowed(FormattedCharSequence text) {
      float turn = (float)(Util.getMillis() % 2500L) / 2500.0F;
      return sink -> text.accept((index, style, codePoint) -> {
         if (!"hafnium:rainbow".equals(style.getInsertion())) {
            return sink.accept(index, style, codePoint);
         } else {
            float hue = index * 0.045F - turn;
            int rgb = Mth.hsvToRgb(hue - (float)Math.floor(hue), 0.75F, 1.0F);
            return sink.accept(index, style.withInsertion(null).withColor(TextColor.fromRgb(rgb)), codePoint);
         }
      });
   }
}
