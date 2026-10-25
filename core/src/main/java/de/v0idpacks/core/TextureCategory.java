package de.v0idpacks.core;

import java.util.Set;
import java.util.regex.Pattern;

/** User-facing filters. Resource paths, not pack names, determine membership. */
public enum TextureCategory {
    ALL("Alle Kategorien"),
    BLOCKS("Blöcke"),
    SWORDS("Schwerter"),
    ARMOR("Rüstung"),
    EQUIPMENT("Equipment"),
    TOOLS("Werkzeuge"),
    RANGED("Fernkampf"),
    FOOD("Nahrung & Tränke"),
    ITEMS("Alle Items"),
    ENTITIES("Entities"),
    GUI("GUI & Effekte"),
    OTHER("Sonstiges");

    private static final Pattern SWORD_NAMES = words("sword|swords");
    private static final Pattern ARMOR_NAMES = words("helmet|helmets|chestplate|chestplates|leggings|boots");
    private static final Pattern TOOL_NAMES = words("axe|axes|pickaxe|pickaxes|shovel|shovels|hoe|hoes|shears|brush|fishing_rod|flint_and_steel|spyglass|compass|clock|carrot_on_a_stick|warped_fungus_on_a_stick");
    private static final Pattern RANGED_NAMES = words("bow|bows|crossbow|crossbows|arrow|arrows|trident");
    private static final Pattern EQUIPMENT_NAMES = words("shield|elytra|totem_of_undying|end_crystal|mace|spear|spears|bucket|saddle|harness|lead|bundle|ender_pearl|ender_eye|firework_rocket|firework_star|fire_charge|wind_charge");
    private static final Pattern ARMOR_LAYERS = Pattern.compile("^(?:models/armor/|trims/|entity/horse/armor/|entity/equipment/(?:humanoid(?:_[a-z_]+)?|horse_body|wolf_body|nautilus_body)/)");
    private static final Pattern EQUIPMENT_ENTITIES = Pattern.compile("^entity/(?:equipment/|shield(?:/|[_.])|trident(?:/|[_.])|end_crystal/|elytra[_.]|projectiles/)");
    private static final Set<String> FOOD_NAMES = Set.of(
            "apple", "golden_apple", "enchanted_golden_apple", "bread", "carrot", "golden_carrot", "potato", "baked_potato", "poisonous_potato", "beetroot",
            "beef", "cooked_beef", "porkchop", "cooked_porkchop", "mutton", "cooked_mutton", "chicken", "cooked_chicken", "rabbit", "cooked_rabbit",
            "cod", "cooked_cod", "salmon", "cooked_salmon", "tropical_fish", "pufferfish", "melon_slice", "sweet_berries", "glow_berries", "cookie", "cake",
            "pumpkin_pie", "mushroom_stew", "rabbit_stew", "suspicious_stew", "beetroot_soup", "dried_kelp", "honey_bottle", "milk_bucket",
            "rotten_flesh", "spider_eye", "chorus_fruit", "potion", "potion_overlay", "splash_potion", "lingering_potion", "ominous_bottle");
    private static final Set<String> GUI_ROOTS = Set.of("gui", "font", "particle", "mob_effect", "effect", "environment", "misc");
    private final String label;
    TextureCategory(String label) { this.label = label; }
    public String label() { return label; }

    public boolean matches(TextureRef texture) {
        String path = texture.path().substring(texture.path().indexOf("/textures/") + 10);
        return matches(path, texture.name());
    }
    private boolean matches(String path, String name) {
        boolean item = path.startsWith("item/") || path.startsWith("items/");
        return switch (this) {
            case ALL -> true;
            case BLOCKS -> path.startsWith("block/") || path.startsWith("blocks/");
            case SWORDS -> item && SWORD_NAMES.matcher(path).find();
            case ARMOR -> ARMOR_LAYERS.matcher(path).find()
                    || item && (ARMOR_NAMES.matcher(path).find() || name.endsWith("_armor") || path.contains("/armor/"));
            // Equipment deliberately includes weapons, tools and armor as well as utility items.
            case EQUIPMENT -> SWORDS.matches(path,name) || ARMOR.matches(path,name) || TOOLS.matches(path,name) || RANGED.matches(path,name)
                    || item && EQUIPMENT_NAMES.matcher(path).find() || EQUIPMENT_ENTITIES.matcher(path).find();
            case TOOLS -> item && TOOL_NAMES.matcher(path).find();
            case RANGED -> item && RANGED_NAMES.matcher(path).find()
                    || path.startsWith("entity/projectiles/") || path.equals("entity/trident.png");
            case FOOD -> item && FOOD_NAMES.contains(name);
            case ITEMS -> item;
            case ENTITIES -> path.startsWith("entity/");
            case GUI -> GUI_ROOTS.contains(path.split("/",2)[0]);
            case OTHER -> !BLOCKS.matches(path,name) && !EQUIPMENT.matches(path,name) && !FOOD.matches(path,name)
                    && !ENTITIES.matches(path,name) && !GUI.matches(path,name);
        };
    }
    private static Pattern words(String names) { return Pattern.compile("(?:^|[/_.-])(?:" + names + ")(?=$|[/_.-])"); }
}
