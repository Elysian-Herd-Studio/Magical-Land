package top.elysianherd.magicaland.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import top.elysianherd.magicaland.client.api.AppearanceOverrideState;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.gui.ConfigScreen;
import top.elysianherd.magicaland.client.emote.EmoteClient;
import top.elysianherd.magicaland.client.emote.EmoteWheelScreen;
import top.elysianherd.magicaland.client.config.ModelManager;
import top.elysianherd.magicaland.client.network.ClientNetworkHandler;
import top.elysianherd.magicaland.client.render.*;
import top.elysianherd.magicaland.client.animation.PonyExpressions;
import top.elysianherd.magicaland.client.animation.PonyFlightVisuals;
import top.elysianherd.magicaland.client.sound.MagicHeldItemSounds;
import top.elysianherd.magicaland.client.sound.PonyHoofSounds;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Client implements ClientModInitializer {
    private static KeyBinding configKeyBinding;

    @Override
    public void onInitializeClient() {
        Config.load();
        AppearanceOverrideState.init();
        BodyTintTextures.init();
        PonyArmorRenderer.init();
        ManeTintTextures.init();
        MagicGlow.init();
        BodyFlightAura.init();
        GlowingItem.initLevitation();
        EyeTintTextures.init();
        TransformationParticles.init();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("magicaland", "expressions"); }
            @Override public void reload(ResourceManager manager) {
                try (var input = manager.getResourceOrThrow(new Identifier("magicaland", "expressions.json")).getInputStream();
                     var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    PonyExpressions.reload(reader);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(Client.class).error("表情库加载失败，保留上一份有效配置", e);
                }
            }
        });
        ModelManager.init();
        MagicHeldItemSounds.init();
        PonyHoofSounds.init();

        // 注册客户端网络处理
        ClientNetworkHandler.register();
        EmoteClient.register();
        EmoteWheelScreen.register();
        PonyFlightVisuals.register();
        PonyTridentVisuals.register();
        UnicornFlightRim.init();

        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.magicaland.config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                "category.magicaland.keys"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKeyBinding.wasPressed()) {
                if (!(client.currentScreen instanceof ConfigScreen)
                        && !ModelManager.isEditing())
                    client.setScreen(new ConfigScreen(client.currentScreen));
            }
        });
    }
}
