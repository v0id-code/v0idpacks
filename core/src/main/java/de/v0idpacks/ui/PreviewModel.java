package de.v0idpacks.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.UnaryOperator;

/** Textured, UV-mapped geometry in a version-independent coordinate system. */
public record PreviewModel(List<Quad> quads, String label) {
    public record Vertex(double x, double y, double z, double u, double v) {
        Vertex uv(double u, double v) { return new Vertex(x,y,z,u,v); }
        Vertex position(double x, double y, double z) { return new Vertex(x,y,z,u,v); }
    }
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, BufferedImage texture) {
        public List<Vertex> vertices() { return List.of(a,b,c,d); }
        Quad map(UnaryOperator<Vertex> f) { return new Quad(f.apply(a),f.apply(b),f.apply(c),f.apply(d),texture); }
    }
    public PreviewModel { quads = List.copyOf(quads); }
    public static PreviewModel fit(List<Quad> quads, String label) {
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY}, max={-min[0],-min[1],-min[2]};
        for(Quad q:quads)for(Vertex v:q.vertices()) {double[] xyz={v.x,v.y,v.z};for(int i=0;i<3;i++){min[i]=Math.min(min[i],xyz[i]);max[i]=Math.max(max[i],xyz[i]);}}
        double size=Math.max(max[0]-min[0],Math.max(max[1]-min[1],max[2]-min[2]));
        if(!Double.isFinite(size)||size<.001)throw new IllegalArgumentException("Leeres Modell");
        double scale=1.12/size;
        return new PreviewModel(quads.stream().map(q->q.map(v->v.position((v.x-(min[0]+max[0])/2)*scale,(v.y-(min[1]+max[1])/2)*scale,(v.z-(min[2]+max[2])/2)*scale))).toList(),label);
    }
    static Vertex vertex(double x,double y,double z) {return new Vertex(x,y,z,0,0);}
    /** Minecraft ModelPart box UV unfolding, including its flipped upper face. */
    static void box(List<Quad> out, BufferedImage image, double x,double y,double z,double dx,double dy,double dz,double u,double v,UnaryOperator<Vertex> transform) {
        Vertex[] p={vertex(x,y,z),vertex(x+dx,y,z),vertex(x+dx,y+dy,z),vertex(x,y+dy,z),vertex(x,y,z+dz),vertex(x+dx,y,z+dz),vertex(x+dx,y+dy,z+dz),vertex(x,y+dy,z+dz)};
        int[][] indices={{5,4,0,1},{2,3,7,6},{0,4,7,3},{1,0,3,2},{5,1,2,6},{4,5,6,7}};
        double[][] uv={{u+dz,v,u+dz+dx,v+dz},{u+dz+dx,v+dz,u+dz+dx*2,v},{u,v+dz,u+dz,v+dz+dy},{u+dz,v+dz,u+dz+dx,v+dz+dy},{u+dz+dx,v+dz,u+dz*2+dx,v+dz+dy},{u+dz*2+dx,v+dz,u+dz*2+dx*2,v+dz+dy}};
        for(int i=0;i<6;i++) {
            int[] f=indices[i];double[] t=uv[i];
            // Entity atlases use logical 64x32 UVs even for high-resolution packs.
            out.add(new Quad(p[f[0]].uv(t[2]/64,t[1]/32),p[f[1]].uv(t[0]/64,t[1]/32),p[f[2]].uv(t[0]/64,t[3]/32),p[f[3]].uv(t[2]/64,t[3]/32),image).map(transform));
        }
    }
    public static PreviewModel endCrystal(BufferedImage atlas) {
        List<Quad> faces=new ArrayList<>();
        box(faces,atlas,-6,0,-6,12,4,12,0,16,UnaryOperator.identity());
        // Vanilla pose at age 0: nested 60-degree rotations, two glass shells and core.
        double[] sizes={1,.875,.875*.765625};
        for(int layer=0;layer<3;layer++) {
            double size=sizes[layer], angle=(layer+1)*Math.PI/3;
            box(faces,atlas,-4,-4,-4,8,8,8,layer==2?32:0,0,p->{
                double x=p.x*size,y=p.y*size,z=p.z*size,c=Math.cos(angle),s=Math.sin(angle),a=Math.sqrt(.5),dot=(x+z)*a;
                return p.position(x*c-y*a*s+a*dot*(1-c),y*c+(x-z)*a*s+15.2,z*c+y*a*s+a*dot*(1-c));
            });
        }
        return fit(faces,"End Crystal · Entity-Textur");
    }
}
