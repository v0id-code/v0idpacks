package de.v0idpacks.ui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/** The user's selected raster artwork, cached at its actual display size. */
final class Branding {
    private static final Map<String,BufferedImage> SCALED=new HashMap<>();
    private static final BufferedImage LOGO=read("/assets/v0idpacks/icon.png"), WORDMARK=read("/assets/v0idpacks/branding/v0idpacks-wordmark.png");
    private Branding() {}
    private static BufferedImage read(String name) {
        try(InputStream in=Branding.class.getResourceAsStream(name)) {
            if(in==null)throw new IOException("Branding fehlt: "+name);
            BufferedImage image=ImageIO.read(in);if(image==null)throw new IOException("Branding nicht lesbar: "+name);return image;
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    static void logo(Graphics2D g,int x,int y,int size){g.drawImage(scaled(LOGO,"logo",size,size),x,y,null);}
    static void wordmark(Graphics2D g,int x,int y,int width){g.drawImage(scaled(WORDMARK,"wordmark",width,Math.max(1,(int)Math.round((double)width*WORDMARK.getHeight()/WORDMARK.getWidth()))),x,y,null);}
    private static BufferedImage scaled(BufferedImage source,String key,int width,int height) {
        String id=key+":"+width+":"+height;BufferedImage cached=SCALED.get(id);if(cached!=null)return cached;
        BufferedImage image=source;
        do {
            int w=Math.max(width,image.getWidth()/2),h=Math.max(height,image.getHeight()/2);
            BufferedImage next=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image,0,0,w,h,null);g.dispose();image=next;
        } while(image.getWidth()!=width||image.getHeight()!=height);
        SCALED.put(id,image);return image;
    }
}
