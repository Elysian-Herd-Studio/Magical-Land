package top.elysianherd.magicaland.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import top.elysianherd.magicaland.client.config.Config;
import top.elysianherd.magicaland.client.config.ModelConfig;
import top.elysianherd.magicaland.network.TransformationMessage;

public final class TransformationParticles {
    private static final Map<UUID, Long> LAST_PLAYED = new LinkedHashMap<>();
    private static final BufferBuilder BUFFER = new BufferBuilder(4096);
    private static final Identifier DOT = new Identifier("minecraft", "generic_0");
    private static final Identifier STAR = new Identifier("minecraft", "glitter_3");
    private static boolean initialized;
    private static long budgetStart;
    private static int budgetUsed;

    private TransformationParticles() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LAST_PLAYED.clear();
            budgetStart = 0;
            budgetUsed = 0;
        });
    }

    public static void play(PlayerEntity player, ModelConfig config) {
        init();
        MinecraftClient client = MinecraftClient.getInstance();
        if (config == null || !visible(client, player)) return;
        long now = System.nanoTime();
        Long last = LAST_PLAYED.get(player.getUuid());
        if (last != null && now - last < TransformationMessage.COOLDOWN_NANOS) return;
        if (budgetStart == 0 || now - budgetStart >= 500_000_000L) {
            budgetStart = now;
            budgetUsed = 0;
        }
        if (budgetUsed >= 8) return;
        var texture = client.getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
        if (!(texture instanceof SpriteAtlasTexture atlas)) return;
        Sprite dot = atlas.getSprite(DOT), star = atlas.getSprite(STAR);
        LAST_PLAYED.put(player.getUuid(), now);
        while (LAST_PLAYED.size() > 256) LAST_PLAYED.remove(LAST_PLAYED.keySet().iterator().next());
        budgetUsed++;
        client.particleManager.addParticle(new Burst(client.world, player, dot, star,
                TransformationBurstMath.sample(player.getUuid().getLeastSignificantBits() ^ now,
                        config.bodyColor, config.magicGlowColor, player.bodyYaw)));
    }

    private static boolean visible(MinecraftClient client, PlayerEntity player) {
        if (client.world == null || client.player == null || player == null
                || player.getWorld() != client.world || player.isRemoved() || !player.isAlive()
                || player.isInvisible() || player.isSpectator() || !Config.getInstance().replacePlayerModel
                || client.options.getParticles().getValue() == ParticlesMode.MINIMAL) return false;
        return player == client.player || (client.player.squaredDistanceTo(player) <= 32 * 32
                && client.player.canSee(player) && !player.isInvisibleTo(client.player));
    }

    private static final class Burst extends Particle {
        private final PlayerEntity owner;
        private final Sprite dot, star;
        private final List<TransformationBurstMath.Speck> specks;

        private Burst(ClientWorld world, PlayerEntity owner, Sprite dot, Sprite star,
                List<TransformationBurstMath.Speck> specks) {
            super(world, owner.getX(), owner.getY(), owner.getZ());
            this.owner = owner;
            this.dot = dot;
            this.star = star;
            this.specks = specks;
            maxAge = TransformationBurstMath.LIFETIME_TICKS;
            collidesWithWorld = false;
        }

        @Override public void tick() {
            if (++age >= maxAge || !visible(MinecraftClient.getInstance(), owner)) markDead();
        }

        @Override public ParticleTextureSheet getType() { return ParticleTextureSheet.CUSTOM; }

        @Override public void buildGeometry(VertexConsumer unused, Camera camera, float tickDelta) {
            if (!visible(MinecraftClient.getInstance(), owner)) return;
            float t = age + tickDelta;
            float opacity = TransformationBurstMath.opacity(t);
            if (opacity <= .001f) return;
            boolean oldDepthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            boolean oldBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            int oldTexture = RenderSystem.getShaderTexture(0);
            var oldShader = RenderSystem.getShader();
            float[] oldColor = RenderSystem.getShaderColor().clone();
            try {
                var client = MinecraftClient.getInstance();
                var particleTarget = client.worldRenderer.getParticlesFramebuffer();
                boolean isolatedParticleTarget = particleTarget != null
                        && particleTarget.fbo != client.getFramebuffer().fbo
                        && particleTarget.fbo == GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                // Fabulous 用独立粒子深度排序；只写该目标，绝不写主世界深度。
                RenderSystem.depthMask(isolatedParticleTarget);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getParticleProgram);
                RenderSystem.setShaderTexture(0, SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                BUFFER.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
                Vec3d cameraPosition = camera.getPos();
                double travel = TransformationBurstMath.displacement(t);
                int index = 0;
                boolean reduced = MinecraftClient.getInstance().options.getParticles().getValue() == ParticlesMode.DECREASED;
                for (var speck : specks) {
                    if (reduced && !speck.star() && ++index % 2 == 0) continue;
                    Sprite sprite = speck.star() ? star : dot;
                    float px = (float) (x + speck.x() + speck.vx() * travel - cameraPosition.x);
                    float py = (float) (y + speck.y() + speck.vy() * travel - cameraPosition.y);
                    float pz = (float) (z + speck.z() + speck.vz() * travel - cameraPosition.z);
                    float size = speck.size() * (1 - .35f * t / maxAge);
                    float rotation = speck.star() ? speck.angle() + t * .035f : 0;
                    var facing = new org.joml.Quaternionf(camera.getRotation()).rotateZ(rotation);
                    Vector3f right = new Vector3f(size, 0, 0).rotate(facing);
                    Vector3f up = new Vector3f(0, size, 0).rotate(facing);
                    // generic_0 只有中心一个实像素，直接采其中心，避免点被透明边距缩成不可见。
                    float uvLow = speck.star() ? .25f : .5625f;
                    float uvHigh = speck.star() ? .875f : .5625f;
                    float minU = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * uvLow;
                    float maxU = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * uvHigh;
                    float minV = sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * uvLow;
                    float maxV = sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * uvHigh;
                    vertex(px, py, pz, right, up, -1, -1, maxU, maxV, speck.rgb(), opacity);
                    vertex(px, py, pz, right, up, -1, 1, maxU, minV, speck.rgb(), opacity);
                    vertex(px, py, pz, right, up, 1, 1, minU, minV, speck.rgb(), opacity);
                    vertex(px, py, pz, right, up, 1, -1, minU, maxV, speck.rgb(), opacity);
                }
                BufferRenderer.drawWithGlobalProgram(BUFFER.end());
            } finally {
                try {
                    if (BUFFER.isBuilding()) {
                        var unfinished = BUFFER.endNullable();
                        if (unfinished != null) unfinished.release();
                    }
                    BUFFER.clear();
                } finally {
                    RenderSystem.depthMask(oldDepthWrite);
                    RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
                    if (oldBlend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
                    RenderSystem.setShaderTexture(0, oldTexture);
                    RenderSystem.setShader(() -> oldShader);
                    RenderSystem.setShaderColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
                }
            }
        }

        private static void vertex(float x, float y, float z, Vector3f right, Vector3f up,
                int sx, int sy, float u, float v, int rgb, float alpha) {
            BUFFER.vertex(x + right.x * sx + up.x * sy, y + right.y * sx + up.y * sy,
                    z + right.z * sx + up.z * sy).texture(u, v)
                    .color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha)
                    .light(0xE000E0).next();
        }
    }
}
