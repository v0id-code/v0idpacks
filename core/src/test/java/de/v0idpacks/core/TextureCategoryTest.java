package de.v0idpacks.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TextureCategoryTest {
    private static TextureRef texture(String relative) {return new TextureRef("§bSwords & Armor Pack.zip","assets/minecraft/textures/"+relative+".png","");}
    @Test void categoriesUseResourcePathsAndPreserveEveryPackVariant() {
        assertTrue(TextureCategory.BLOCKS.matches(texture("block/diamond_block")));
        assertFalse(TextureCategory.SWORDS.matches(texture("block/sword_banner")));
        assertFalse(TextureCategory.ARMOR.matches(texture("block/stone")));
        for(String name:List.of("diamond_sword","copper_sword","iron_sword_overlay")) assertTrue(TextureCategory.SWORDS.matches(texture("item/"+name)),name);
        assertFalse(TextureCategory.SWORDS.matches(texture("item/diamond_pickaxe")));
        assertTrue(TextureCategory.SWORDS.matches(new TextureRef("Modded Pack","assets/example/textures/item/weapons/swords/ruby.png","")));
        assertTrue(TextureCategory.ALL.matches(texture("anything/custom")));
    }
    @Test void armorIncludesItemIconsOverlaysAndOldAndNewWornLayers() {
        for(String path:List.of("item/diamond_helmet","item/leather_chestplate_overlay","item/iron_leggings","item/netherite_boots","item/diamond_horse_armor","item/wolf_armor",
                "entity/equipment/humanoid/diamond","entity/equipment/humanoid_baby/iron","entity/equipment/humanoid_leggings/leather","entity/equipment/horse_body/gold",
                "models/armor/diamond_layer_1","models/armor/leather_layer_2_overlay","trims/entity/humanoid/coast","trims/items/helmet_trim"))
            assertTrue(TextureCategory.ARMOR.matches(texture(path)),path);
        assertFalse(TextureCategory.ARMOR.matches(texture("item/armor_stand")));
        assertFalse(TextureCategory.ARMOR.matches(texture("entity/equipment/wings/elytra")));
    }
    @Test void equipmentCombinesWeaponsToolsArmorAndUtilityTextures() {
        for(String path:List.of("item/diamond_sword","item/diamond_pickaxe","item/shears","item/netherite_helmet","item/bow_pulling_2","item/crossbow_firework",
                "item/elytra_broken","item/totem_of_undying","item/water_bucket","item/mace","item/copper_spear_in_hand","item/end_crystal","item/ender_pearl",
                "entity/shield/base","entity/shield_base","entity/trident","entity/equipment/wings/elytra","entity/end_crystal/end_crystal","entity/equipment/pig_saddle/saddle"))
            assertTrue(TextureCategory.EQUIPMENT.matches(texture(path)),path);
        assertTrue(TextureCategory.TOOLS.matches(texture("item/fishing_rod_cast")));
        assertFalse(TextureCategory.TOOLS.matches(texture("item/diamond_sword")));
        assertTrue(TextureCategory.RANGED.matches(texture("item/tipped_arrow_head")));
        assertFalse(TextureCategory.RANGED.matches(texture("item/bowl")));
        assertFalse(TextureCategory.EQUIPMENT.matches(texture("block/diamond_block")));
    }
    @Test void foodAndRemainingFiltersKeepMaterialsAndEffectsReachable() {
        for(String name:List.of("golden_apple","cooked_beef","rabbit_stew","potion_overlay","lingering_potion"))assertTrue(TextureCategory.FOOD.matches(texture("item/"+name)));
        for(String name:List.of("rabbit_foot","rabbit_hide","chicken_spawn_egg"))assertFalse(TextureCategory.FOOD.matches(texture("item/"+name)));
        assertTrue(TextureCategory.ITEMS.matches(texture("item/diamond_sword")));
        assertTrue(TextureCategory.ENTITIES.matches(texture("entity/end_crystal/end_crystal")));
        assertTrue(TextureCategory.GUI.matches(texture("gui/sprites/hud/heart/full")));
        assertTrue(TextureCategory.GUI.matches(texture("particle/flame")));
        assertTrue(TextureCategory.OTHER.matches(texture("item/diamond")));
        assertTrue(TextureCategory.OTHER.matches(texture("painting/kebab")));
        assertFalse(TextureCategory.OTHER.matches(texture("item/iron_sword")));
    }
}
