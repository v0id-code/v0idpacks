package de.v0idpacks.ui;

import de.v0idpacks.core.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static BufferedImage atlas() {
        BufferedImage atlas=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<64;x++) {
            int c=y>=16?0xff706c7c:x>=32?0xffff44cc:(x%8==0||y%8==0||x%8==7||y%8==7)?0xff88eeff:0;
            atlas.setRGB(x,y,c);
        }
        return atlas;
    }
    @Test void crystalUsesSeparateGlassCoreAndBaseAtlasRegionsAndRevealsCore() throws Exception {
        PreviewModel model=ModelLoader.load(new TextureRef("test",ModelLoader.CRYSTAL,""),new TextureImage(atlas(),64,32,false),path->null);
        assertNotNull(model);
        BufferedImage image=new PreviewRenderer().render(model,320,320,-.48,.25,1);
        int pink=0,glass=0,base=0;
        for(int y=0;y<320;y++)for(int x=0;x<320;x++) {
            int c=image.getRGB(x,y),r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;if((c>>>24)==0)continue;
            if(r>g*2&&b>g*2)pink++;else if(g>r*1.3&&b>r*1.3)glass++;else base++;
        }
        assertTrue(pink>200,"Core must be visible through glass cutouts");assertTrue(glass>200);assertTrue(base>200);assertEquals(0,image.getRGB(0,0));
        Path dir=Path.of("build/previews");Files.createDirectories(dir);ImageIO.write(image,"png",dir.resolve("end-crystal-test-atlas.png").toFile());
    }
    @Test void crystalInventoryIconUsesEntityAtlasFor3dAndLeavesIconUnchanged() throws Exception {
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(atlas(),"png",png);
        TextureImage icon=new TextureImage(WorkshopTest.sword(0xff00ff00),16,16,false);
        PreviewModel model=ModelLoader.load(new TextureRef("test",ModelLoader.CRYSTAL_ITEM,""),icon,path->path.equals(ModelLoader.CRYSTAL)?png.toByteArray():null);
        assertNotNull(model);assertEquals(16,icon.frame().getWidth());assertEquals(64,model.quads().getFirst().texture().getWidth());
        assertNull(ModelLoader.load(new TextureRef("test",ModelLoader.CRYSTAL_ITEM,""),icon,path->null));
    }
    @Test void jsonParentsTextureVariablesUvAndRotationUseTheSelectedTexture() throws Exception {
        Map<String,byte[]> assets=new HashMap<>();
        assets.put("assets/minecraft/models/block/demo.json",bytes("{\"parent\":\"block/parent\",\"textures\":{\"side\":\"#all\",\"all\":\"block/demo\"}}"));
        assets.put("assets/minecraft/models/block/parent.json",bytes("""
                {"elements":[{"from":[0,0,0],"to":[16,8,8],"rotation":{"origin":[8,8,8],"axis":"y","angle":22.5,"rescale":true},
                "faces":{"south":{"uv":[0,0,8,8],"texture":"#side","rotation":90},"up":{"texture":"#all"}}}]}
                """));
        BufferedImage image=WorkshopTest.sword(0xff00ff00);
        PreviewModel model=ModelLoader.load(new TextureRef("test","assets/minecraft/textures/block/demo.png",""),new TextureImage(image,16,16,false),assets::get);
        assertNotNull(model);assertEquals(2,model.quads().size());assertSame(image,model.quads().getFirst().texture());
        assertEquals(.5,model.quads().getFirst().b().u());assertEquals(.5,model.quads().getFirst().b().v());
        assertNotEquals(model.quads().getFirst().a().z(),model.quads().getFirst().d().z());
    }
    @Test void modernItemDefinitionCanPointAtAnArbitrarilyNamedModel() throws Exception {
        Map<String,byte[]> assets=Map.of("assets/minecraft/items/demo.json",bytes("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"custom:tools/sample\"}}"),
                "assets/custom/models/tools/sample.json",bytes("{\"textures\":{\"layer\":\"minecraft:item/demo\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,1],\"faces\":{\"south\":{\"texture\":\"#layer\"}}}]}"));
        assertNotNull(ModelLoader.load(new TextureRef("test","assets/minecraft/textures/item/demo.png",""),new TextureImage(WorkshopTest.sword(0xff00ff00),16,16,false),assets::get));
    }
    @Test void cyclicAndUnsafeModelDependenciesAreRejectedAndUnrelatedModelsIgnored() throws Exception {
        TextureRef ref=new TextureRef("test","assets/minecraft/textures/block/demo.png","");TextureImage image=new TextureImage(atlas(),64,32,false);
        assertThrows(IOException.class,()->ModelLoader.load(ref,image,path->bytes("{\"parent\":\"block/demo\"}")));
        assertThrows(IOException.class,()->ModelLoader.load(ref,image,path->bytes("{\"parent\":\"../private\"}")));
        Map<String,byte[]> assets=Map.of("assets/minecraft/models/block/demo.json",bytes("{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{\"south\":{\"texture\":\"#a\"}}}],\"textures\":{\"a\":\"#b\",\"b\":\"#a\"}}"));
        assertThrows(IOException.class,()->ModelLoader.load(ref,image,assets::get));
    }
    @Test void translucentModelFacesBlendWithTheVisibleCore() {
        BufferedImage blue=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);blue.setRGB(0,0,0x800000ff);
        BufferedImage red=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);red.setRGB(0,0,0xffff0000);
        var a=new PreviewModel.Vertex(-.5,.5,0,0,0);var b=new PreviewModel.Vertex(-.5,-.5,0,0,1);var c=new PreviewModel.Vertex(.5,-.5,0,1,1);var d=new PreviewModel.Vertex(.5,.5,0,1,0);
        var back=new PreviewModel.Quad(a,b,c,d,red);var front=new PreviewModel.Quad(a.position(-.5,.5,.1),b.position(-.5,-.5,.1),c.position(.5,-.5,.1),d.position(.5,.5,.1),blue);
        BufferedImage rendered=new PreviewRenderer().render(new PreviewModel(List.of(front,back),"test"),100,100,0,0,1);
        int color=rendered.getRGB(45,50);assertEquals(255,color>>>24);assertTrue(((color>>>16)&255)>50);assertTrue((color&255)>50);
    }
}
