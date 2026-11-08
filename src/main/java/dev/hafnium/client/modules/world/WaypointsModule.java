/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.world;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import dev.hafnium.client.anubis.chat.ModuleChat;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.core.event.ClientTickEvent;
import dev.hafnium.client.anubis.gui.WorldLabel;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.setting.ActionSetting;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.anubis.setting.StringListSetting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

public final class WaypointsModule extends Module {
   private static final Pattern SPLIT = Pattern.compile("[\\s,]+");
   private static final Pattern DEATH_NAME = Pattern.compile("Death \\d{2}:\\d{2}");
   private static final DateTimeFormatter DEATH_TIME = DateTimeFormatter.ofPattern("HH:mm");
   private static final double PROJECT_DISTANCE = 64.0;
   private static final double NETHER_SCALE = 8.0;
   private static final float FOCUS_RADIUS = 40.0F;
   private static final float FADE_BLOCKS = 4.0F;
   private static final int PADDING = 3;
   private static final int MARKER = 5;
   private static final int PANEL_RGB = 1052692;
   private static final int TEXT_LABEL = -4934466;
   private final StringListSetting points = this.setting(new StringListSetting("Waypoints").hint("One per line: Name x y z, optionally ending in nether or end. y may be left out."));
   private final KeybindSetting addKey = this.setting(new KeybindSetting("Add Here Key"));
   private final ActionSetting addHere = this.setting(new ActionSetting("Add Here", this::addHere));
   private final BoolSetting deaths = this.setting(new BoolSetting("Death Waypoints", true));
   private final NumberSetting deathMemory = this.setting(new NumberSetting("Death Memory", 3.0F, 1.0F, 10.0F, 1.0F));
   private final BoolSetting otherDimension = this.setting(new BoolSetting("Show In Other Dimension", true));
   private final BoolSetting showDistance = this.setting(new BoolSetting("Show Distance", true));
   private final NumberSetting maxDistance = this.setting(new NumberSetting("Max Distance", 0.0F, 0.0F, 30000.0F, 100.0F));
   private final NumberSetting hideWithin = this.setting(new NumberSetting("Hide Within", 3.0F, 0.0F, 32.0F, 1.0F));
   private final NumberSetting scale = this.setting(new NumberSetting("Scale", 1.0F, 0.5F, 2.0F, 0.1F));
   private final ColorSetting color = this.setting(new ColorSetting("Color", -14006));
   private final ColorSetting deathColor = this.setting(new ColorSetting("Death Color", -47814));
   private List<WaypointsModule.Point> parsed;
   private int drawnLastFrame;
   private boolean wasDead;

   public WaypointsModule() {
      super("Waypoints", Category.WORLD, "Saved places shown on screen with distance. Entries are \"Name x y z\", optionally ending in nether or end.");
      this.points.onChange(() -> this.parsed = null);
      this.deathMemory.visibleWhen(this.deaths::get);
      this.deathColor.visibleWhen(this.deaths::get);
   }

   @Override
   protected void onEnable(ModuleContext ctx) {
      ctx.subscribe(ClientTickEvent.class, e -> { if (e == ClientTickEvent.END) this.onTick(null); });
      ctx.subscribe(HudRenderEvent.class, this::onRender2D);

      this.wasDead = false;
      // Anubis also accepted mouse buttons for this bind; Hafnium keybinds are keyboard keys.
      ctx.subscribe(GameKeyEvent.class, event -> this.pressed(event.key()));
   }

   private void pressed(int key) {
      if (key != KeybindSetting.NONE && key == this.addKey.get() && Minecraft.getInstance().screen == null) {
         this.addHere();
      }
   }

   private void addHere() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         String name = this.freeName();
         this.save(name, player.getBlockX(), player.getBlockY(), player.getBlockZ(), mc.level.dimension());
      } else {
         this.say(ModuleChat.body().warn("Join a world first."));
      }
   }

   private void save(String name, int x, int y, int z, ResourceKey<Level> dimension) {
      String entry = name + " " + x + " " + y + " " + z + suffix(dimension);
      if (!this.points.addValue(entry)) {
         this.say(ModuleChat.body().warn("The waypoint list is full").text(" (64), remove one first."));
      } else {
         this.say(ModuleChat.body().good("Saved ").name(name).text(" at ").coords(x, y, z).text("."));
      }
   }

   private String freeName() {
      List<WaypointsModule.Point> all = this.parsed();
      int n = all.size() + 1;

      while (true) {
         String name = "Waypoint " + n;
         if (all.stream().noneMatch(point -> point.name().equalsIgnoreCase(name))) {
            return name;
         }

         n++;
      }
   }

   private void onTick(Object event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null) {
         boolean dead = player.isDeadOrDying();
         if (dead && !this.wasDead && this.deaths.get()) {
            this.forgetOldDeaths(Math.max(0, this.deathMemory.getInt() - 1));
            this.save("Death " + LocalTime.now().format(DEATH_TIME), player.getBlockX(), player.getBlockY(), player.getBlockZ(), mc.level.dimension());
         }

         this.wasDead = dead;
      } else {
         this.wasDead = false;
      }
   }

   private void forgetOldDeaths(int keep) {
      List<Integer> deathRows = new ArrayList<>();

      for (int i = 0; i < this.points.size(); i++) {
         WaypointsModule.Point point = parse(this.points.entry(i).get());
         if (point != null && point.death()) {
            deathRows.add(i);
         }
      }

      for (int ix = deathRows.size() - keep - 1; ix >= 0; ix--) {
         this.points.remove(deathRows.get(ix));
      }
   }

   private void onRender2D(HudRenderEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null && !mc.options.hideGui) {
         Camera camera = mc.gameRenderer.getMainCamera();
         if (camera != null && camera.isInitialized()) {
            Vec3 eye = camera.position();
            ResourceKey<Level> here = mc.level.dimension();
            double limit = this.maxDistance.get();
            List<WaypointsModule.Shown> visible = new ArrayList<>();

            for (WaypointsModule.Point point : this.parsed()) {
               WaypointsModule.Shown shown = this.place(point, here, eye);
               if (shown != null && (!(limit > 0.0) || !(shown.distance() > limit))) {
                  visible.add(shown);
               }
            }

            visible.sort(Comparator.comparingDouble(WaypointsModule.Shown::distance).reversed());
            this.drawnLastFrame = visible.size();
            GuiGraphics graphics = event.graphics();

            for (WaypointsModule.Shown shown : visible) {
               this.draw(graphics, mc, eye, shown);
            }
         }
      }
   }

   /** Waypoints placed on screen last frame, and the saved entries (for tests). */
   public int drawnLastFrame() {
      return this.drawnLastFrame;
   }

   public List<String> entries() {
      return this.points.values();
   }

   private WaypointsModule.Shown place(WaypointsModule.Point point, ResourceKey<Level> here, Vec3 eye) {
      double x = point.x() + 0.5;
      double z = point.z() + 0.5;
      boolean converted = false;
      if (point.dimension() != here) {
         if (!this.otherDimension.get()) {
            return null;
         }

         if (point.dimension() == Level.OVERWORLD && here == Level.NETHER) {
            x /= 8.0;
            z /= 8.0;
         } else {
            if (point.dimension() != Level.NETHER || here != Level.OVERWORLD) {
               return null;
            }

            x *= 8.0;
            z *= 8.0;
         }

         converted = true;
      }

      double y = point.y() != null && !converted ? point.y().intValue() + 0.5 : eye.y;
      Vec3 at = new Vec3(x, y, z);
      return new WaypointsModule.Shown(point, at, at.distanceTo(eye), converted);
   }

   private void draw(GuiGraphics graphics, Minecraft mc, Vec3 eye, WaypointsModule.Shown shown) {
      float alpha = Math.min(1.0F, Math.max(0.0F, (float)(shown.distance() - this.hideWithin.get()) / 4.0F));
      if (!(alpha <= 0.02F)) {
         Vec3 toward = shown.at().subtract(eye);
         Vec3 aim = shown.distance() > 64.0 ? eye.add(toward.scale(64.0 / shown.distance())) : shown.at();
         float[] screen = WorldLabel.project(mc, aim, graphics.guiWidth(), graphics.guiHeight());
         if (screen != null) {
            WaypointsModule.Point point = shown.point();
            Font font = mc.font;
            int accent = point.death() ? this.deathColor.argb() : this.color.argb();
            String title = point.name();
            String detail = this.showDistance.get() ? distance(shown.distance()) : null;
            if (shown.converted()) {
               detail = (detail == null ? "" : detail + " ") + "(" + dimensionName(point.dimension()) + ")";
            }

            float dx = screen[0] - graphics.guiWidth() * 0.5F;
            float dy = screen[1] - graphics.guiHeight() * 0.5F;
            String coords = dx * dx + dy * dy <= 1600.0F ? point.x() + ", " + (point.y() == null ? "~" : point.y()) + ", " + point.z() : null;
            int titleW = font.width(title) + (detail != null ? 4 + font.width(detail) : 0);
            int width = Math.max(titleW, coords != null ? font.width(coords) : 0) + 6;
            int height = 6 + 9 + (coords != null ? 9 + 1 : 0);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(screen[0], screen[1]);
            float size = this.scale.getFloat();
            pose.scale(size, size);
            int markerColor = fade(accent, alpha);
            graphics.fill(-2, -2, 3, 3, fade(-16777216, alpha * 0.6F));
            graphics.fill(-1, -1, 2, 2, markerColor);
            int left = -width / 2;
            int top = -7 - height;
            graphics.fill(left, top, left + width, top + height, fade(-15724524, alpha * 0.55F));
            graphics.fill(left, top + height - 1, left + width, top + height, markerColor);
            int textX = left + 3;
            int textY = top + 3;
            graphics.drawString(font, title, textX, textY, fade(accent, alpha), true);
            if (detail != null) {
               graphics.drawString(font, detail, textX + font.width(title) + 4, textY, fade(-4934466, alpha), true);
            }

            if (coords != null) {
               graphics.drawString(font, coords, textX, textY + 9 + 1, fade(-1, alpha), true);
            }

            pose.popMatrix();
         }
      }
   }

   private List<WaypointsModule.Point> parsed() {
      if (this.parsed == null) {
         List<WaypointsModule.Point> out = new ArrayList<>();

         for (String entry : this.points.values()) {
            WaypointsModule.Point point = parse(entry);
            if (point != null) {
               out.add(point);
            }
         }

         this.parsed = List.copyOf(out);
      }

      return this.parsed;
   }

   private static WaypointsModule.Point parse(String entry) {
      List<String> tokens = new ArrayList<>(List.of(SPLIT.split(entry.trim())));
      tokens.removeIf(String::isEmpty);
      ResourceKey<Level> dimension = Level.OVERWORLD;
      if (!tokens.isEmpty()) {
         ResourceKey<Level> named = dimension(tokens.get(tokens.size() - 1));
         if (named != null) {
            dimension = named;
            tokens.remove(tokens.size() - 1);
         }
      }

      List<Integer> numbers = new ArrayList<>();

      while (!tokens.isEmpty() && numbers.size() < 3) {
         Integer value = number(tokens.get(tokens.size() - 1));
         if (value == null) {
            break;
         }

         numbers.add(0, value);
         tokens.remove(tokens.size() - 1);
      }

      if (numbers.size() < 2) {
         return null;
      } else {
         String name = String.join(" ", tokens).trim();
         if (name.isEmpty()) {
            name = "Waypoint";
         }

         boolean death = DEATH_NAME.matcher(name).matches();
         return numbers.size() == 3
            ? new WaypointsModule.Point(name, numbers.get(0), numbers.get(1), numbers.get(2), dimension, death)
            : new WaypointsModule.Point(name, numbers.get(0), null, numbers.get(1), dimension, death);
      }
   }

   private static Integer number(String token) {
      try {
         return (int)Math.floor(Double.parseDouble(token));
      } catch (NumberFormatException var2) {
         return null;
      }
   }

   private static ResourceKey<Level> dimension(String token) {
      String var1 = token.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "nether", "the_nether", "minecraft:the_nether" -> Level.NETHER;
         case "end", "the_end", "minecraft:the_end" -> Level.END;
         case "overworld", "minecraft:overworld" -> Level.OVERWORLD;
         default -> null;
      };
   }

   private static String suffix(ResourceKey<Level> dimension) {
      return dimension == Level.NETHER ? " nether" : (dimension == Level.END ? " end" : "");
   }

   private static String dimensionName(ResourceKey<Level> dimension) {
      return dimension == Level.NETHER ? "Nether" : (dimension == Level.END ? "End" : "Overworld");
   }

   private static String distance(double blocks) {
      return blocks >= 10000.0 ? String.format(Locale.ROOT, "%.1fk m", blocks / 1000.0) : Math.round(blocks) + "m";
   }

   private static int fade(int argb, float alpha) {
      int a = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
      return a << 24 | argb & 16777215;
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this.name(), body);
   }

      private record Point(String name, int x, Integer y, int z, ResourceKey<Level> dimension, boolean death) {
   }

      private record Shown(WaypointsModule.Point point, Vec3 at, double distance, boolean converted) {
   }
}
