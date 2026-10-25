package de.v0idpacks.ui;

import com.google.gson.JsonObject;
import de.v0idpacks.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class AnimatedWorkshopTest {
    @TempDir Path temp;
    private EditorSession session;
    private final AtomicLong clock=new AtomicLong();
    @AfterEach void close(){if(session!=null)session.close();}
    private EditorCanvas prepare() throws Exception {
        Path pack=temp.resolve("resourcepacks/§bMotion (Demo)");
        Files.createDirectories(pack.resolve("assets/minecraft/textures/item"));
        Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":46,\"description\":\"Generated test fixture\"}}");
        ImageIO.write(WorkshopTest.sword(0xff4ddbd2),"png",pack.resolve("assets/minecraft/textures/item/diamond_sword.png").toFile());
        JsonObject meta=new JsonObject();meta.addProperty("pack_format",46);
        session=new EditorSession(temp.resolve("resourcepacks"),temp.resolve("config"),meta,p->{});
        await(()->!session.busy());return new EditorCanvas(session,()->{},clock::get);
    }
    private void await(BooleanSupplier finished) {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{do{Thread.sleep(10);session.poll();}while(!finished.getAsBoolean());});
    }
    private void snapshot(String name,BufferedImage image) throws Exception {
        Path dir=Path.of("build/previews");Files.createDirectories(dir);ImageIO.write(image,"png",dir.resolve(name+".png").toFile());
    }
    @Test void brandedOpeningAndHoverReachFinalFrameAndStopUploading() throws Exception {
        EditorCanvas canvas=prepare();BufferedImage first=canvas.draw();int alpha=first.getRGB(500,600)>>>24;
        assertTrue(alpha>0&&alpha<255);snapshot("motion-open-start",first);
        clock.set(100_000_000);BufferedImage middle=canvas.draw();assertTrue((middle.getRGB(500,600)>>>24)>alpha);snapshot("motion-open-middle",middle);
        clock.set(300_000_000);BufferedImage finished=canvas.draw();assertEquals(255,finished.getRGB(500,600)>>>24);snapshot("motion-open-final",finished);
        assertNull(canvas.draw());canvas.mouseMove(40,260);int border=canvas.draw().getRGB(12,250);
        clock.set(350_000_000);assertNotEquals(border,canvas.draw().getRGB(12,250));
        clock.set(450_000_000);assertNotNull(canvas.draw());assertNull(canvas.draw());
        canvas.resize(760,440);snapshot("branding-compact",canvas.draw());
    }
    @Test void filtersAndPopupKeepWorkingDuringTransitionsAndPreviewSettles() throws Exception {
        EditorCanvas canvas=prepare();canvas.draw();clock.set(300_000_000);canvas.draw();
        canvas.click(250,160,0);canvas.draw(); // Open category menu; covered cards stay blocked.
        canvas.key(70,false,false,"",s->{});assertEquals(0,session.favoriteCount());
        clock.set(350_000_000);snapshot("motion-menu-middle",canvas.draw());
        canvas.click(250,250,0);canvas.draw();assertEquals(TextureCategory.SWORDS,session.categoryFilter());
        assertEquals(1,session.visible().size());canvas.click(220,220,0);assertNotNull(session.focused());
        await(()->session.preview()!=null);canvas.draw(); // Preview enters without delaying selection.
        session.favorite();await(()->!session.busy());assertEquals(1,session.favoriteCount());
        clock.set(430_000_000);snapshot("motion-preview-middle",canvas.draw());
        clock.set(700_000_000);snapshot("motion-preview-final",canvas.draw());
        await(()->session.thumbnail(session.focused())!=null);canvas.draw();assertNull(canvas.draw());
    }
    @Test void disablingMotionMidTransitionIsImmediateAndPersists() throws Exception {
        EditorCanvas canvas=prepare();canvas.draw();canvas.click(40,300,0);await(()->!session.busy());
        assertFalse(session.animationsEnabled());assertFalse(new UiPreferences(temp.resolve("config")).animationsEnabled());
        assertEquals(255,canvas.draw().getRGB(500,600)>>>24);assertNull(canvas.draw());
        canvas.mouseMove(40,260);assertNotNull(canvas.draw());assertNull(canvas.draw());
        EditorCanvas reopened=new EditorCanvas(session,()->{},clock::get);
        assertEquals(255,reopened.draw().getRGB(500,600)>>>24);assertNull(reopened.draw());
    }
}
