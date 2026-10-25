package de.v0idpacks.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArmorPartTest {
    private static TextureRef texture(String path) {return new TextureRef("Helm Brustplatte Hose Schuhe.zip","assets/example/textures/"+path+".png","");}

    @Test void separatesSlotsAcrossMaterialsNamespacesAndOverlays() {
        Map<ArmorPart,String> names=Map.of(ArmorPart.HELMET,"helmet",ArmorPart.CHESTPLATE,"chestplate",ArmorPart.LEGGINGS,"leggings",ArmorPart.BOOTS,"boots");
        for(var slot:names.entrySet())for(String material:List.of("diamond","iron","copper","netherite","leather","custom_ruby")) {
            for(String suffix:List.of("","_overlay")) {
                TextureRef ref=texture("item/"+material+"_"+slot.getValue()+suffix);
                assertTrue(slot.getKey().matches(ref),ref.path());
                for(ArmorPart other:names.keySet())if(other!=slot.getKey())assertFalse(other.matches(ref),other+" must not include "+ref.path());
            }
        }
        assertTrue(ArmorPart.HELMET.matches(texture("item/armor/helmets/ruby")));
        assertTrue(ArmorPart.CHESTPLATE.matches(texture("items/leather_chestplate")));
    }
    @Test void sharedAtlasesTrimsAndOtherItemsRemainOutsideIndividualSlotFilters() {
        for(String path:List.of("entity/equipment/humanoid/diamond","entity/equipment/humanoid_leggings/diamond","models/armor/diamond_layer_1",
                "models/armor/diamond_layer_2","trims/items/helmet_trim","item/diamond_horse_armor","item/wolf_armor","item/armor_stand","item/iron_sword","block/helmet_decoration")) {
            TextureRef ref=texture(path);assertTrue(ArmorPart.ALL.matches(ref));
            for(ArmorPart part:ArmorPart.values())if(part!=ArmorPart.ALL)assertFalse(part.matches(ref),part+" must not include "+path);
        }
    }
}
