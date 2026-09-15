package top.elysianherd.magicaland.client.render;

import software.bernie.geckolib.cache.object.GeoCube;
import top.elysianherd.magicaland.client.animation.PonyExpressions;

public final class EyeMaterials {
    private EyeMaterials() {}

    public static boolean isEyeBone(String name) {
        if (name == null) return false;
        if ("shut".equals(name)) return true;
        for (var style : PonyExpressions.eyeStyles().values())
            if (style.bones().containsValue(name)) return true;
        return false;
    }

    public static boolean isPupil(GeoCube cube) {
        float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        for (var quad : cube.quads()) {
            if (quad == null) continue;
            for (var vertex : quad.vertices()) {
                minU = Math.min(minU, vertex.texU() * 128);
                maxU = Math.max(maxU, vertex.texU() * 128);
                minV = Math.min(minV, vertex.texV() * 128);
                maxV = Math.max(maxV, vertex.texV() * 128);
            }
        }
        // 瞳孔是黑色箱式 UV；睫毛只采样窄色条，二者在生气表情中也须独立。
        return minU >= 7.99f && maxU <= 16.01f && minV >= 4.99f && maxV <= 10.01f
                && maxU - minU > 5;
    }
}
