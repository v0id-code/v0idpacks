package de.v0idpacks.core;

import java.util.regex.Pattern;

/** Individual armor item textures; shared worn-armor atlases remain in ALL. */
public enum ArmorPart {
    ALL("Alle", null),
    HELMET("Helm", "helmets?"),
    CHESTPLATE("Brustplatte", "chestplates?"),
    LEGGINGS("Hose", "leggings"),
    BOOTS("Schuhe", "boots");

    private final String label;
    private final Pattern names;

    ArmorPart(String label, String names) {
        this.label = label;
        this.names = names == null ? null : Pattern.compile("(?:^|[/_.-])(?:" + names + ")(?=$|[/_.-])");
    }
    public String label() { return label; }

    public boolean matches(TextureRef texture) {
        if (this == ALL) return true;
        String path = texture.path().substring(texture.path().indexOf("/textures/") + 10);
        return (path.startsWith("item/") || path.startsWith("items/")) && names.matcher(path).find();
    }
}
