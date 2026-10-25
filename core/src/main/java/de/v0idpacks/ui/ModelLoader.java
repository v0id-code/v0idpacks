package de.v0idpacks.ui;

import com.google.gson.*;
import de.v0idpacks.core.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;
import static de.v0idpacks.ui.PreviewModel.*;

/** Reads ordinary cuboid JSON models and the vanilla crystal entity atlas. */
public final class ModelLoader {
    @FunctionalInterface public interface Assets { byte[] read(String path) throws IOException; }
    private record Definition(Map<String,String> textures,JsonArray elements) {}
    public static final String CRYSTAL="assets/minecraft/textures/entity/end_crystal/end_crystal.png";
    public static final String CRYSTAL_ITEM="assets/minecraft/textures/item/end_crystal.png";
    private ModelLoader() {}
    public static PreviewModel load(TextureRef ref,TextureImage selected,Assets assets) throws IOException {
        if(ref.path().equals(CRYSTAL))return endCrystal(selected.frame());
        if(ref.path().equals(CRYSTAL_ITEM)) {
            byte[] atlas=assets.read(CRYSTAL);
            byte[] meta=atlas==null?null:assets.read(CRYSTAL+".mcmeta");
            return atlas==null?null:endCrystal(TextureImage.decode(new PackCatalog.Data(atlas,meta==null?new byte[0]:meta)).frame());
        }
        if(!ref.category().equals("block")&&!ref.category().equals("item"))return null;
        String[] parts=ref.path().split("/",4);String ns=parts[1],relative=parts[3].substring(0,parts[3].length()-4);
        String modelId=ns+":"+relative;
        if(ref.category().equals("item")) {
            JsonObject item=json(assets.read("assets/"+ns+"/items/"+relative.substring(5)+".json"));
            if(item!=null && item.has("model")) {
                JsonObject entry=item.getAsJsonObject("model");
                if(!entry.has("type")||!Set.of("model","minecraft:model").contains(entry.get("type").getAsString()))return null;
                modelId=entry.get("model").getAsString();
            }
        }
        Definition def=definition(modelId,assets,new HashSet<>());
        if(def==null||def.elements==null||def.elements.isEmpty())return null;
        if(def.elements.size()>512)throw new IOException("Modell hat zu viele Elemente");
        Map<String,BufferedImage> images=new HashMap<>();images.put(ref.path(),selected.frame());
        long imagePixels=(long)selected.frame().getWidth()*selected.frame().getHeight();
        List<Quad> faces=new ArrayList<>();boolean usesSelected=false;
        for(JsonElement element:def.elements) {
            JsonObject e=element.getAsJsonObject();double[] from=array(e.getAsJsonArray("from"),3),to=array(e.getAsJsonArray("to"),3);
            for(int i=0;i<3;i++)if(to[i]<from[i])throw new IOException("Ungültige Modellgrenzen");
            UnaryOperator<Vertex> rotation=rotation(e);
            for(var entry:e.getAsJsonObject("faces").entrySet()) {
                JsonObject f=entry.getValue().getAsJsonObject();String texture=resolve(f.get("texture").getAsString(),def.textures);
                String path=asset(texture,"textures",".png");usesSelected|=path.equals(ref.path());
                BufferedImage image=images.get(path);
                if(image==null) {
                    if(images.size()>48)throw new IOException("Zu viele Modelltexturen");
                    byte[] png=assets.read(path);if(png==null)throw new IOException("Modelltextur fehlt: "+texture);
                    byte[] meta=assets.read(path+".mcmeta");
                    image=TextureImage.decode(new PackCatalog.Data(png,meta==null?new byte[0]:meta)).frame();
                    imagePixels+=(long)image.getWidth()*image.getHeight();if(imagePixels>32L*1024*1024)throw new IOException("Modelltexturen benötigen zu viel Speicher");
                    images.put(path,image);
                }
                Vertex[] vertices=face(entry.getKey(),from,to);
                double[] uv=f.has("uv")?array(f.getAsJsonArray("uv"),4):defaultUv(entry.getKey(),from,to);
                int degrees=f.has("rotation")?f.get("rotation").getAsInt():0;
                if(degrees%90!=0)throw new IOException("Ungültige UV-Rotation");
                double[][] coords={{uv[0],uv[1]},{uv[0],uv[3]},{uv[2],uv[3]},{uv[2],uv[1]}};
                for(int i=0;i<4;i++){double[] t=coords[Math.floorMod(i+degrees/90,4)];vertices[i]=rotation.apply(vertices[i].uv(t[0]/16,t[1]/16));}
                faces.add(new Quad(vertices[0],vertices[1],vertices[2],vertices[3],image));
            }
        }
        // A matching filename alone is insufficient: don't show a model unrelated to this texture.
        return usesSelected&&!faces.isEmpty()?fit(faces,"JSON-Modell · feste Pose"):null;
    }
    private static Definition definition(String id,Assets assets,Set<String> chain) throws IOException {
        String path=asset(id,"models",".json");
        if(!chain.add(path)||chain.size()>16)throw new IOException("Zyklische oder zu lange Modell-Vererbung");
        JsonObject json=json(assets.read(path));if(json==null)return null;
        Definition parent=null;
        if(json.has("parent"))parent=definition(json.get("parent").getAsString(),assets,chain);
        Map<String,String> textures=new HashMap<>();if(parent!=null)textures.putAll(parent.textures);
        if(json.has("textures"))for(var e:json.getAsJsonObject("textures").entrySet())textures.put(e.getKey(),e.getValue().getAsString());
        return new Definition(textures,json.has("elements")?json.getAsJsonArray("elements"):parent==null?null:parent.elements);
    }
    private static JsonObject json(byte[] bytes) {return bytes==null?null:JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static String resolve(String value,Map<String,String> textures) throws IOException {
        Set<String> seen=new HashSet<>();
        while(value.startsWith("#")) {if(!seen.add(value)||seen.size()>32)throw new IOException("Zyklische Texturreferenz");String key=value.substring(1);value=textures.get(key);if(value==null)throw new IOException("Texturreferenz fehlt: "+key);}
        return value;
    }
    private static String asset(String id,String folder,String extension) throws IOException {
        String[] parts=id.contains(":")?id.split(":",2):new String[]{"minecraft",id};
        String path="assets/"+parts[0]+"/"+folder+"/"+parts[1]+extension;
        if(!PackCatalog.safeAsset(path))throw new IOException("Ungültiger Modellpfad");return path;
    }
    private static double[] array(JsonArray json,int count) throws IOException {
        if(json==null||json.size()!=count)throw new IOException("Ungültige Modellkoordinaten");double[] out=new double[count];
        for(int i=0;i<count;i++){out[i]=json.get(i).getAsDouble();if(!Double.isFinite(out[i])||Math.abs(out[i])>128)throw new IOException("Modellkoordinaten außerhalb des Bereichs");}return out;
    }
    private static UnaryOperator<Vertex> rotation(JsonObject e) throws IOException {
        if(!e.has("rotation"))return UnaryOperator.identity();
        JsonObject r=e.getAsJsonObject("rotation");double[] origin=array(r.getAsJsonArray("origin"),3);String axis=r.get("axis").getAsString();
        if(!Set.of("x","y","z").contains(axis))throw new IOException("Ungültige Modellachse");
        double angle=r.get("angle").getAsDouble();if(!Double.isFinite(angle)||Math.abs(angle)>45)throw new IOException("Ungültige Modellrotation");
        double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle)),scale=r.has("rescale")&&r.get("rescale").getAsBoolean()?1/c:1;
        return p->{double x=p.x()-origin[0],y=p.y()-origin[1],z=p.z()-origin[2];double a=x,b=y,d=z;
            switch(axis){case "x"->{b=(y*c-z*s)*scale;d=(y*s+z*c)*scale;}case "y"->{a=(x*c+z*s)*scale;d=(-x*s+z*c)*scale;}case "z"->{a=(x*c-y*s)*scale;b=(x*s+y*c)*scale;}}
            return p.position(a+origin[0],b+origin[1],d+origin[2]);};
    }
    private static Vertex[] face(String side,double[] a,double[] b) throws IOException {
        double x=a[0],y=a[1],z=a[2],X=b[0],Y=b[1],Z=b[2];
        return switch(side) {
            case "down"->new Vertex[]{vertex(x,y,Z),vertex(x,y,z),vertex(X,y,z),vertex(X,y,Z)};
            case "up"->new Vertex[]{vertex(x,Y,z),vertex(x,Y,Z),vertex(X,Y,Z),vertex(X,Y,z)};
            case "north"->new Vertex[]{vertex(X,Y,z),vertex(X,y,z),vertex(x,y,z),vertex(x,Y,z)};
            case "south"->new Vertex[]{vertex(x,Y,Z),vertex(x,y,Z),vertex(X,y,Z),vertex(X,Y,Z)};
            case "west"->new Vertex[]{vertex(x,Y,z),vertex(x,y,z),vertex(x,y,Z),vertex(x,Y,Z)};
            case "east"->new Vertex[]{vertex(X,Y,Z),vertex(X,y,Z),vertex(X,y,z),vertex(X,Y,z)};
            default->throw new IOException("Unbekannte Modellseite");
        };
    }
    private static double[] defaultUv(String side,double[] a,double[] b) throws IOException {
        return switch(side) {
            case "down"->new double[]{a[0],16-b[2],b[0],16-a[2]};case "up"->new double[]{a[0],a[2],b[0],b[2]};
            case "north"->new double[]{16-b[0],16-b[1],16-a[0],16-a[1]};case "south"->new double[]{a[0],16-b[1],b[0],16-a[1]};
            case "west"->new double[]{a[2],16-b[1],b[2],16-a[1]};case "east"->new double[]{16-b[2],16-b[1],16-a[2],16-a[1]};
            default->throw new IOException("Unbekannte Modellseite");
        };
    }
}
