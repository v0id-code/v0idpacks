package de.v0idpacks.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import @IDENTIFIER_IMPORT@;
import de.v0idpacks.core.*;
import de.v0idpacks.ui.EditorSession;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;

/** Generated only with -Psmoke. Never part of release JARs. */
public final class SmokeClient implements ClientModInitializer {
    private int ticks, stage;
    private long started;
    private V0idPacksClient mod;
    private WorkshopScreen screen;
    private byte[] expected;
    @Override public void onInitializeClient() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) throw new IllegalStateException("Smoke test requires a development instance");
        try {
            Path pack=FabricLoader.getInstance().getGameDir().resolve("resourcepacks/Smoke Fixture");
            Path png=pack.resolve("assets/minecraft/textures/item/diamond_sword.png");
            Files.createDirectories(png.getParent());
            // Deliberately distinct from vanilla to verify actual resource priority after reload.
            BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            for(int y=1;y<15;y++) for(int x=6;x<10;x++) image.setRGB(x,y,0xff29addd);
            ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);expected=out.toByteArray();Files.write(png,expected);
            Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":46,\"description\":\"Local smoke fixture\"}}");
        } catch(IOException e) { throw new RuntimeException(e); }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(Minecraft client) {
        if(stage==99) return;
        if(started==0) started=System.nanoTime();
        try {
            if(System.nanoTime()-started>180_000_000_000L) throw new AssertionError("Timed out at stage "+stage);
            if(++ticks<50) return;
            if(stage==0) {
                mod=FabricLoader.getInstance().getEntrypoints("client",ClientModInitializer.class).stream().filter(e->e instanceof V0idPacksClient).map(e->(V0idPacksClient)e).findFirst().orElseThrow();
                mod.open(client,null);screen=(WorkshopScreen)client.@CURRENT_SCREEN@;stage=1;return;
            }
            EditorSession session=mod.session();
            if(session.busy()) return;
            if(session.error()) throw new AssertionError(session.status());
            switch(stage) {
                case 1 -> {
                    if(!session.ready()) throw new AssertionError("Library unavailable");
                    TextureRef ref=session.visible().stream().filter(t->t.pack().equals("Smoke Fixture")).findFirst().orElseThrow();
                    // A repeated launch must have retained the previously activated export in options.txt.
                    if(session.selectedCount()>0 && client.getResourcePackRepository().getSelectedIds().stream().noneMatch(s->s.startsWith("file/v0idpacks-custom-"))) throw new AssertionError("Activation did not survive restart");
                    session.focus(ref);if(!session.isFavorite(ref))session.favorite();stage=2;
                }
                case 2 -> {if(session.preview()==null)return;if(!session.isSelected(session.focused()))session.select();stage=3;}
                case 3 -> {session.export(true);stage=4;}
                case 4 -> {
                    if(!session.status().contains("aktiv"))throw new AssertionError(session.status());
                    if(screen.renderedFrames<2)throw new AssertionError("Workshop did not render");
                    LibraryStore persisted=new LibraryStore(FabricLoader.getInstance().getConfigDir().resolve("v0idpacks"));
                    if(persisted.state().selected().size()!=1||persisted.state().favorites().size()!=1)throw new AssertionError("Persistence mismatch");
                    try(InputStream in=client.getResourceManager().getResource(@IDENTIFIER@.fromNamespaceAndPath("minecraft","textures/item/diamond_sword.png")).orElseThrow().open()) {
                        if(!Arrays.equals(expected,in.readAllBytes()))throw new AssertionError("Export not active in resource manager");
                    }
                    System.out.println("V0IDPACKS_SMOKE_PASS "+FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString()+" frames="+screen.renderedFrames);
                    stage=99;client.stop();
                }
            }
        } catch(Throwable error) {
            System.err.println("V0IDPACKS_SMOKE_FAIL stage="+stage);error.printStackTrace();stage=99;client.stop();
        }
    }
}
