package de.v0idpacks.ui;

import de.v0idpacks.core.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import static de.v0idpacks.ui.UiMotion.Effect.*;

/** Native in-game canvas. Redraws on changes and during bounded UI transitions. */
public final class EditorCanvas {
    // tastemaker: editor/canvas, technical dark, seed 214. See .tastemaker/style-lock.md.
    public static final Color BG = new Color(0x0d1417), SURFACE = new Color(0x181f23), BORDER = new Color(0x242b2f);
    public static final Color TEXT = new Color(0xe6f4fc), MUTED = new Color(0xa8b8c1), ACCENT = new Color(0x29addd);
    private static final Color PRIMARY = new Color(0x0980a6), SECONDARY = new Color(0x0e3241), WHITE = Color.WHITE;
    private static final Color ERROR = new Color(0xffb7a5), CHECKER = new Color(0x20292e);
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13), SMALL = BODY.deriveFont(11f), LABEL = BODY.deriveFont(Font.BOLD, 13f), TITLE = BODY.deriveFont(Font.BOLD, 22f);
    private record Hit(String id, String label, Rectangle box, boolean enabled, Runnable action) {}
    private record FilterChoice(String id, String label, boolean selected, Runnable action) {}
    private record ViewState(EditorSession.Tab tab, String query, String pack, TextureCategory category,
                             ArmorPart armor, ToolType tool, boolean packs, int page, boolean ready) {}
    private final EditorSession session;
    private final Runnable close;
    private final LongSupplier clock;
    private final UiMotion motion;
    private final PreviewRenderer renderer = new PreviewRenderer();
    private final List<Hit> hits = new ArrayList<>();
    private BufferedImage image, previewImage, openingImage;
    private TextureImage renderedTexture;
    private TextureImage lastPreview;
    private ViewState lastView;
    private boolean lastCategoryMenu;
    private boolean animationsEnabled;
    private long frameTime;
    private PreviewRenderer.Mode mode = PreviewRenderer.Mode.AUTO, renderedMode;
    private long renderedRevision = -1;
    private int width = 1120, height = 660, scroll, columns = 4, pageSize = 12;
    private String focus = "", hover = "", nameDraft = "";
    private String previousHover = "", pressed = "";
    private boolean dirty = true, allTextSelected, dragging;
    private boolean allTextures;
    private boolean categoryMenu;
    private int categoryIndex;
    private Rectangle viewport = new Rectangle();
    private double yaw = -.48, pitch = .25, zoom = 1;
    private double renderedYaw, renderedPitch, renderedZoom;

    public EditorCanvas(EditorSession session, Runnable close) { this(session,close,System::nanoTime); }
    EditorCanvas(EditorSession session, Runnable close, LongSupplier clock) {
        this.session=session;this.close=close;this.clock=clock;motion=new UiMotion(clock);
        animationsEnabled=session.ready()&&session.animationsEnabled();
        motion.setEnabled(animationsEnabled);motion.start(OPEN);
    }
    public void resize(int w, int h) {
        if (w != width || h != height) { width = w; height = h; dirty = true; previewImage = null; }
    }
    public int width() { return width; }
    public int height() { return height; }
    public boolean isEditingText() { return focus.equals("search") || focus.equals("name"); }
    public boolean showingPacks() { return session.tab()==EditorSession.Tab.ALL && session.packFilter().isEmpty() && session.categoryFilter()==TextureCategory.ALL && !allTextures; }
    public String narration() {
        String control = hits.stream().filter(h -> h.id.equals(focus)).map(Hit::label).findFirst().orElse("Textur-Bibliothek");
        return "v0idpacks. " + control + ". " + session.status();
    }
    /** null means the GPU texture can be reused this frame. */
    public BufferedImage draw() {
        session.poll();
        boolean enabled=session.ready()&&session.animationsEnabled();
        if(animationsEnabled!=enabled){animationsEnabled=enabled;dirty=true;}
        motion.setEnabled(enabled);
        ViewState view=new ViewState(session.tab(),session.query(),session.packFilter(),session.categoryFilter(),
                session.armorPartFilter(),session.toolTypeFilter(),showingPacks(),scroll,session.ready());
        if(lastView!=null&&!lastView.equals(view))motion.start(CONTENT);
        lastView=view;
        if(lastPreview!=session.preview()) {lastPreview=session.preview();if(lastPreview!=null)motion.start(PREVIEW);}
        if(categoryMenu&&!lastCategoryMenu)motion.start(MENU);
        lastCategoryMenu=categoryMenu;
        frameTime=clock.getAsLong();
        if (!dirty && renderedRevision == session.revision() && !motion.needsFrame()) return null;
        dirty = false; renderedRevision = session.revision();
        if (image == null || image.getWidth() != width || image.getHeight() != height) image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        hits.clear(); fill(g, 0, 0, width, height, BG);
        int sidebar = 144, inspector = 280, contentX = sidebar + 16, contentW = width - contentX - inspector - 28;
        int inspectorX = width - inspector - 12, bottom = height - 42;
        fill(g, 0, 0, sidebar, height, SURFACE); line(g, sidebar, 0, sidebar, height, BORDER);
        fill(g,0,0,sidebar,74,BG);
        Branding.logo(g,56,3,32);
        Branding.wordmark(g,6,31,132);
        nav(g, "all", "Bibliothek", "Packs", EditorSession.Tab.ALL, 80);
        nav(g, "favorites", "Favoriten", Integer.toString(session.favoriteCount()), EditorSession.Tab.FAVORITES, 120);
        nav(g, "selected", "Mein Pack", Integer.toString(session.selectedCount()), EditorSession.Tab.SELECTED, 160);
        text(g, session.packs().size() + " Packs installiert", 14, 236, SMALL, MUTED);
        button(g, "refresh", "Neu einlesen", 12, 248, 120, 30, session.ready() && !session.busy(), false, session::refresh);
        button(g,"animations","Animationen: "+(session.animationsEnabled()?"An":"Aus"),12,288,120,28,session.ready()&&!session.busy(),false,session::toggleAnimations);
        text(g, "Lokal gespeichert", 14, height - 52, LABEL, TEXT);
        text(g, "Favoriten & Auswahl", 14, height - 32, SMALL, MUTED);

        boolean armor=session.tab()==EditorSession.Tab.ALL&&session.categoryFilter()==TextureCategory.ARMOR;
        boolean tools=session.tab()==EditorSession.Tab.ALL&&session.categoryFilter()==TextureCategory.TOOLS;
        String title = switch(session.tab()) { case ALL -> showingPacks()?"Deine Texturepacks":session.categoryFilter()!=TextureCategory.ALL?session.categoryFilter().label():session.packFilter().isEmpty()?"Alle Texturen":session.packName(session.packFilter()); case FAVORITES -> "Deine Favoriten"; case SELECTED -> "Dein eigenes Pack"; };
        if(armor&&session.armorPartFilter()!=ArmorPart.ALL)title+=" · "+session.armorPartFilter().label();
        if(tools&&session.toolTypeFilter()!=ToolType.ALL)title+=" · "+session.toolTypeFilter().label();
        text(g, clip(g,title,contentW,TITLE), contentX, 32, TITLE, TEXT);
        String subtitle=showingPacks()?"Pack öffnen oder nach Kategorie filtern.":session.tab()==EditorSession.Tab.ALL&&session.packFilter().isEmpty()?"Texturen aus allen installierten Packs.":"Auswählen. Ansehen. Zusammenstellen.";
        if(armor&&session.armorPartFilter()!=ArmorPart.ALL)subtitle="Item-Texturen aus allen installierten Packs.";
        if(tools&&session.toolTypeFilter()!=ToolType.ALL)subtitle="Item-Texturen aus allen installierten Packs.";
        text(g, clip(g,subtitle,contentW,SMALL), contentX, 50, SMALL, MUTED);
        button(g, "close", "Schließen", width - 102, 16, 90, 28, true, false, close);
        input(g, "search", showingPacks()?"Packs suchen…":"Texturen suchen…", session.query(), contentX, 64, contentW, 32);
        button(g, "pack", showingPacks()?"Alle Texturen anzeigen":"‹ Zur Pack-Übersicht", contentX, 106, Math.max(154,contentW-94), 28, session.ready(), false, () -> {
            allTextures=showingPacks();session.filter(EditorSession.Tab.ALL,"","");scroll=0;dirty=true;
        });
        int count=showingPacks()?session.visiblePacks().size():session.visible().size();
        text(g, count + (showingPacks()?" Packs":" Treffer"), contentX + contentW - 80, 125, SMALL, MUTED);

        boolean library=session.tab()==EditorSession.Tab.ALL;
        if(library)button(g,"category","Kategorie: "+session.categoryFilter().label()+"  ▾",contentX,148,contentW,28,session.ready(),session.categoryFilter()!=TextureCategory.ALL,()->{
            categoryMenu=!categoryMenu;categoryIndex=session.categoryFilter().ordinal();
            focus=categoryMenu?"category-option:"+categoryIndex:"category";dirty=true;
        });
        if(armor)armorFilters(g,contentX,182,contentW);
        if(tools)toolFilters(g,contentX,182,contentW);
        int gridTop = armor||tools?222:library?190:148, gridBottom = bottom - 36;
        columns = showingPacks()?Math.max(1,contentW/265):Math.max(2, contentW / 116);
        int gap = 10, tileW = (contentW - gap * (columns - 1)) / columns;
        int tileH = showingPacks()?92:122, rows = Math.max(1, (gridBottom - gridTop + gap) / (tileH + gap));
        pageSize = rows * columns;
        scroll = Math.clamp(scroll, 0, Math.max(0, ((count - 1) / pageSize) * pageSize));
        Composite contentComposite=g.getComposite();alpha(g,.35f+.65f*motion.progress(CONTENT,frameTime));
        for (int i = scroll; i < Math.min(count, scroll + pageSize); i++) {
            int local = i - scroll, x = contentX + local % columns * (tileW + gap), y = gridTop + local / columns * (tileH + gap);
            if(showingPacks())packTile(g,session.visiblePacks().get(i),x,y,tileW,tileH);
            else tile(g, session.visible().get(i), x, y, tileW, tileH);
        }
        if (count==0) {
            String empty = !session.ready() ? (session.busy() ? "Bibliothek wird geladen…" : "Bibliothek konnte nicht geöffnet werden")
                    : !session.query().isEmpty() || !session.packFilter().isEmpty() || session.categoryFilter()!=TextureCategory.ALL ? "Keine passenden Texturen"
                    : switch (session.tab()) { case ALL -> "Noch keine Texturen gefunden"; case FAVORITES -> "Hier landen deine Lieblings-Texturen"; case SELECTED -> "Stelle dein erstes Pack zusammen"; };
            text(g, clip(g,showingPacks()&&session.ready()?"Keine passenden Packs gefunden":empty,contentW-16,LABEL), contentX + 8, gridTop + 54, LABEL, TEXT);
            boolean filtered=library&&(!session.query().isEmpty()||session.categoryFilter()!=TextureCategory.ALL);
            String hint = armor ? "Anderes Teil wählen oder Suche leeren." : tools ? "Anderes Werkzeug wählen oder Suche leeren." : filtered ? "Andere Kategorie wählen oder Suche leeren." : library ? "ZIPs oder Pack-Ordner in resourcepacks ablegen." : "Wähle links in der Bibliothek eine Textur aus.";
            text(g, clip(g,hint,contentW-16,SMALL), contentX + 8, gridTop + 78, SMALL, MUTED);
            if (library&&!filtered) text(g, "Danach „Neu einlesen“ wählen.", contentX + 8, gridTop + 100, SMALL, MUTED);
        }
        g.setComposite(contentComposite);
        button(g, "prev", "Zurück", contentX, bottom - 35, 85, 28, scroll > 0, false, () -> { scroll = Math.max(0, scroll - pageSize); dirty = true; });
        int pages = Math.max(1, (count + pageSize - 1) / pageSize);
        text(g, (scroll / pageSize + 1) + " / " + pages, contentX + 102, bottom - 16, SMALL, MUTED);
        button(g, "next", "Weiter", contentX + contentW - 85, bottom - 35, 85, 28, scroll + pageSize < count, false, () -> { scroll += pageSize; dirty = true; });

        inspector(g, inspectorX, 64, inspector, height - 184);
        line(g, sidebar, bottom, width, bottom, BORDER);
        String status = (session.busy() ? "◌  " : session.error() ? "!  " : "✓  ") + session.status();
        text(g, clip(g, status, width - sidebar - 28, SMALL), contentX, bottom + 17, SMALL, session.error() ? ERROR : MUTED);
        text(g, "F: Favorit   ·   Leertaste: Auswahl   ·   Tab: Navigation   ·   Esc: Schließen", contentX, bottom + 33, SMALL, MUTED);
        if(categoryMenu&&library)categoryDropdown(g,contentX,182,Math.min(contentW,400));
        g.dispose();
        float opening=motion.progress(OPEN,frameTime);
        motion.finishFrame(frameTime);
        if(opening>=1)return image;
        if(openingImage==null||openingImage.getWidth()!=width||openingImage.getHeight()!=height)
            openingImage=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D openingGraphics=openingImage.createGraphics();
        // Alpha only: positions, glyphs and hit targets stay on their native pixels.
        openingGraphics.setComposite(AlphaComposite.Src.derive(.2f+.8f*opening));
        openingGraphics.drawImage(image,0,0,null);openingGraphics.dispose();return openingImage;
    }
    private void nav(Graphics2D g, String id, String label, String count, EditorSession.Tab tab, int y) {
        boolean selected = session.tab() == tab;
        Rectangle rect = new Rectangle(8, y, 128, 34);
        fill(g, rect.x, rect.y, rect.width, rect.height, selected ? SECONDARY : SURFACE);
        if (selected) fill(g, 8, y + 7, 3, 20, ACCENT);
        text(g, label, 20, y + 22, LABEL, selected ? TEXT : MUTED);
        if (tab != EditorSession.Tab.ALL) text(g, count, 126 - g.getFontMetrics(SMALL).stringWidth(count), y + 22, SMALL, selected ? ACCENT : MUTED);
        hit(g, new Hit(id, label, rect, true, () -> { session.filter(tab, "", ""); allTextures=false;categoryMenu=false;scroll = 0; dirty = true; }));
    }
    private void chooseCategory(TextureCategory category) {
        session.browseCategory(category);allTextures=true;categoryMenu=false;scroll=0;focus="category";dirty=true;
    }
    private void armorFilters(Graphics2D g,int x,int y,int w) {
        filterChoices(g,x,y,w,Arrays.stream(ArmorPart.values()).map(part->new FilterChoice("armor:"+part.name(),part.label(),session.armorPartFilter()==part,()->{
            session.browseArmorPart(part);allTextures=true;scroll=0;dirty=true;
        })).toList());
    }
    private void toolFilters(Graphics2D g,int x,int y,int w) {
        filterChoices(g,x,y,w,Arrays.stream(ToolType.values()).map(type->new FilterChoice("tool:"+type.name(),type.label(),session.toolTypeFilter()==type,()->{
            session.browseToolType(type);allTextures=true;scroll=0;dirty=true;
        })).toList());
    }
    private void filterChoices(Graphics2D g,int x,int y,int w,List<FilterChoice> choices) {
        int gap=4,required;int[] widths=new int[choices.size()];Font font=BODY;
        // Long category labels stay complete in the compact layout, without scaling the canvas.
        while(true) {
            required=gap*(choices.size()-1);
            for(int i=0;i<choices.size();i++){widths[i]=FormattedText.width(g,choices.get(i).label(),font)+16;required+=widths[i];}
            if(required<=w||font.getSize()<=9)break;
            font=font.deriveFont(font.getSize2D()-1);
        }
        int remaining=Math.max(0,w-required);
        for(int i=0;i<choices.size();i++) {
            FilterChoice choice=choices.get(i);int extra=remaining/(choices.size()-i);remaining-=extra;
            int partWidth=widths[i]+extra;
            button(g,choice.id(),choice.label(),x,y,partWidth,26,session.ready(),choice.selected(),choice.action(),font);
            x+=partWidth+gap;
        }
    }
    private void categoryDropdown(Graphics2D g,int x,int y,int w) {
        Composite composite=g.getComposite();alpha(g,.35f+.65f*motion.progress(MENU,frameTime));
        TextureCategory[] categories=TextureCategory.values();
        // Modal hit targets prevent clicks, shortcuts and scrolling from reaching covered tiles.
        Hit trigger=hits.stream().filter(h->h.id.equals("category")).findFirst().orElseThrow();
        hits.clear();hits.add(trigger);
        int h=28+((categories.length+1)/2)*30+4,colW=(w-24)/2;
        fill(g,x,y,w,h,BG);outline(g,x,y,w,h,ACCENT);
        text(g,"Kategorien aus allen Packs",x+12,y+19,SMALL,MUTED);
        for(int i=0;i<categories.length;i++) {
            TextureCategory category=categories[i];
            button(g,"category-option:"+i,category.label(),x+8+(i%2)*(colW+8),y+28+(i/2)*30,colW,26,true,category==session.categoryFilter(),()->chooseCategory(category));
        }
        g.setComposite(composite);
    }
    private void packTile(Graphics2D g,PackInfo pack,int x,int y,int w,int h) {
        fill(g,x,y,w,h,SURFACE);
        if(pack.icon()!=null)g.drawImage(pack.icon(),x+12,y+14,56,56,null);
        else {fill(g,x+12,y+14,56,56,SECONDARY);for(int row=0;row<3;row++)for(int col=0;col<3;col++)fill(g,x+21+col*13,y+23+row*13,10,10,(row+col)%2==0?ACCENT:BORDER);}
        text(g,clip(g,pack.displayName(),w-92,LABEL),x+80,y+28,LABEL,TEXT);
        text(g,clip(g,pack.description().isBlank()?"Lokales Ressourcenpaket":pack.description(),w-92,SMALL),x+80,y+48,SMALL,MUTED);
        text(g,pack.textureCount()+" Texturen  ›",x+80,y+70,SMALL,MUTED);
        hit(g,new Hit("source:"+pack.id(),MinecraftFormatting.plain(pack.displayName()),new Rectangle(x,y,w,h),true,()->{session.filter(EditorSession.Tab.ALL,"",pack.id());allTextures=false;scroll=0;dirty=true;}));
    }
    private void tile(Graphics2D g, TextureRef ref, int x, int y, int w, int h) {
        boolean focused = Objects.equals(session.focused(), ref);
        fill(g, x, y, w, h, focused ? SECONDARY : SURFACE);
        checker(g, x + 6, y + 6, w - 12, 74);
        BufferedImage thumb = session.thumbnail(ref);
        if (thumb != null) {int size=64;double ratio=(double)thumb.getWidth()/thumb.getHeight();int iw=(int)(size*Math.min(1,ratio)),ih=(int)(size*Math.min(1,1/ratio));g.drawImage(thumb,x+(w-iw)/2,y+10+(64-ih)/2,iw,ih,null);}
        else text(g, session.thumbnailFailed(ref) ? "Nicht lesbar" : "…", x + 12, y + 54, SMALL, MUTED);
        if (session.isFavorite(ref)) text(g, "★", x + 8, y + 23, BODY, ACCENT);
        if (session.isSelected(ref)) { fill(g, x + w - 30, y + 7, 22, 20, PRIMARY); text(g, "✓", x + w - 25, y + 22, BODY, WHITE); }
        text(g, clip(g, ref.name(), w - 16, LABEL), x + 8, y + 99, LABEL, TEXT);
        text(g, clip(g, session.packName(ref.pack()), w - 16, SMALL), x + 8, y + 115, SMALL, MUTED);
        if (focused) outline(g, x, y, w, h, ACCENT);
        hit(g, new Hit("tile:" + ref.key(), ref.name() + " aus " + ref.pack(), new Rectangle(x,y,w,h), true, () -> {
            session.focus(ref); previewImage = null;
            mode = PreviewRenderer.Mode.AUTO;
            yaw = -.48; pitch = .25; zoom = 1; dirty = true;
        }));
    }
    private void inspector(Graphics2D g, int x, int y, int w, int h) {
        fill(g, x, y, w, h, SURFACE);
        int p = 12;
        text(g, "VORSCHAU", x + p, y + 18, SMALL, MUTED);
        TextureRef ref = session.focused();
        text(g, clip(g, ref == null ? "Wähle eine Textur" : ref.name(), w - 24, LABEL), x + p, y + 40, LABEL, TEXT);
        int viewportHeight = Math.clamp(height-390,60,250);
        viewport = new Rectangle(x + p, y + 54, w - 24, viewportHeight);
        checker(g, viewport.x, viewport.y, viewport.width, viewport.height);
        if (session.preview() != null) {
            TextureImage texture = session.preview();
            if (previewImage == null || renderedTexture != texture || renderedMode != mode || yaw != renderedYaw || pitch != renderedPitch || zoom != renderedZoom || previewImage.getWidth() != viewport.width || previewImage.getHeight() != viewport.height) {
                PreviewRenderer.Mode fallback=ref.category().equals("block")?PreviewRenderer.Mode.CUBE:ref.category().equals("item")?PreviewRenderer.Mode.ITEM:PreviewRenderer.Mode.FLAT;
                previewImage = mode==PreviewRenderer.Mode.AUTO && session.previewModel()!=null
                        ? renderer.render(session.previewModel(),viewport.width,viewport.height,yaw,pitch,zoom)
                        : renderer.render(texture.frame(), mode==PreviewRenderer.Mode.AUTO?fallback:mode, viewport.width, viewport.height, yaw, pitch, zoom);
                renderedTexture = texture; renderedMode = mode; renderedYaw = yaw; renderedPitch = pitch; renderedZoom = zoom;
            }
            Graphics2D previewGraphics=(Graphics2D)g.create();
            float arrival=motion.progress(PREVIEW,frameTime);
            alpha(previewGraphics,arrival);previewGraphics.clip(viewport);
            previewGraphics.drawImage(previewImage,viewport.x,viewport.y+Math.round(6*(1-arrival)),null);
            previewGraphics.dispose();
            String resolution = mode==PreviewRenderer.Mode.AUTO&&session.previewModel()!=null?session.previewModel().label():texture.width() + " × " + texture.height() + (texture.animated() ? " · erstes Animationsbild" : " px");
            if(mode==PreviewRenderer.Mode.AUTO && session.previewModel()==null && !texture.animated())resolution+=" · "+(ref.category().equals("block")?"Texturwürfel":ref.category().equals("item")?"3D-Sprite":"2D-Textur");
            if(mode==PreviewRenderer.Mode.AUTO && session.previewModel()==null && !session.modelNote().isEmpty())resolution="Texturansicht · Modell nicht verfügbar";
            text(g, resolution, x + p, viewport.y + viewport.height + 21, SMALL, MUTED);
        } else {
            String label = ref == null ? "Deine nächste Lieblings-Textur?" : session.previewError().isEmpty() ? "Vorschau wird geladen…" : "Vorschau nicht verfügbar";
            if(ref==null&&viewport.height>=180) {
                fill(g,viewport.x,viewport.y,viewport.width,viewport.height,BG);
                int centerX=viewport.x+viewport.width/2,centerY=viewport.y+viewport.height/2;
                Branding.logo(g,centerX-32,centerY-78,64);
                Branding.wordmark(g,centerX-100,centerY-8,200);
                String hint="Textur auswählen und vergleichen";
                text(g,hint,centerX-FormattedText.width(g,hint,SMALL)/2,viewport.y+viewport.height-18,SMALL,MUTED);
            } else text(g, label, viewport.x + 12, viewport.y + viewport.height / 2, SMALL, MUTED);
            if (!session.previewError().isEmpty()) text(g, clip(g, session.previewError(), w - 32, SMALL), x + p, viewport.y + viewport.height + 21, SMALL, ERROR);
        }
        hit(g, new Hit("viewport", "3D-Vorschau. Ziehen oder Pfeiltasten zum Drehen, Mausrad zum Zoomen.", viewport, ref != null, () -> {}));
        int controls = viewport.y + viewport.height + 30;
        String[] modes = { "Auto", "Item", "Würfel", "2D" };
        PreviewRenderer.Mode[] values={PreviewRenderer.Mode.AUTO,PreviewRenderer.Mode.ITEM,PreviewRenderer.Mode.CUBE,PreviewRenderer.Mode.FLAT};
        for (int i = 0; i < 4; i++) {
            PreviewRenderer.Mode next = values[i];
            button(g, "mode" + i, modes[i], x + p + i * 65, controls, 61, 26, ref != null, mode == next, () -> { mode = next; dirty = true; });
        }
        text(g, ref!=null&&ref.path().equals(ModelLoader.CRYSTAL_ITEM)&&session.previewModel()!=null?"3D: Entity-Textur · 2D: Inventar-Icon":"Ziehen: drehen · Mausrad: zoomen", x + p, controls + 44, SMALL, MUTED);
        int buttonsY = controls + 54;
        boolean enabled = ref != null && session.ready() && !session.busy();
        button(g, "favorite", ref != null && session.isFavorite(ref) ? "★  Favorit gespeichert" : "☆  Als Favorit speichern", x + p, buttonsY, w - 24, 28, enabled, false, session::favorite);
        String select = ref != null && session.isSelected(ref) ? "Aus Pack entfernen" : ref != null && session.replacesOther(ref) ? "Vorhandene Textur ersetzen" : "In mein Pack übernehmen";
        button(g, "select", select, x + p, buttonsY + 34, w - 24, 30, enabled, true, session::select);
        if (ref != null && height>=600) {
            text(g, clip(g, ref.path(), w - 24, SMALL), x + p, buttonsY + 82, SMALL, MUTED);
        }
        // Export controls occupy their own panel, above the global status line.
        int footerY = height - 112;
        fill(g, x, footerY, w, 68, BG);
        input(g, "name", "Pack-Name", focus.equals("name") ? nameDraft : session.name(), x, footerY, w - 48, 28);
        button(g, "rename", "OK", x + w - 42, footerY, 42, 28, session.ready() && !session.busy(), false, () -> session.rename(nameDraft.isEmpty() ? session.name() : nameDraft));
        button(g, "export", "Exportieren", x, footerY + 34, 100, 30, session.selectedCount() > 0 && !session.busy(), false, () -> session.export(false));
        button(g, "activate", "Speichern & aktivieren", x + 106, footerY + 34, w - 106, 30, session.selectedCount() > 0 && !session.busy(), true, () -> session.export(true));
    }
    private void input(Graphics2D g, String id, String placeholder, String value, int x, int y, int w, int h) {
        fill(g, x, y, w, h, BG); outline(g, x, y, w, h, focus.equals(id) ? ACCENT : MUTED);
        if (focus.equals(id) && allTextSelected) fill(g, x + 8, y + 5, Math.min(w - 16, FormattedText.width(g,value,BODY) + 3), h - 10, SECONDARY);
        String visible = clip(g, value.isEmpty() ? placeholder : value, w - 22, BODY);
        text(g, visible, x + 10, y + h / 2 + 5, BODY, value.isEmpty() ? MUTED : TEXT);
        if (focus.equals(id) && !allTextSelected) { int caret = x + 11 + FormattedText.width(g,visible,BODY); line(g, caret, y + 9, caret, y + h - 9, ACCENT); }
        hit(g, new Hit(id, placeholder, new Rectangle(x,y,w,h), session.ready(), () -> { if (id.equals("name")) nameDraft = session.name(); allTextSelected = false; }));
    }
    private void button(Graphics2D g, String id, String label, int x, int y, int w, int h, boolean enabled, boolean primary, Runnable action) {
        button(g,id,label,x,y,w,h,enabled,primary,action,BODY);
    }
    private void button(Graphics2D g, String id, String label, int x, int y, int w, int h, boolean enabled, boolean primary, Runnable action,Font font) {
        Color fill = primary && enabled ? PRIMARY : SURFACE;
        fill(g, x, y, w, h, fill);
        if (!primary) outline(g, x, y, w, h, BORDER);
        String displayed = clip(g, label, w - 16, font);
        text(g, displayed, x + (w - FormattedText.width(g,displayed,font)) / 2, y + h / 2 + 5, font, enabled ? (primary ? WHITE : TEXT) : MUTED);
        hit(g, new Hit(id, label, new Rectangle(x,y,w,h), enabled, action));
    }
    private void hit(Graphics2D g, Hit hit) {
        hits.add(hit);
        if(!hit.enabled)return;
        float progress=motion.progress(HOVER,frameTime);
        float hoverAlpha=hit.id.equals(hover)?progress:hit.id.equals(previousHover)?1-progress:0;
        if(hoverAlpha>0) {
            Graphics2D effect=(Graphics2D)g.create();alpha(effect,hoverAlpha);
            outline(effect,hit.box.x,hit.box.y,hit.box.width,hit.box.height,MUTED);effect.dispose();
        }
        if(hit.id.equals(pressed)) {
            Graphics2D effect=(Graphics2D)g.create();alpha(effect,.16f*(1-motion.progress(PRESS,frameTime)));
            fill(effect,hit.box.x,hit.box.y,hit.box.width,hit.box.height,ACCENT);effect.dispose();
        }
        if(hit.id.equals(focus))outline(g,hit.box.x,hit.box.y,hit.box.width,hit.box.height,ACCENT);
    }
    public void mouseMove(int x, int y) {
        String next = hits.stream().filter(h -> h.enabled && h.box.contains(x,y)).map(Hit::id).reduce((a,b) -> b).orElse("");
        if (!hover.equals(next)) { previousHover=hover;hover=next;motion.start(HOVER);dirty=true; }
    }
    public boolean click(int x, int y, int button) {
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.box.contains(x,y)) continue;
            if (!hit.enabled) return true;
            focus = hit.id; allTextSelected = false; activate(hit); dirty = true;
            dragging = focus.equals("viewport"); return true;
        }
        focus = categoryMenu ? "category" : "";categoryMenu=false; dirty = true; return true;
    }
    public boolean drag(double dx, double dy) {
        if (!dragging || mode == PreviewRenderer.Mode.FLAT) return false;
        yaw += dx * .012; pitch = Math.clamp(pitch + dy * .012, -1.5, 1.5); dirty = true; return true;
    }
    public void release() { dragging = false; }
    public boolean scroll(int x, int y, double amount) {
        if(categoryMenu){categoryIndex=Math.floorMod(categoryIndex-(int)Math.signum(amount),TextureCategory.values().length);focus="category-option:"+categoryIndex;dirty=true;return true;}
        if (viewport.contains(x,y)) { zoom = Math.clamp(zoom + amount * .12, .4, 2.5); dirty = true; return true; }
        int count=showingPacks()?session.visiblePacks().size():session.visible().size();
        scroll = Math.clamp(scroll - (int)Math.signum(amount) * pageSize, 0, Math.max(0, ((count - 1) / pageSize) * pageSize));
        dirty = true; return true;
    }
    public boolean type(int codepoint) {
        if (!focus.equals("search") && !focus.equals("name")) return false;
        if (!Character.isValidCodePoint(codepoint) || Character.isISOControl(codepoint)) return false;
        String value = allTextSelected ? "" : currentText();
        setText(value + new String(Character.toChars(codepoint))); allTextSelected = false; return true;
    }
    public boolean key(int key, boolean shift, boolean control, String clipboard, Consumer<String> copy) {
        if(categoryMenu) {
            if(key==256){categoryMenu=false;focus="category";dirty=true;return true;}
            if(key==257||key==335||key==32){chooseCategory(TextureCategory.values()[categoryIndex]);return true;}
            int step=switch(key){case 258->shift?-1:1;case 263->-1;case 262->1;case 265->-2;case 264->2;default->0;};
            categoryIndex=Math.floorMod(categoryIndex+step,TextureCategory.values().length);focus="category-option:"+categoryIndex;dirty=true;return true;
        }
        if (key == 256) { close.run(); return true; }
        if (key == 258) {
            List<Hit> enabled = hits.stream().filter(Hit::enabled).toList(); if (enabled.isEmpty()) return true;
            int index = -1; for(int i=0;i<enabled.size();i++) if(enabled.get(i).id.equals(focus)) index=i;
            focus = enabled.get(Math.floorMod(index + (shift ? -1 : 1), enabled.size())).id;
            if (focus.equals("name")) nameDraft = session.name();
            dirty = true; return true;
        }
        if (focus.equals("search") || focus.equals("name")) {
            String value = currentText();
            if (control && key == 65) { allTextSelected = true; dirty = true; return true; }
            if (control && (key == 67 || key == 88)) { copy.accept(value); if (key == 88) setText(""); return true; }
            if (control && key == 86) { setText((allTextSelected ? "" : value) + clipboard.replaceAll("[\\p{Cntrl}]", "")); allTextSelected = false; return true; }
            if (key == 259 || key == 261) {
                if (allTextSelected) setText("");
                else if (!value.isEmpty()) setText(value.substring(0, value.offsetByCodePoints(value.length(), -1)));
                allTextSelected = false; return true;
            }
            if (key == 257 || key == 335) { if (focus.equals("name")) session.rename(nameDraft); focus = ""; dirty = true; return true; }
            return false;
        }
        if (focus.equals("viewport")) {
            if (key == 263 || key == 262) yaw += (key == 263 ? -.12 : .12);
            if (key == 265 || key == 264) pitch = Math.clamp(pitch + (key == 265 ? -.12 : .12), -1.5, 1.5);
            if (key == 61 || key == 45) zoom = Math.clamp(zoom + (key == 61 ? .1 : -.1), .4, 2.5);
            dirty = true;
        }
        if (key == 70) { session.favorite(); return true; }
        if (key == 32) { session.select(); return true; }
        if (key == 257 || key == 335) {
            hits.stream().filter(h -> h.enabled && h.id.equals(focus)).findFirst().ifPresent(this::activate); dirty = true; return true;
        }
        return false;
    }
    private String currentText() { return focus.equals("search") ? session.query() : nameDraft; }
    private void activate(Hit hit){pressed=hit.id;motion.start(PRESS);hit.action.run();}
    private static void alpha(Graphics2D g,float opacity) {
        float current=g.getComposite() instanceof AlphaComposite composite?composite.getAlpha():1;
        g.setComposite(AlphaComposite.SrcOver.derive(Math.clamp(current*opacity,0,1)));
    }
    private void setText(String value) {
        int max = focus.equals("name") ? 64 : 160;
        value = value.substring(0, Math.min(value.length(), max));
        if (focus.equals("search")) { session.search(value); scroll = 0; }
        else nameDraft = value;
        dirty = true;
    }
    private static void checker(Graphics2D g, int x, int y, int w, int h) {
        fill(g,x,y,w,h,SURFACE);
        for(int iy=0;iy<h;iy+=12) for(int ix=0;ix<w;ix+=12) if((ix/12+iy/12)%2==0) fill(g,x+ix,y+iy,Math.min(12,w-ix),Math.min(12,h-iy),CHECKER);
    }
    private static void fill(Graphics2D g,int x,int y,int w,int h,Color c) { g.setColor(c); g.fillRect(x,y,w,h); }
    private static void line(Graphics2D g,int x,int y,int ex,int ey,Color c) { g.setColor(c); g.drawLine(x,y,ex,ey); }
    private static void outline(Graphics2D g,int x,int y,int w,int h,Color c) { g.setColor(c); g.drawRect(x,y,w-1,h-1); }
    private static void text(Graphics2D g,String s,int x,int y,Font f,Color c) { FormattedText.draw(g,s,x,y,f,c); }
    private static String clip(Graphics2D g,String s,int w,Font f) {
        return FormattedText.clip(g,s,w,f);
    }
}
