package de.v0idpacks.core;

import com.google.gson.*;
import java.util.*;

/** Legacy Minecraft formatting, including ampersand aliases and RGB sequences. */
public final class MinecraftFormatting {
    private static final int[] COLORS = {0x000000,0x0000aa,0x00aa00,0x00aaaa,0xaa0000,0xaa00aa,0xffaa00,0xaaaaaa,0x555555,0x5555ff,0x55ff55,0x55ffff,0xff5555,0xff55ff,0xffff55,0xffffff};
    private static final List<String> NAMES = List.of("black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white");
    public record Span(String text, Integer color, boolean bold, boolean italic, boolean underline, boolean strike, boolean obfuscated) {}
    private MinecraftFormatting() {}
    public static List<Span> parse(String text) {
        List<Span> result=new ArrayList<>(); StringBuilder buffer=new StringBuilder();
        Integer color=null; boolean bold=false,italic=false,underline=false,strike=false,obfuscated=false;
        for(int i=0;i<text.length();) {
            char ch=text.charAt(i);
            if((ch=='§'||ch=='&') && i+1<text.length()) {
                char code=Character.toLowerCase(text.charAt(i+1)); int skip=2; Integer rgb=null;
                if(code=='#' && i+8<=text.length() && text.substring(i+2,i+8).matches("[a-fA-F0-9]{6}")) {rgb=Integer.parseInt(text.substring(i+2,i+8),16);skip=8;}
                else if(code=='x' && i+14<=text.length()) {
                    StringBuilder hex=new StringBuilder();
                    for(int j=i+2;j<i+14;j+=2) if((text.charAt(j)=='§'||text.charAt(j)=='&') && Character.digit(text.charAt(j+1),16)>=0) hex.append(text.charAt(j+1));
                    if(hex.length()==6) {rgb=Integer.parseInt(hex.toString(),16);skip=14;}
                }
                int palette="0123456789abcdef".indexOf(code);
                if(rgb!=null||palette>=0||"klmnor".indexOf(code)>=0) {
                    if(!buffer.isEmpty()) {result.add(new Span(buffer.toString(),color,bold,italic,underline,strike,obfuscated));buffer.setLength(0);}
                    if(rgb!=null||palette>=0||code=='r') {color=rgb!=null?rgb:palette>=0?COLORS[palette]:null;bold=italic=underline=strike=obfuscated=false;}
                    else switch(code) {case 'l'->bold=true;case 'o'->italic=true;case 'n'->underline=true;case 'm'->strike=true;case 'k'->obfuscated=true;}
                    i+=skip;continue;
                }
            }
            buffer.append(ch);i++;
        }
        if(!buffer.isEmpty())result.add(new Span(buffer.toString(),color,bold,italic,underline,strike,obfuscated));
        return List.copyOf(result);
    }
    public static String plain(String text) {StringBuilder out=new StringBuilder();for(Span span:parse(text))out.append(span.text());return out.toString();}
    public static String prefix(Span span) {
        StringBuilder out=new StringBuilder("§r");
        if(span.color()!=null) {out.append("§x");for(char c:String.format(Locale.ROOT,"%06x",span.color()).toCharArray())out.append('§').append(c);}
        if(span.bold())out.append("§l");if(span.italic())out.append("§o");if(span.underline())out.append("§n");if(span.strike())out.append("§m");if(span.obfuscated())out.append("§k");return out.toString();
    }
    public static String component(JsonElement json) {return component(json,new Span("",null,false,false,false,false,false),0);}
    private static String component(JsonElement json,Span inherited,int depth) {
        if(json==null||json.isJsonNull()||depth>16)return "";
        if(json.isJsonPrimitive())return prefix(inherited)+json.getAsString();
        if(json.isJsonArray()){StringBuilder out=new StringBuilder();for(JsonElement child:json.getAsJsonArray())out.append(component(child,inherited,depth+1));return out.toString();}
        JsonObject object=json.getAsJsonObject();Integer rgb=inherited.color();
        if(object.has("color")) {
            String color=object.get("color").getAsString();int index=NAMES.indexOf(color);
            if(index>=0)rgb=COLORS[index];else if(color.matches("#[a-fA-F0-9]{6}"))rgb=Integer.parseInt(color.substring(1),16);else if(color.equals("reset"))rgb=null;
        }
        Span style=new Span("",rgb,flag(object,"bold",inherited.bold()),flag(object,"italic",inherited.italic()),flag(object,"underlined",inherited.underline()),flag(object,"strikethrough",inherited.strike()),flag(object,"obfuscated",inherited.obfuscated()));
        StringBuilder result=new StringBuilder(prefix(style)).append(object.has("text")?object.get("text").getAsString():"");
        if(object.has("extra"))result.append(component(object.get("extra"),style,depth+1));
        return result.toString();
    }
    private static boolean flag(JsonObject object,String key,boolean inherited){return object.has(key)?object.get(key).getAsBoolean():inherited;}
}
