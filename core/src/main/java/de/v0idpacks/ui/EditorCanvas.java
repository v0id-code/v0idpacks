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
    // tastemaker: technical dark, seed 214; full-width library, top tabs, on-demand preview.
    public static final Color BG = new Color(0x0d1417), SURFACE = new Color(0x181f23), BORDER = new Color(0x242b2f);
    public static final Color TEXT = new Color(0xe6f4fc), MUTED = new Color(0xa8b8c1), ACCENT = new Color(0x29addd);
    private static final Color PRIMARY = new Color(0x0980a6), SECONDARY = new Color(0x0e3241), WHITE = Color.WHITE;
    private static final Color ERROR = new Color(0xffb7a5), CHECKER = new Color(0x20292e);
    private static final int CONTROL_RADIUS = 10, PANEL_RADIUS = 16;
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13), SMALL = BODY.deriveFont(11f), LABEL = BODY.deriveFont(Font.BOLD, 13f), TITLE = BODY.deriveFont(Font.BOLD, 22f);
    private record Hit(String id, String label, Rectangle box, boolean enabled, Runnable action) {}
    private record FilterChoice(String id, String label, boolean selected, Runnable action) {}
    private record ViewState(EditorSession.Tab tab, String query, String pack, TextureCategory category,
                             ArmorPart armor, ToolType tool, boolean packs, int page, boolean ready) {}
    private final EditorSession session;
    private final Runnable close;
    private Runnable chooseLogo = () -> {};
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
    private boolean nameEdited;
    private boolean projectsMenu;
    private boolean identityMenu;
    private boolean identitySaving;
    private boolean previewMenu;
    private boolean lastPreviewMenu;
    private String previewReturnFocus = "";
    private int projectPage;
    private String editingProject = "";
    private int categoryIndex;
    private Rectangle viewport = new Rectangle();
    private Rectangle previewDialogBounds=new Rectangle();
    private double yaw = -.48, pitch = .25, zoom = 1;
    private double renderedYaw, renderedPitch, renderedZoom;

    public EditorCanvas(EditorSession session, Runnable close) { this(session,close,System::nanoTime); }
    public EditorCanvas(EditorSession session, Runnable close, Runnable chooseLogo) {
        this(session, close); this.chooseLogo = chooseLogo;
    }
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
    // Package-local geometry lets interaction tests follow controls through layout changes.
    Rectangle controlBounds(String id) {
        return hits.stream().filter(h -> h.id.equals(id)).map(h -> new Rectangle(h.box)).findFirst().orElseThrow();
    }
    String firstControl(String prefix) {
        return hits.stream().map(Hit::id).filter(id->id.startsWith(prefix)).findFirst().orElseThrow();
    }
    public boolean isEditingText() { return focus.equals("search") || focus.equals("name"); }
    public boolean showingPacks() { return session.tab()==EditorSession.Tab.ALL && session.packFilter().isEmpty() && session.categoryFilter()==TextureCategory.ALL && !allTextures; }
    public String narration() {
        String control = hits.stream().filter(h -> h.id.equals(focus)).map(Hit::label).findFirst().orElse("Textur-Bibliothek");
        return "v0idpacks. " + control + ". " + session.status();
    }
    /** null means the GPU texture can be reused this frame. */
    public BufferedImage draw() {
        session.poll();
        if(identitySaving&&!session.busy()) {
            identitySaving=false;
            if(!session.error()){identityMenu=false;focus="identity";}
            dirty=true;
        }
        if (session.ready() && !session.busy() && !editingProject.equals(session.projectId())) {
            editingProject = session.projectId(); nameEdited = false; nameDraft = session.name();
            projectsMenu = false; identityMenu = false; previewMenu=false; focus = ""; scroll = 0; dirty = true;
        }
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
        if(previewMenu&&!lastPreviewMenu)motion.start(MENU);
        lastPreviewMenu=previewMenu;
        if(previewMenu&&session.focused()==null)closePreview();
        frameTime=clock.getAsLong();
        if (!dirty && renderedRevision == session.revision() && !motion.needsFrame()) return null;
        dirty = false; renderedRevision = session.revision();
        if (image == null || image.getWidth() != width || image.getHeight() != height) image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        hits.clear(); fill(g, 0, 0, width, height, BG);
        int contentX=20,contentW=width-40,bottom=height-92;
        fill(g,0,0,width,56,SURFACE);line(g,0,55,width,55,BORDER);
        Branding.header(g,20,28,22);
        nav(g,"all","Bibliothek",EditorSession.Tab.ALL,184);
        nav(g,"favorites","Favoriten",EditorSession.Tab.FAVORITES,318);
        nav(g,"selected","Mein Pack",EditorSession.Tab.SELECTED,452);
        button(g,"close","Schließen",width-110,14,90,28,true,false,close);

        boolean armor=session.tab()==EditorSession.Tab.ALL&&session.categoryFilter()==TextureCategory.ARMOR;
        boolean tools=session.tab()==EditorSession.Tab.ALL&&session.categoryFilter()==TextureCategory.TOOLS;
        String title = switch(session.tab()) { case ALL -> showingPacks()?"Deine Texturepacks":session.categoryFilter()!=TextureCategory.ALL?session.categoryFilter().label():session.packFilter().isEmpty()?"Alle Texturen":session.packName(session.packFilter()); case FAVORITES -> "Deine Favoriten"; case SELECTED -> session.name(); };
        if(armor&&session.armorPartFilter()!=ArmorPart.ALL)title+=" · "+session.armorPartFilter().label();
        if(tools&&session.toolTypeFilter()!=ToolType.ALL)title+=" · "+session.toolTypeFilter().label();
        int titleX=showingPacks()?contentX:contentX+128;
        text(g,clip(g,title,contentW-(titleX-contentX)-96,TITLE),titleX,86,TITLE,TEXT);
        button(g,"pack",showingPacks()?"Alle Texturen":"‹ Alle Packs",showingPacks()?width-230:contentX,66,114,28,session.ready(),false,()->{
            allTextures=showingPacks();session.filter(EditorSession.Tab.ALL,"","");scroll=0;dirty=true;
        });
        int count=showingPacks()?session.visiblePacks().size():session.visible().size();
        String resultCount=count + (showingPacks()?" Packs":" Treffer");
        text(g,resultCount,contentX+contentW-FormattedText.width(g,resultCount,SMALL),84,SMALL,MUTED);

        boolean library=session.tab()==EditorSession.Tab.ALL;
        int categoryX=width-396,searchWidth=library?categoryX-contentX-8:contentW-192;
        input(g,"search",showingPacks()?"Packs suchen…":"Texturen suchen…",session.query(),contentX,100,searchWidth,32);
        button(g,"refresh","resourcepacks reloaden",width-204,100,184,32,session.ready()&&!session.busy(),false,session::refresh);
        if(library)button(g,"category",session.categoryFilter().label(),categoryX,100,184,32,session.ready(),false,()->{
            categoryMenu=!categoryMenu;categoryIndex=session.categoryFilter().ordinal();
            focus=categoryMenu?"category-option:"+categoryIndex:"category";dirty=true;
        });
        if(armor)armorFilters(g,contentX,144,Math.min(contentW,560));
        if(tools)toolFilters(g,contentX,144,Math.min(contentW,560));
        int gridTop = armor||tools?184:146, gridBottom = bottom - 42;
        columns = showingPacks()?Math.max(1,contentW/265):Math.max(2, contentW / 128);
        int gap = 10, tileW = (contentW - gap * (columns - 1)) / columns;
        int tileH=showingPacks()?92:height<520?112:122,rows=Math.max(1,(gridBottom-gridTop+gap)/(tileH+gap));
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
            String hint = armor ? "Anderes Teil wählen oder Suche leeren." : tools ? "Anderes Werkzeug wählen oder Suche leeren." : filtered ? "Andere Kategorie wählen oder Suche leeren." : library ? "ZIPs oder Pack-Ordner in resourcepacks ablegen." : "Wähle im Tab Bibliothek eine Textur aus.";
            text(g, clip(g,hint,contentW-16,SMALL), contentX + 8, gridTop + 78, SMALL, MUTED);
            if (library&&!filtered) text(g, "Danach „resourcepacks reloaden“ wählen.", contentX + 8, gridTop + 100, SMALL, MUTED);
        }
        g.setComposite(contentComposite);
        button(g, "prev", "Zurück", contentX, bottom - 35, 85, 28, scroll > 0, false, () -> { scroll = Math.max(0, scroll - pageSize); dirty = true; });
        int pages = Math.max(1, (count + pageSize - 1) / pageSize);
        text(g, (scroll / pageSize + 1) + " / " + pages, contentX + 102, bottom - 16, SMALL, MUTED);
        button(g, "next", "Weiter", contentX + contentW - 85, bottom - 35, 85, 28, scroll + pageSize < count, false, () -> { scroll += pageSize; dirty = true; });

        viewport=new Rectangle();
        projectBar(g,contentX,bottom,contentW);
        String status = (session.busy() ? "◌  " : session.error() ? "!  " : "✓  ") + session.status();
        text(g, clip(g, status, contentW-156, SMALL), contentX, height-9, SMALL, session.error() ? ERROR : MUTED);
        button(g,"animations","Animationen: "+(session.animationsEnabled()?"An":"Aus"),width-152,height-27,132,24,session.ready()&&!session.busy(),false,session::toggleAnimations);
        if(categoryMenu&&library)categoryDropdown(g,categoryX-216,140,400);
        if(previewMenu)textureDialog(g);
        if(projectsMenu)projectDialog(g);
        if(identityMenu)identityDialog(g);
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
    private int projectPageSize() { return Math.max(1, (Math.min(height - 48, 440) - 160) / 48); }
    private void projectBar(Graphics2D g,int x,int y,int w) {
        round(g,x,y,w,64,PANEL_RADIUS,SURFACE);
        int iconX=x+12, iconY=y+12;
        round(g,iconX,iconY,40,40,CONTROL_RADIUS,BG);
        if(session.logo()!=null)g.drawImage(session.logo(),iconX+4,iconY+4,32,32,null);
        else Branding.logo(g,iconX+6,iconY+6,28);
        int actionsX=x+w-292, titleWidth=Math.max(40,actionsX-x-264);
        text(g,clip(g,draftName(),titleWidth,LABEL),x+64,y+26,LABEL,TEXT);
        text(g,session.selectedCount()+" Texturen",x+64,y+45,SMALL,MUTED);
        button(g,"projects","Meine Packs",actionsX-188,y+16,98,32,session.ready()&&!session.busy(),false,()->{
            projectsMenu=true;categoryMenu=false;projectPage=0;focus="new-project";dirty=true;
        });
        button(g,"identity","Anpassen",actionsX-82,y+16,74,32,session.ready()&&!session.busy(),false,()->{
            identityMenu=true;categoryMenu=false;focus="name";allTextSelected=false;dirty=true;
            if(!nameEdited)nameDraft=session.name();
        });
        boolean enabled=session.selectedCount()>0&&!session.busy();
        button(g,"export","Exportieren",actionsX,y+16,108,32,enabled,false,()->session.export(false,draftName()));
        button(g,"activate","Speichern & aktivieren",actionsX+116,y+16,164,32,enabled,true,()->session.export(true,draftName()));
    }
    private void dismissIdentity() {
        if(session.busy())return;
        session.rename(draftName());
        identitySaving=session.busy();dirty=true;
    }
    private void identityDialog(Graphics2D g) {
        fill(g,0,0,width,height,new Color(0,0,0,175));
        int w=408,h=304,x=(width-w)/2,y=(height-h)/2;
        round(g,x,y,w,h,PANEL_RADIUS,SURFACE);
        hits.clear();
        text(g,"Dein Pack",x+24,y+36,TITLE,TEXT);
        text(g,"Name und Logo für die Ressourcenpaket-Liste.",x+24,y+58,SMALL,MUTED);
        text(g,"Name im Spiel",x+24,y+92,LABEL,TEXT);
        input(g,"name","Pack-Name",draftName(),x+24,y+104,w-48,34);
        round(g,x+24,y+154,64,64,CONTROL_RADIUS,BG);
        if(session.logo()!=null)g.drawImage(session.logo(),x+28,y+158,56,56,null);
        else Branding.logo(g,x+36,y+166,40);
        button(g,"logo",session.hasLogo()?"Logo ändern…":"PNG-Logo wählen…",x+104,y+154,w-128,30,session.ready()&&!session.busy(),false,chooseLogo);
        if(session.hasLogo())button(g,"remove-logo","Logo entfernen",x+104,y+190,w-128,26,!session.busy(),false,session::removeLogo);
        else text(g,"Quadratisches PNG",x+104,y+207,SMALL,MUTED);
        if(session.error())text(g,clip(g,session.status(),w-48,SMALL),x+24,y+241,SMALL,ERROR);
        button(g,"identity-close","Fertig",x+w-124,y+h-48,100,32,!session.busy(),true,this::dismissIdentity);
        text(g,"Änderungen werden lokal gespeichert.",x+24,y+h-27,SMALL,MUTED);
    }
    private void projectDialog(Graphics2D g) {
        fill(g, 0, 0, width, height, new Color(0,0,0,175));
        int w = Math.min(480, width - 48), h = Math.min(height - 48, 440), x = (width-w)/2, y = (height-h)/2;
        round(g,x,y,w,h,PANEL_RADIUS,SURFACE);
        hits.clear(); // Modal controls exclusively own mouse and keyboard focus.
        text(g,"Meine Packs",x+20,y+32,TITLE,TEXT);
        text(g,"Ein Pack wählen, um es weiterzubearbeiten.",x+20,y+54,SMALL,MUTED);
        var projects = new ArrayList<>(session.projects().entrySet());
        int size = projectPageSize(), pages = Math.max(1,(projects.size()+size-1)/size);
        projectPage = Math.clamp(projectPage,0,pages-1);
        for (int i=projectPage*size;i<Math.min(projects.size(),(projectPage+1)*size);i++) {
            var entry=projects.get(i); var project=entry.getValue(); int rowY=y+70+(i%size)*48;
            boolean selected=entry.getKey().equals(session.projectId());
            round(g,x+20,rowY,w-40,42,CONTROL_RADIUS,selected?SECONDARY:BG);
            text(g,clip(g,project.name(),w-160,LABEL),x+30,rowY+17,LABEL,TEXT);
            text(g,project.selected().size()+" Texturen"+(project.logo().isEmpty()?"":" · mit Logo"),x+30,rowY+33,SMALL,MUTED);
            text(g,selected?"Geöffnet":"Bearbeiten",x+w-104,rowY+25,SMALL,selected?ACCENT:MUTED);
            hit(g,new Hit("project:"+entry.getKey(),project.name()+" bearbeiten",new Rectangle(x+20,rowY,w-40,42),!session.busy(),()->{
                if (selected) { projectsMenu=false;focus="projects";dirty=true; }
                else session.switchProject(entry.getKey(),draftName());
            }));
        }
        int navigationY=y+h-86;
        button(g,"projects-prev","‹",x+20,navigationY,32,24,projectPage>0&&!session.busy(),false,()->{projectPage--;dirty=true;});
        text(g,(projectPage+1)+" / "+pages,x+62,navigationY+17,SMALL,MUTED);
        button(g,"projects-next","›",x+110,navigationY,32,24,projectPage+1<pages&&!session.busy(),false,()->{projectPage++;dirty=true;});
        text(g,clip(g,session.error()?session.status():"Name und Auswahl bleiben beim Wechsel erhalten.",w-40,SMALL),x+20,y+h-48,SMALL,session.error()?ERROR:MUTED);
        button(g,"new-project","Neues Pack",x+20,y+h-38,144,26,!session.busy(),true,()->session.createProject(draftName()));
        button(g,"projects-close","Zurück",x+w-110,y+h-38,90,26,!session.busy(),false,()->{projectsMenu=false;focus="projects";dirty=true;});
    }
    private void nav(Graphics2D g,String id,String label,EditorSession.Tab tab,int x) {
        boolean selected = session.tab() == tab;
        int y=12;Rectangle rect=new Rectangle(x,y,128,34);
        round(g, rect.x, rect.y, rect.width, rect.height, CONTROL_RADIUS, selected ? SECONDARY : SURFACE);
        if(selected)round(g,x+12,y+32,104,2,2,ACCENT);
        UiIcons.draw(g,switch(tab){case ALL->"grid";case FAVORITES->"star";case SELECTED->"package";},x+10,y+9,selected?ACCENT:MUTED);
        text(g,label,x+34,y+22,LABEL,selected?TEXT:MUTED);
        String count=Integer.toString(tab==EditorSession.Tab.FAVORITES?session.favoriteCount():session.selectedCount());
        if(tab!=EditorSession.Tab.ALL)text(g,count,x+120-g.getFontMetrics(SMALL).stringWidth(count),y+22,SMALL,selected?ACCENT:MUTED);
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
        round(g,x,y,w,h,CONTROL_RADIUS,BG);roundOutline(g,x,y,w,h,CONTROL_RADIUS,ACCENT);
        text(g,"Kategorien aus allen Packs",x+12,y+19,SMALL,MUTED);
        for(int i=0;i<categories.length;i++) {
            TextureCategory category=categories[i];
            button(g,"category-option:"+i,category.label(),x+8+(i%2)*(colW+8),y+28+(i/2)*30,colW,26,true,category==session.categoryFilter(),()->chooseCategory(category));
        }
        g.setComposite(composite);
    }
    private void packTile(Graphics2D g,PackInfo pack,int x,int y,int w,int h) {
        round(g,x,y,w,h,PANEL_RADIUS,SURFACE);
        if(pack.icon()!=null)g.drawImage(pack.icon(),x+12,y+14,56,56,null);
        else {fill(g,x+12,y+14,56,56,SECONDARY);for(int row=0;row<3;row++)for(int col=0;col<3;col++)fill(g,x+21+col*13,y+23+row*13,10,10,(row+col)%2==0?ACCENT:BORDER);}
        text(g,clip(g,pack.displayName(),w-92,LABEL),x+80,y+28,LABEL,TEXT);
        text(g,clip(g,pack.description().isBlank()?"Lokales Ressourcenpaket":pack.description(),w-92,SMALL),x+80,y+48,SMALL,MUTED);
        text(g,pack.textureCount()+" Texturen  ›",x+80,y+70,SMALL,MUTED);
        hit(g,new Hit("source:"+pack.id(),MinecraftFormatting.plain(pack.displayName()),new Rectangle(x,y,w,h),true,()->{session.filter(EditorSession.Tab.ALL,"",pack.id());allTextures=false;scroll=0;dirty=true;}));
    }
    private void tile(Graphics2D g, TextureRef ref, int x, int y, int w, int h) {
        boolean focused = Objects.equals(session.focused(), ref);
        round(g, x, y, w, h, PANEL_RADIUS, focused ? SECONDARY : SURFACE);
        int thumbHeight=h-48;
        round(g,x+6,y+6,w-12,thumbHeight,CONTROL_RADIUS,BG);
        BufferedImage thumb = session.thumbnail(ref);
        if (thumb != null) {int size=Math.min(64,thumbHeight-8);double ratio=(double)thumb.getWidth()/thumb.getHeight();int iw=(int)(size*Math.min(1,ratio)),ih=(int)(size*Math.min(1,1/ratio));g.drawImage(thumb,x+(w-iw)/2,y+6+(thumbHeight-ih)/2,iw,ih,null);}
        else text(g, session.thumbnailFailed(ref) ? "Nicht lesbar" : "…", x + 12, y + 54, SMALL, MUTED);
        if (session.isFavorite(ref)) UiIcons.draw(g,"star",x+8,y+9,ACCENT);
        if (session.isSelected(ref)) { round(g, x + w - 28, y + 7, 20, 20, CONTROL_RADIUS,PRIMARY); UiIcons.draw(g,"check",x+w-26,y+9,WHITE); }
        text(g, clip(g, ref.name(), w - 16, LABEL), x + 8, y+h-23, LABEL, TEXT);
        text(g, clip(g, session.packName(ref.pack()), w - 16, SMALL), x + 8, y+h-7, SMALL, MUTED);
        if (focused) roundOutline(g, x, y, w, h, PANEL_RADIUS, ACCENT);
        hit(g, new Hit("tile:" + ref.key(), ref.name() + " aus " + ref.pack(), new Rectangle(x,y,w,h), true, () -> {
            openPreview(ref);
        }));
    }
    private void openPreview(TextureRef ref) {
        session.focus(ref);previewImage=null;previewMenu=true;categoryMenu=false;
        int w=Math.min(900,width-48),h=Math.min(560,height-48);
        previewDialogBounds=new Rectangle((width-w)/2,(height-h)/2,w,h);
        hits.clear();viewport=new Rectangle();
        previewReturnFocus="tile:"+ref.key();focus="viewport";dragging=false;
        mode=PreviewRenderer.Mode.AUTO;yaw=-.48;pitch=.25;zoom=1;dirty=true;
    }
    private void closePreview() {
        previewMenu=false;dragging=false;focus=previewReturnFocus;viewport=new Rectangle();hits.clear();dirty=true;
    }
    private void textureDialog(Graphics2D g) {
        TextureRef ref=session.focused();if(ref==null)return;
        Composite composite=g.getComposite();alpha(g,.35f+.65f*motion.progress(MENU,frameTime));
        fill(g,0,0,width,height,new Color(0,0,0,190));
        int w=Math.min(900,width-48),h=Math.min(560,height-48),x=(width-w)/2,y=(height-h)/2;
        previewDialogBounds=new Rectangle(x,y,w,h);
        int controlsX=x+w-280,controlsW=256;
        round(g, x, y, w, h, PANEL_RADIUS,SURFACE);
        hits.clear();
        text(g,clip(g,ref.name(),w-160,TITLE),x+24,y+34,TITLE,TEXT);
        text(g,clip(g,session.packName(ref.pack()),w-160,SMALL),x+24,y+55,SMALL,MUTED);
        button(g,"preview-close","Zurück",x+w-110,y+20,86,28,true,false,this::closePreview);
        viewport=new Rectangle(x+24,y+76,w-328,h-100);
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
            text(g,clip(g,resolution,controlsW,SMALL),controlsX,y+156,SMALL,MUTED);
        } else {
            String label=session.previewError().isEmpty()?"Vorschau wird geladen…":"Vorschau nicht verfügbar";
            text(g,clip(g,label,viewport.width-24,SMALL),viewport.x+12,viewport.y+viewport.height/2,SMALL,MUTED);
            if (!session.previewError().isEmpty()) text(g, clip(g, session.previewError(), controlsW, SMALL), controlsX, y+156, SMALL, ERROR);
        }
        hit(g,new Hit("viewport","3D-Vorschau. Ziehen oder Pfeiltasten zum Drehen, Mausrad zum Zoomen.",viewport,true,()->{}));
        text(g,"Darstellung",controlsX,y+92,LABEL,TEXT);
        int controls=y+104;
        String[] modes = { "Auto", "Item", "Würfel", "2D" };
        PreviewRenderer.Mode[] values={PreviewRenderer.Mode.AUTO,PreviewRenderer.Mode.ITEM,PreviewRenderer.Mode.CUBE,PreviewRenderer.Mode.FLAT};
        for (int i = 0; i < 4; i++) {
            PreviewRenderer.Mode next = values[i];
            int modeW=controlsW/4;
            button(g,"mode"+i,modes[i],controlsX+i*modeW,controls,modeW-3,26,true,mode==next,()->{mode=next;dirty=true;});
        }
        text(g,"Quell-Pack",controlsX,y+190,LABEL,TEXT);
        text(g,clip(g,session.packName(ref.pack()),controlsW,BODY),controlsX,y+212,BODY,TEXT);
        text(g,clip(g,ref.path(),controlsW,SMALL),controlsX,y+234,SMALL,MUTED);
        text(g,ref.path().equals(ModelLoader.CRYSTAL_ITEM)&&session.previewModel()!=null?"3D: Entity · 2D: Inventar-Icon":"Ziehen: drehen · Mausrad: zoomen",controlsX,y+h-134,SMALL,MUTED);
        boolean enabled=session.ready()&&!session.busy();
        button(g,"favorite",session.isFavorite(ref)?"Favorit gespeichert":"Als Favorit speichern",controlsX,y+h-112,controlsW,30,enabled,false,session::favorite);
        String select=session.isSelected(ref)?"Aus Pack entfernen":session.replacesOther(ref)?"Vorhandene Textur ersetzen":"In mein Pack übernehmen";
        button(g,"select",select,controlsX,y+h-72,controlsW,36,enabled,!session.isSelected(ref),session::select);
        if(session.error())text(g,clip(g,session.status(),w-48,SMALL),x+24,y+h-12,SMALL,ERROR);
        g.setComposite(composite);
    }
    private void input(Graphics2D g, String id, String placeholder, String value, int x, int y, int w, int h) {
        round(g, x, y, w, h, CONTROL_RADIUS,BG);
        roundOutline(g,x,y,w,h,CONTROL_RADIUS,focus.equals(id)?ACCENT:BORDER);
        int inset=id.equals("search")?34:10;
        if (focus.equals(id) && allTextSelected) fill(g, x + inset, y + 5, Math.min(w - inset - 8, FormattedText.width(g,value,BODY) + 3), h - 10, SECONDARY);
        if(id.equals("search"))UiIcons.draw(g,"search",x+10,y+(h-16)/2,MUTED);
        String visible = clip(g, value.isEmpty() ? placeholder : value, w - inset - 12, BODY);
        text(g, visible, x + inset, y + h / 2 + 5, BODY, value.isEmpty() ? MUTED : TEXT);
        if (focus.equals(id) && !allTextSelected) { int caret = x + inset + 1 + (value.isEmpty()?0:FormattedText.width(g,visible,BODY)); line(g, caret, y + 9, caret, y + h - 9, ACCENT); }
        hit(g, new Hit(id, placeholder, new Rectangle(x,y,w,h), session.ready()&&!identitySaving, () -> { if (id.equals("name") && !nameEdited) nameDraft = session.name(); allTextSelected = false; }));
    }
    private void button(Graphics2D g, String id, String label, int x, int y, int w, int h, boolean enabled, boolean primary, Runnable action) {
        button(g,id,label,x,y,w,h,enabled,primary,action,BODY);
    }
    private void button(Graphics2D g, String id, String label, int x, int y, int w, int h, boolean enabled, boolean primary, Runnable action,Font font) {
        boolean segment=id.startsWith("mode")||id.startsWith("armor:")||id.startsWith("tool:")||id.startsWith("category-option:");
        boolean quiet=id.equals("pack")||id.equals("close")||id.equals("animations")||id.equals("remove-logo");
        boolean selected=segment&&primary&&enabled;
        Color fill=selected?SECONDARY:primary&&enabled?PRIMARY:quiet?BG:SURFACE;
        if(!quiet&&!segment)round(g,x,y+2,w,h,CONTROL_RADIUS,BG);
        round(g,x,y,w,h,CONTROL_RADIUS,fill);
        if(!quiet&&!segment)roundOutline(g,x,y,w,h,CONTROL_RADIUS,BORDER);
        if(primary&&enabled&&!segment)line(g,x+8,y+1,x+w-9,y+1,ACCENT);
        if(selected)round(g,x+8,y+h-3,w-16,2,2,ACCENT);
        String icon=switch(id){case "export"->"download";case "favorite"->"star";default->"";};
        int iconSpace=icon.isEmpty()?0:22;
        String displayed = clip(g, label, w - 16 - iconSpace - (id.equals("category")?20:0), font);
        int labelX=x+(w-FormattedText.width(g,displayed,font)-iconSpace)/2;
        if(!icon.isEmpty())UiIcons.draw(g,icon,labelX,y+(h-16)/2,enabled?TEXT:MUTED);
        if(id.equals("category"))UiIcons.draw(g,"chevron-down",x+w-22,y+(h-16)/2,MUTED);
        text(g, displayed, labelX+iconSpace, y + h / 2 + 5, font, enabled ? (selected?ACCENT:primary?WHITE:quiet?MUTED:TEXT) : MUTED);
        hit(g, new Hit(id, label, new Rectangle(x,y,w,h), enabled, action));
    }
    private void hit(Graphics2D g, Hit hit) {
        hits.add(hit);
        if(!hit.enabled)return;
        float progress=motion.progress(HOVER,frameTime);
        float hoverAlpha=hit.id.equals(hover)?progress:hit.id.equals(previousHover)?1-progress:0;
        if(hoverAlpha>0) {
            Graphics2D effect=(Graphics2D)g.create();alpha(effect,hoverAlpha);
            roundOutline(effect,hit.box.x,hit.box.y,hit.box.width,hit.box.height,CONTROL_RADIUS,MUTED);effect.dispose();
        }
        if(hit.id.equals(pressed)) {
            Graphics2D effect=(Graphics2D)g.create();alpha(effect,.16f*(1-motion.progress(PRESS,frameTime)));
            round(effect,hit.box.x,hit.box.y,hit.box.width,hit.box.height,CONTROL_RADIUS,ACCENT);effect.dispose();
        }
        if(hit.id.equals(focus))roundOutline(g,hit.box.x,hit.box.y,hit.box.width,hit.box.height,CONTROL_RADIUS,ACCENT);
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
            dragging = hit.id.equals("viewport"); return true;
        }
        if(previewMenu){if(!previewDialogBounds.contains(x,y))closePreview();return true;}
        if(identityMenu||projectsMenu)return true;
        focus = categoryMenu ? "category" : "";categoryMenu=false; dirty = true; return true;
    }
    public boolean drag(double dx, double dy) {
        if (identityMenu || projectsMenu || !dragging || mode == PreviewRenderer.Mode.FLAT) return false;
        yaw += dx * .012; pitch = Math.clamp(pitch + dy * .012, -1.5, 1.5); dirty = true; return true;
    }
    public void release() { dragging = false; }
    public boolean scroll(int x, int y, double amount) {
        if(identityMenu)return true;
        if (projectsMenu) { projectPage=Math.clamp(projectPage-(int)Math.signum(amount),0,Math.max(0,(session.projects().size()-1)/projectPageSize()));dirty=true;return true; }
        if(categoryMenu){categoryIndex=Math.floorMod(categoryIndex-(int)Math.signum(amount),TextureCategory.values().length);focus="category-option:"+categoryIndex;dirty=true;return true;}
        if (viewport.contains(x,y)) { zoom = Math.clamp(zoom + amount * .12, .4, 2.5); dirty = true; return true; }
        if(previewMenu)return true;
        int count=showingPacks()?session.visiblePacks().size():session.visible().size();
        scroll = Math.clamp(scroll - (int)Math.signum(amount) * pageSize, 0, Math.max(0, ((count - 1) / pageSize) * pageSize));
        dirty = true; return true;
    }
    public boolean type(int codepoint) {
        if(identitySaving)return true;
        if (projectsMenu) return true;
        if (!focus.equals("search") && !focus.equals("name")) return false;
        if (!Character.isValidCodePoint(codepoint) || Character.isISOControl(codepoint)) return false;
        String value = allTextSelected ? "" : currentText();
        setText(value + new String(Character.toChars(codepoint))); allTextSelected = false; return true;
    }
    public boolean key(int key, boolean shift, boolean control, String clipboard, Consumer<String> copy) {
        if(identitySaving)return true;
        if(previewMenu&&key==256){closePreview();return true;}
        if(identityMenu&&key==256){if(!session.busy())dismissIdentity();return true;}
        if (projectsMenu && key==256) { if(!session.busy()){projectsMenu=false;focus="projects";dirty=true;}return true; }
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
            if (focus.equals("name") && !nameEdited) nameDraft = session.name();
            dirty = true; return true;
        }
        if (projectsMenu) {
            if (key==257 || key==335 || key==32) hits.stream().filter(h->h.enabled&&h.id.equals(focus)).findFirst().ifPresent(this::activate);
            dirty=true;return true;
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
            if (key == 257 || key == 335) { if(identityMenu)dismissIdentity();else {if (focus.equals("name")) session.rename(nameDraft);focus="";} dirty = true; return true; }
            return false;
        }
        if(identityMenu) {
            if(key==257||key==335||key==32)hits.stream().filter(h->h.enabled&&h.id.equals(focus)).findFirst().ifPresent(this::activate);
            dirty=true;return true;
        }
        if (focus.equals("viewport")) {
            if (key == 263 || key == 262) yaw += (key == 263 ? -.12 : .12);
            if (key == 265 || key == 264) pitch = Math.clamp(pitch + (key == 265 ? -.12 : .12), -1.5, 1.5);
            if (key == 61 || key == 45) zoom = Math.clamp(zoom + (key == 61 ? .1 : -.1), .4, 2.5);
            dirty = true;
        }
        if(previewMenu&&key==32&&!focus.equals("viewport")) {
            hits.stream().filter(h->h.enabled&&h.id.equals(focus)).findFirst().ifPresent(this::activate);dirty=true;return true;
        }
        if (key == 70) { session.favorite(); return true; }
        if (key == 32) { session.select(); return true; }
        if (key == 257 || key == 335) {
            hits.stream().filter(h -> h.enabled && h.id.equals(focus)).findFirst().ifPresent(this::activate); dirty = true; return true;
        }
        return false;
    }
    private String currentText() { return focus.equals("search") ? session.query() : nameDraft; }
    private String draftName() { return nameEdited ? nameDraft : session.name(); }
    private void activate(Hit hit){pressed=hit.id;motion.start(PRESS);hit.action.run();}
    private static void alpha(Graphics2D g,float opacity) {
        float current=g.getComposite() instanceof AlphaComposite composite?composite.getAlpha():1;
        g.setComposite(AlphaComposite.SrcOver.derive(Math.clamp(current*opacity,0,1)));
    }
    private void setText(String value) {
        int max = focus.equals("name") ? 64 : 160;
        value = value.substring(0, Math.min(value.length(), max));
        if (focus.equals("search")) { session.search(value); scroll = 0; }
        else { nameDraft = value; nameEdited = true; }
        dirty = true;
    }
    private static void checker(Graphics2D g, int x, int y, int w, int h) {
        fill(g,x,y,w,h,SURFACE);
        for(int iy=0;iy<h;iy+=12) for(int ix=0;ix<w;ix+=12) if((ix/12+iy/12)%2==0) fill(g,x+ix,y+iy,Math.min(12,w-ix),Math.min(12,h-iy),CHECKER);
    }
    private static void fill(Graphics2D g,int x,int y,int w,int h,Color c) { g.setColor(c); g.fillRect(x,y,w,h); }
    private static void round(Graphics2D g,int x,int y,int w,int h,int radius,Color c) { g.setColor(c);g.fillRoundRect(x,y,w,h,radius,radius); }
    private static void roundOutline(Graphics2D g,int x,int y,int w,int h,int radius,Color c) { g.setColor(c);g.drawRoundRect(x,y,w-1,h-1,radius,radius); }
    private static void line(Graphics2D g,int x,int y,int ex,int ey,Color c) { g.setColor(c); g.drawLine(x,y,ex,ey); }
    private static void text(Graphics2D g,String s,int x,int y,Font f,Color c) { FormattedText.draw(g,s,x,y,f,c); }
    private static String clip(Graphics2D g,String s,int w,Font f) {
        return FormattedText.clip(g,s,w,f);
    }
}
