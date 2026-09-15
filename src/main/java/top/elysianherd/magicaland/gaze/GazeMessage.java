package top.elysianherd.magicaland.gaze;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record GazeMessage(UUID owner, Identifier dimension, int entityId, UUID target) {
    public static final Identifier CHANNEL = new Identifier("magicaland", "gaze_v1");

    public GazeMessage {
        if (owner == null || dimension == null || dimension.toString().length() > 128
                || (target == null ? entityId != -1 : entityId < 0)) {
            throw new IllegalArgumentException("无效注视目标消息");
        }
    }

    public void write(PacketByteBuf buffer) {
        buffer.writeUuid(owner);
        buffer.writeString(dimension.toString(), 128);
        buffer.writeBoolean(target != null);
        if (target != null) {
            buffer.writeVarInt(entityId);
            buffer.writeUuid(target);
        }
    }

    public static GazeMessage read(PacketByteBuf buffer) {
        UUID owner = buffer.readUuid();
        Identifier dimension = new Identifier(buffer.readString(128));
        boolean present = buffer.readBoolean();
        int entityId = present ? buffer.readVarInt() : -1;
        UUID target = present ? buffer.readUuid() : null;
        if (buffer.readableBytes() != 0) throw new IllegalArgumentException("注视消息包含多余数据");
        return new GazeMessage(owner, dimension, entityId, target);
    }
}
