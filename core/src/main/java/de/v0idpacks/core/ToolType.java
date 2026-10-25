package de.v0idpacks.core;

import java.util.regex.Pattern;

/** Tool item subcategories. Word boundaries keep axes separate from pickaxes. */
public enum ToolType {
    ALL("Alle", null),
    PICKAXE("Spitzhacken", "pickaxes?"),
    AXE("Äxte", "axes?"),
    SHOVEL("Schaufeln", "shovels?"),
    HOE("Hacken", "hoes?");

    private final String label;
    private final Pattern names;

    ToolType(String label, String names) {
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
