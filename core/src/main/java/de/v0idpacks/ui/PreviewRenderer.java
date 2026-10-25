package de.v0idpacks.ui;

import java.awt.image.BufferedImage;
import java.util.*;

/** Small software rasterizer: independent of Minecraft's OpenGL/Vulkan/render-state revisions. */
public final class PreviewRenderer {
    public enum Mode { ITEM, CUBE, FLAT, AUTO }
    private record Vertex(double x, double y, double z, double u, double v) {}
    private record Face(Vertex a, Vertex b, Vertex c, Vertex d, double nx, double ny, double nz, BufferedImage texture) {
        Face(Vertex a,Vertex b,Vertex c,Vertex d,double nx,double ny,double nz) {this(a,b,c,d,nx,ny,nz,null);}
    }
    private record ScreenFace(Projected a,Projected b,Projected c,Projected d,double light,BufferedImage texture) {
        double z(){return (a.z+b.z+c.z+d.z)/4;}
    }
    private record Projected(double x, double y, double z, double u, double v) {}
    private BufferedImage source;
    private PreviewModel model;
    private Mode mode;
    private List<Face> mesh = List.of();
    private int width, height;
    private int[] pixels;
    private double[] depth;
    private double cy, sy, cx, sx, scale;

    public BufferedImage render(BufferedImage texture, Mode mode, int width, int height, double yaw, double pitch, double zoom) {
        if (this.source != texture || this.mode != mode || model!=null) {
            model=null;
            this.source = texture; this.mode = mode; mesh = createMesh(texture, mode);
        }
        return draw(width,height,yaw,pitch,zoom);
    }
    public BufferedImage render(PreviewModel model,int width,int height,double yaw,double pitch,double zoom) {
        if(this.model!=model) {
            this.model=model;mode=Mode.AUTO;
            mesh=model.quads().stream().map(q->{
                Vertex a=vertex(q.a()),b=vertex(q.b()),c=vertex(q.c()),d=vertex(q.d());
                double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
                double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx, length=Math.sqrt(nx*nx+ny*ny+nz*nz);
                return new Face(a,b,c,d,nx/Math.max(length,1e-9),ny/Math.max(length,1e-9),nz/Math.max(length,1e-9),q.texture());
            }).toList();
        }
        return draw(width,height,yaw,pitch,zoom);
    }
    private static Vertex vertex(PreviewModel.Vertex v){return p(v.x(),v.y(),v.z(),v.u(),v.v());}
    private BufferedImage draw(int width,int height,double yaw,double pitch,double zoom) {
        this.width = width; this.height = height;
        pixels = new int[width * height]; depth = new double[pixels.length];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        cy = Math.cos(yaw); sy = Math.sin(yaw); cx = Math.cos(pitch); sx = Math.sin(pitch);
        scale = Math.min(width, height) * 0.63 * zoom;
        if (mode == Mode.FLAT) { cy = cx = 1; sy = sx = 0; }
        List<ScreenFace> projected=new ArrayList<>(mesh.size());
        for (Face face : mesh) {
            Projected a = project(face.a), b = project(face.b), c = project(face.c), d = project(face.d);
            double nx = face.nx * cy + face.nz * sy;
            double nz = -face.nx * sy + face.nz * cy;
            double ny = face.ny * cx - nz * sx;
            nz = face.ny * sx + nz * cx;
            double light = mode == Mode.FLAT ? 1 : 0.65 + 0.35 * Math.max(0, -nx * 0.35 + ny * 0.65 + nz * 0.68);
            projected.add(new ScreenFace(a,b,c,d,light,face.texture==null?source:face.texture));
        }
        // Opaque/cutout surfaces establish depth; translucent surfaces blend back to front.
        for(ScreenFace f:projected)drawFace(f,false);
        projected.sort(Comparator.comparingDouble(ScreenFace::z));
        for(ScreenFace f:projected)drawFace(f,true);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }
    private Projected project(Vertex v) {
        double x = v.x * cy + v.z * sy, z = -v.x * sy + v.z * cy;
        double y = v.y * cx - z * sx;
        z = v.y * sx + z * cx;
        return new Projected(width / 2.0 + x * scale, height / 2.0 - y * scale, z, v.u, v.v);
    }
    private void drawFace(ScreenFace f,boolean translucent){triangle(f.a,f.b,f.c,f.light,f.texture,translucent,false);triangle(f.a,f.c,f.d,f.light,f.texture,translucent,true);}
    private void triangle(Projected a, Projected b, Projected c, double light,BufferedImage texture,boolean translucent,boolean sharedEdge) {
        double area = edge(a.x, a.y, b.x, b.y, c.x, c.y);
        if (Math.abs(area) < 1e-8) return;
        int minX = Math.max(0, (int)Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int maxX = Math.min(width - 1, (int)Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int minY = Math.max(0, (int)Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int maxY = Math.min(height - 1, (int)Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            double u = edge(b.x, b.y, c.x, c.y, x + .5, y + .5) / area;
            double v = edge(c.x, c.y, a.x, a.y, x + .5, y + .5) / area;
            double w = 1 - u - v;
            if (u < -1e-7 || v < -1e-7 || w < -1e-7) continue;
            if(sharedEdge && Math.abs(w)<1e-7)continue;
            double z = a.z * u + b.z * v + c.z * w;
            int idx = x + y * width;
            if (z < depth[idx]) continue;
            double tu = a.u * u + b.u * v + c.u * w, tv = a.v * u + b.v * v + c.v * w;
            int tx = Math.clamp((int)(tu * texture.getWidth()), 0, texture.getWidth() - 1);
            int ty = Math.clamp((int)(tv * texture.getHeight()), 0, texture.getHeight() - 1);
            int color = texture.getRGB(tx, ty), alpha = color >>> 24;
            if (alpha < 16 || translucent!=(alpha<250)) continue;
            int r = (int)(((color >>> 16) & 255) * light), g = (int)(((color >>> 8) & 255) * light), bl = (int)((color & 255) * light);
            if(translucent) {
                int old=pixels[idx],oldAlpha=old>>>24;
                double a1=alpha/255.,a0=oldAlpha/255.,out=a1+a0*(1-a1);
                r=(int)((r*a1+((old>>>16)&255)*a0*(1-a1))/out);
                g=(int)((g*a1+((old>>>8)&255)*a0*(1-a1))/out);
                bl=(int)((bl*a1+(old&255)*a0*(1-a1))/out);
                pixels[idx]=((int)Math.round(out*255)<<24)|(r<<16)|(g<<8)|bl;
            } else {pixels[idx] = 0xff000000 | (r << 16) | (g << 8) | bl;depth[idx] = z;}
        }
    }
    private static double edge(double ax, double ay, double bx, double by, double x, double y) { return (x - ax) * (by - ay) - (y - ay) * (bx - ax); }
    private static Vertex p(double x, double y, double z, double u, double v) { return new Vertex(x, y, z, u, v); }
    private static List<Face> createMesh(BufferedImage image, Mode mode) {
        List<Face> faces = new ArrayList<>();
        double ratio = (double)image.getWidth() / image.getHeight();
        double hw = Math.min(1, ratio) * .5, hh = Math.min(1, 1 / ratio) * .5;
        if (mode == Mode.CUBE) { hw = hh = .42; }
        double d = mode == Mode.CUBE ? .42 : .035;
        faces.add(new Face(p(-hw,hh,d,0,0),p(hw,hh,d,1,0),p(hw,-hh,d,1,1),p(-hw,-hh,d,0,1),0,0,1));
        if (mode == Mode.FLAT) return faces;
        faces.add(new Face(p(hw,hh,-d,1,0),p(-hw,hh,-d,0,0),p(-hw,-hh,-d,0,1),p(hw,-hh,-d,1,1),0,0,-1));
        if (mode == Mode.CUBE) {
            faces.add(new Face(p(-hw,hh,-d,0,0),p(hw,hh,-d,1,0),p(hw,hh,d,1,1),p(-hw,hh,d,0,1),0,1,0));
            faces.add(new Face(p(-hw,-hh,d,0,0),p(hw,-hh,d,1,0),p(hw,-hh,-d,1,1),p(-hw,-hh,-d,0,1),0,-1,0));
            faces.add(new Face(p(-hw,hh,-d,0,0),p(-hw,hh,d,1,0),p(-hw,-hh,d,1,1),p(-hw,-hh,-d,0,1),-1,0,0));
            faces.add(new Face(p(hw,hh,d,0,0),p(hw,hh,-d,1,0),p(hw,-hh,-d,1,1),p(hw,-hh,d,0,1),1,0,0));
            return faces;
        }
        // Front/back retain full resolution. Only silhouette geometry is bounded for HD textures.
        int w = Math.min(128, image.getWidth()), h = Math.min(128, image.getHeight());
        boolean[][] solid = new boolean[h][w];
        for (int y=0;y<h;y++) for(int x=0;x<w;x++) solid[y][x]=(image.getRGB(x*image.getWidth()/w,y*image.getHeight()/h)>>>24)>=16;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            if (!solid[y][x]) continue;
            double l=-hw+2*hw*x/w, r=-hw+2*hw*(x+1)/w, top=hh-2*hh*y/h, bottom=hh-2*hh*(y+1)/h;
            double u=(x+.5)/w, v=(y+.5)/h;
            if(x==0||!solid[y][x-1]) faces.add(new Face(p(l,top,-d,u,v),p(l,top,d,u,v),p(l,bottom,d,u,v),p(l,bottom,-d,u,v),-1,0,0));
            if(x==w-1||!solid[y][x+1]) faces.add(new Face(p(r,top,d,u,v),p(r,top,-d,u,v),p(r,bottom,-d,u,v),p(r,bottom,d,u,v),1,0,0));
            if(y==0||!solid[y-1][x]) faces.add(new Face(p(l,top,-d,u,v),p(r,top,-d,u,v),p(r,top,d,u,v),p(l,top,d,u,v),0,1,0));
            if(y==h-1||!solid[y+1][x]) faces.add(new Face(p(l,bottom,d,u,v),p(r,bottom,d,u,v),p(r,bottom,-d,u,v),p(l,bottom,-d,u,v),0,-1,0));
        }
        return faces;
    }
}
