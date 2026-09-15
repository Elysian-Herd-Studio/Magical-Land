package top.elysianherd.magicaland.client.config.style;

public final class PonyStyleDefinition {
    public final PonyStylePart part;
    public final String id;

    PonyStyleDefinition(PonyStylePart part, String id) {
        this.part = part;
        this.id = id;
    }

    public String boneNamePrefix() {
        return "Style" + id + part.boneSuffix;
    }
}
