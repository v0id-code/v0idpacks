package de.v0idpacks.fabric;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import de.v0idpacks.ui.EditorCanvas;
import de.v0idpacks.ui.EditorSession;
import de.v0idpacks.ui.PanelLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import @IDENTIFIER_IMPORT@;
import @GRAPHICS_IMPORT@;
import java.awt.image.BufferedImage;

public final class WorkshopScreen extends Screen {
    private static final @IDENTIFIER@ TEXTURE = @IDENTIFIER@.fromNamespaceAndPath("v0idpacks", "workshop");
    private final Screen parent;
    private final EditorCanvas canvas;
    private DynamicTexture texture;
    private PanelLayout panel;
    private long lastUpload;
    private boolean textInput;
    int renderedFrames;
    public WorkshopScreen(Screen parent, EditorSession session) {
        super(Component.literal("v0idpacks")); this.parent = parent; this.canvas = new EditorCanvas(session, this::onClose);
    }
    @Override protected void init() {
        panel = PanelLayout.fit(width,height,minecraft.getWindow().getWidth(),minecraft.getWindow().getHeight());
        canvas.resize(panel.pixelWidth(),panel.pixelHeight());
    }
    @Override public void @RENDER_METHOD@(@GRAPHICS@ graphics, int mouseX, int mouseY, float delta) {
        renderedFrames++;
        canvas.mouseMove(panel.mouseX(mouseX), panel.mouseY(mouseY));
        if (textInput != canvas.isEditingText()) { textInput = canvas.isEditingText(); @TEXT_INPUT_UPDATE@ }
        long now = System.nanoTime();
        if (texture == null || now - lastUpload > 33_333_333L) {
            BufferedImage image = canvas.draw();
            if (image != null) {
                upload(image); lastUpload = now;
            }
        }
        graphics.fill(0,0,width,height,0x99000000);
        if (texture != null) graphics.blit(@BLIT_PIPELINE@, TEXTURE, panel.x(), panel.y(), 0f, 0f, panel.width(), panel.height(), canvas.width(), canvas.height(), canvas.width(), canvas.height());
    }
    private void upload(BufferedImage image) {
        if (texture == null || texture.getPixels().getWidth() != image.getWidth() || texture.getPixels().getHeight() != image.getHeight()) {
            if (texture != null) minecraft.getTextureManager().release(TEXTURE);
            texture = @CREATE_TEXTURE@;
            minecraft.getTextureManager().register(TEXTURE, texture);
        }
        NativeImage pixels = texture.getPixels();
        int w = image.getWidth(), h = image.getHeight();
        int[] colors = ((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int argb = colors[y * w + x];
            @SET_PIXEL@
        }
        texture.upload();
    }
    @Override public void onClose() { minecraft.@SET_SCREEN@(parent); }
    @Override public boolean isPauseScreen() { return true; }
    @Override public void removed() { @TEXT_INPUT_CLOSE@ if (texture != null) { minecraft.getTextureManager().release(TEXTURE); texture = null; } }
    @Override protected void updateNarrationState(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, "v0idpacks");
        output.add(NarratedElementType.USAGE, canvas.narration());
    }
    private boolean key(int key, int modifiers) {
        int normalized = switch (key) {
            case InputConstants.KEY_ESCAPE -> 256;
            case InputConstants.KEY_TAB -> 258;
            case InputConstants.KEY_RETURN -> 257;
            case InputConstants.KEY_NUMPADENTER -> 335;
            case InputConstants.KEY_BACKSPACE -> 259;
            case InputConstants.KEY_DELETE -> 261;
            case InputConstants.KEY_LEFT -> 263;
            case InputConstants.KEY_RIGHT -> 262;
            case InputConstants.KEY_UP -> 265;
            case InputConstants.KEY_DOWN -> 264;
            case InputConstants.KEY_A -> 65;
            case InputConstants.KEY_C -> 67;
            case InputConstants.KEY_V -> 86;
            case InputConstants.KEY_X -> 88;
            case InputConstants.KEY_F -> 70;
            case InputConstants.KEY_SPACE -> 32;
            case InputConstants.KEY_EQUALS -> 61;
            case InputConstants.KEY_MINUS -> 45;
            default -> -1;
        };
        return canvas.key(normalized, (modifiers & @MOD_SHIFT@) != 0, (modifiers & @MOD_CONTROL@) != 0, minecraft.keyboardHandler.getClipboard(), minecraft.keyboardHandler::setClipboard);
    }
    @INPUT_METHODS@
}
