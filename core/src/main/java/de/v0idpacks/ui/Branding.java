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
            BufferedImage image=ImageIO.read(in);if(image==null)throw new IOException("Branding nicht lesbar: "+name);return visibleContent(image);
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
    // Align the artwork itself; transparent canvas margins differ between the two assets.
    private static BufferedImage visibleContent(BufferedImage image) {
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            if((image.getRGB(x,y)>>>24)<=16)continue;
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
        }
        return right<left?image:image.getSubimage(left,top,right-left+1,bottom-top+1);
    }
    static void logo(Graphics2D g,int x,int y,int size){
        double scale=(double)size/Math.max(LOGO.getWidth(),LOGO.getHeight());
        int w=Math.max(1,(int)Math.round(LOGO.getWidth()*scale)),h=Math.max(1,(int)Math.round(LOGO.getHeight()*scale));
        g.drawImage(scaled(LOGO,"logo",w,h),x+(size-w)/2,y+(size-h)/2,null);
    }
    static void header(Graphics2D g,int x,int centerY,int height) {
        int logoWidth=Math.max(1,(int)Math.round((double)height*LOGO.getWidth()/LOGO.getHeight()));
        int wordmarkWidth=Math.max(1,(int)Math.round((double)height*WORDMARK.getWidth()/WORDMARK.getHeight()));
        int y=centerY-height/2;
        g.drawImage(scaled(LOGO,"logo",logoWidth,height),x,y,null);
        g.drawImage(scaled(WORDMARK,"wordmark",wordmarkWidth,height),x+logoWidth+10,y,null);
    }
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
