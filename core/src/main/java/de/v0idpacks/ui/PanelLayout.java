package de.v0idpacks.ui;

/** Maps a compact physical-pixel panel to Minecraft's scaled GUI coordinates. */
public record PanelLayout(int x,int y,int width,int height,int pixelWidth,int pixelHeight) {
    public static PanelLayout fit(int guiWidth,int guiHeight,int framebufferWidth,int framebufferHeight) {
        double sx=(double)framebufferWidth/Math.max(1,guiWidth),sy=(double)framebufferHeight/Math.max(1,guiHeight);
        int w=Math.max(1,(int)(Math.min(1120,Math.max(1,framebufferWidth-32))/sx));
        int h=Math.max(1,(int)(Math.min(660,Math.max(1,framebufferHeight-32))/sy));
        return new PanelLayout((guiWidth-w)/2,(guiHeight-h)/2,w,h,Math.max(760,(int)Math.round(w*sx)),Math.max(440,(int)Math.round(h*sy)));
    }
    public int mouseX(double guiX){return (int)Math.floor((guiX-x)*pixelWidth/width);}
    public int mouseY(double guiY){return (int)Math.floor((guiY-y)*pixelHeight/height);}
    public double dragX(double dx){return dx*pixelWidth/width;}
    public double dragY(double dy){return dy*pixelHeight/height;}
}
