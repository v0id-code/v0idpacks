package de.v0idpacks.ui;

import de.v0idpacks.core.MinecraftFormatting;
import java.awt.*;

final class FormattedText {
    private FormattedText() {}
    private static Font font(Font base,MinecraftFormatting.Span span) {
        return base.deriveFont(base.getStyle()|(span.bold()?Font.BOLD:0)|(span.italic()?Font.ITALIC:0));
    }
    static int width(Graphics2D g,String text,Font font) {
        int w=0;for(var span:MinecraftFormatting.parse(text))w+=g.getFontMetrics(font(font,span)).stringWidth(span.text());return w;
    }
    static void draw(Graphics2D g,String text,int x,int y,Font base,Color fallback) {
        for(var span:MinecraftFormatting.parse(text)) {
            Font font=font(base,span);g.setFont(font);g.setColor(span.color()==null?fallback:new Color(span.color()));
            String visible=span.obfuscated()?span.text().replaceAll("\\S","#"):span.text();
            g.drawString(visible,x,y);int w=g.getFontMetrics(font).stringWidth(span.text());
            if(span.underline())g.drawLine(x,y+2,x+w,y+2);if(span.strike())g.drawLine(x,y-font.getSize()/3,x+w,y-font.getSize()/3);x+=w;
        }
    }
    static String clip(Graphics2D g,String text,int width,Font base) {
        if(width<=0)return "";
        if(width(g,text,base)<=width)return text;
        int available=width-g.getFontMetrics(base).stringWidth("…"),used=0;StringBuilder out=new StringBuilder();
        for(var span:MinecraftFormatting.parse(text)) {
            FontMetrics metrics=g.getFontMetrics(font(base,span));out.append(MinecraftFormatting.prefix(span));
            for(int i=0;i<span.text().length();) {
                int cp=span.text().codePointAt(i);String ch=new String(Character.toChars(cp));int advance=metrics.stringWidth(ch);
                if(used+advance>available)return out+"§r…";
                used+=advance;out.append(ch);i+=Character.charCount(cp);
            }
        }
        return out+"§r…";
    }
}
