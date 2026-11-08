package dev.hafnium.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hafnium.client.core.ClientThread;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.event.ClientTickEvent;
import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.event.WorldEvent;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.ClickGuiScreen;
import dev.hafnium.client.modules.BuiltinModules;
import dev.hafnium.client.config.ServerProfileStore;
import dev.hafnium.client.core.permission.PermissionNotices;
import dev.hafnium.client.modules.automation.OperatingModeModule;
import dev.hafnium.client.modules.client.CapabilitiesModule;
import dev.hafnium.client.modules.client.ServerProfileModule;
import dev.hafnium.client.modules.world.ObservationModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import dev.hafnium.client.net.EnvironmentTracker;
import dev.hafnium.client.world.WorldObserver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

/**
 * Mod entrypoint and Minecraft adapter: builds the {@link HafniumCore}, registers modules, and bridges Fabric
 * callbacks into the core (ticks, world join/leave, chunks, block updates, HUD, keys).
 */
public final class HafniumClient implements ClientModInitializer {
    public static final String MOD_ID = "hafnium";

    private static KeyMapping openKey;
    private static KeyMapping emergencyStopKey;
    private static WorldObserver observer;
    private static boolean inWorld;

    public static KeyMapping openKey() {
        return openKey;
    }

    public static WorldObserver observer() {
        return observer;
    }

    /** From ClientLevelMixin. */
    public static void onServerBlockUpdate(ClientLevel level, BlockPos pos, BlockState state) {
        if (observer != null) {
            HafniumCore.get().navigation().invalidate();
            observer.onBlockChanged(level, pos, state);
        }
    }

    /** Shows a permission notice in the local chat window (client-side only; nothing is sent to the server). */
    private static void showNotice(PermissionNotices.Notice notice) {
        Minecraft mc = Minecraft.getInstance();
        Runnable show = () -> {
            if (mc.gui == null) {
                return;
            }
            ChatFormatting color = switch (notice.level()) {
                case INFO -> ChatFormatting.GRAY;
                case WARNING -> ChatFormatting.GOLD;
                case ERROR -> ChatFormatting.RED;
            };
            mc.gui.getChat().addMessage(Component.literal("[Hafnium] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(notice.text()).withStyle(color)));
        };
        if (mc.isSameThread()) {
            show.run();
        } else {
            mc.execute(show);
        }
    }

    /** Idempotent: clears world observations, drops permissions and cancels world tasks. */
    private static void handleDisconnect(HafniumCore core, EnvironmentTracker environment) {
        if (!inWorld) {
            return;
        }
        inWorld = false;
        core.navigation().invalidate();
        observer.onDisconnect();
        environment.onDisconnect();
        core.events().post(WorldEvent.LEAVE);
    }

    @Override
    public void onInitializeClient() {
        HafniumCore core = new HafniumCore(new ClientThread() {
            @Override
            public boolean isCurrent() {
                return Minecraft.getInstance().isSameThread();
            }

            @Override
            public void execute(Runnable task) {
                Minecraft.getInstance().execute(task);
            }
        }, dev.hafnium.client.core.world.WorldModelLimits.DEFAULT);
        HafniumCore.install(core);
        EventBus bus = core.events();
        BuiltinModules.registerAll(core.modules());
        new dev.hafnium.client.config.ModuleSettingsStore(core, FabricLoader.getInstance().getConfigDir().resolve("hafnium/module-settings.json")).attach();
        dev.hafnium.client.hud.HudLayout.load(FabricLoader.getInstance().getConfigDir().resolve("hafnium/hud-layout.json"));
        dev.hafnium.client.render.DebrisOverlay.register(core);
        dev.hafnium.client.anubis.esp.ChunkScanner.init(core.events());

        ObservationModule observation = core.modules().get(ObservationModule.class);
        core.world().setLimits(observation.limits());
        observation.onChange(() -> core.world().setLimits(observation.limits()));

        observer = new WorldObserver(core.world());
        new ServerProfileStore(FabricLoader.getInstance().getConfigDir().resolve("hafnium").resolve("server-profiles.json"))
                .attach(core.profiles());
        core.modules().get(ServerProfileModule.class).bind(core.events(), core.profiles(), core.permissions());
        core.modules().get(OperatingModeModule.class).bind(core.events(), core.control());
        // The capability indicator is on by default so the user always sees what Hafnium may do here.
        core.modules().setEnabled(core.modules().get(CapabilitiesModule.class), true);
        core.notices().setSink(HafniumClient::showNotice);


        EnvironmentTracker environment = new EnvironmentTracker(core.permissions(), core.profiles());
        environment.register();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.hafnium.clickgui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        emergencyStopKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.hafnium.emergency_stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, category));

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            dev.hafnium.client.anubis.util.PerformanceBudget.beginTick(); // refill the per-tick scan budget
            bus.post(ClientTickEvent.START);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (emergencyStopKey.consumeClick()) {
                core.control().emergencyStop("Emergency stop key");
            }
            while (openKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new ClickGuiScreen(null));
                }
            }
            if (mc.getConnection() == null && inWorld) {
                // Safety net: a disconnect whose callback did not reach us (see handleDisconnect).
                handleDisconnect(core, environment);
            }
            environment.tick(mc);
            observer.tick(mc);
            core.scheduler().tick();
            bus.post(ClientTickEvent.END);
            dev.hafnium.client.config.DeferredJsonFile.tickAll(core.scheduler());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            inWorld = true;
            observer.onJoin();
            environment.onJoin(mc);
            bus.post(WorldEvent.JOIN);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            // Run directly on the client thread: Minecraft drops its pending task queue while disconnecting, so work
            // queued with mc.execute() here can be lost.
            if (mc.isSameThread()) {
                handleDisconnect(core, environment);
            } else {
                mc.execute(() -> handleDisconnect(core, environment));
            }
        });
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> { core.navigation().invalidate(); observer.onChunkLoad(level, chunk); dev.hafnium.client.event.ChunkLoadEvent.dispatch(bus, level, chunk); });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> { core.navigation().invalidate(); observer.onChunkUnload(level, chunk); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> { core.shutdown(); dev.hafnium.client.config.DeferredJsonFile.flushAll(); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (graphics, delta) -> {
            if (bus.hasSubscribers(HudRenderEvent.class)) {
                bus.post(HudRenderEvent.INSTANCE.set(graphics, delta));
            }
        });
    }
}
