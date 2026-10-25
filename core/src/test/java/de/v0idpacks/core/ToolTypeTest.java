package de.v0idpacks.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolTypeTest {
    private static TextureRef texture(String path) {return new TextureRef("Axes and Pickaxes.zip","assets/example/textures/"+path+".png","");}

    @Test void toolTypesStaySeparateAcrossMaterialsNamespacesAndTextureVariants() {
        Map<ToolType,String> names=Map.of(ToolType.PICKAXE,"pickaxe",ToolType.AXE,"axe",ToolType.SHOVEL,"shovel",ToolType.HOE,"hoe");
        for(var type:names.entrySet())for(String material:List.of("wooden","stone","iron","golden","diamond","netherite","copper","custom_ruby")) {
            for(String suffix:List.of("","_overlay","_head")) {
                TextureRef ref=texture("item/"+material+"_"+type.getValue()+suffix);
                assertTrue(type.getKey().matches(ref),ref.path());
                for(ToolType other:names.keySet())if(other!=type.getKey())assertFalse(other.matches(ref),other+" must not include "+ref.path());
            }
        }
        assertTrue(ToolType.PICKAXE.matches(texture("item/tools/pickaxes/ruby")));
        assertTrue(ToolType.AXE.matches(texture("items/iron_axe")));
        assertFalse(ToolType.AXE.matches(texture("item/tools/pickaxes/ruby")));
    }
    @Test void otherToolsAndNonItemTexturesDoNotLeakIntoSpecificTypes() {
        for(String path:List.of("item/shears","item/brush","item/compass","item/fishing_rod","item/iron_sword","item/diamond_helmet",
                "block/axe_decoration","entity/axe","gui/pickaxe","item/taxex","item/shoelace")) {
            TextureRef ref=texture(path);assertTrue(ToolType.ALL.matches(ref));
            for(ToolType type:ToolType.values())if(type!=ToolType.ALL)assertFalse(type.matches(ref),type+" must not include "+path);
        }
    }
}
