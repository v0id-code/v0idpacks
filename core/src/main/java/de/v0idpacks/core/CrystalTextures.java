package de.v0idpacks.core;

import java.util.List;

/** The inventory sprite and placed entity must be selected as one source-pack pair. */
public final class CrystalTextures {
    public static final String ITEM = "assets/minecraft/textures/item/end_crystal.png";
    public static final String ENTITY = "assets/minecraft/textures/entity/end_crystal/end_crystal.png";
    public static final List<String> PATHS = List.of(ITEM, ENTITY);

    private CrystalTextures() {}

    public static boolean contains(String path) { return PATHS.contains(path); }
    public static String companion(String path) { return path.equals(ITEM) ? ENTITY : ITEM; }
}
