package de.v0idpacks.ui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Feather (MIT) icons from Iconify. Original SVGs and native 16px exports: assets/ui/icons. */
final class UiIcons {
    private static final Map<String,BufferedImage> CACHE=new HashMap<>();
    private UiIcons() {}
    static void draw(Graphics2D g,String name,int x,int y,Color color) {
        BufferedImage image=CACHE.computeIfAbsent(name+":"+color.getRGB(),key->{
            try(var resource=UiIcons.class.getResourceAsStream("/assets/v0idpacks/ui/"+name+".png")) {
                if(resource==null)throw new IllegalStateException("Missing UI icon: "+name);
                BufferedImage source=ImageIO.read(resource),tinted=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
                for(int iy=0;iy<16;iy++)for(int ix=0;ix<16;ix++)
                    tinted.setRGB(ix,iy,(source.getRGB(ix,iy)&0xff000000)|(color.getRGB()&0x00ffffff));
                return tinted;
            }catch(java.io.IOException e){throw new IllegalStateException("Cannot read UI icon: "+name,e);}
        });
        g.drawImage(image,x,y,null);
    }
}
