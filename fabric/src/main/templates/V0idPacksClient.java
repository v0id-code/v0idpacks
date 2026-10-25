package de.v0idpacks.fabric;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.v0idpacks.core.PackCatalog;
import de.v0idpacks.core.PackPresentation;
import de.v0idpacks.ui.EditorSession;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import @KEY_HELPER_IMPORT@;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import @IDENTIFIER_IMPORT@;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public final class V0idPacksClient implements ClientModInitializer {
    private EditorSession session;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("v0idpacks");
    @Override public void onInitializeClient() {
        KeyMapping key = @KEY_HELPER_CALL@(new KeyMapping("key.v0idpacks.open", InputConstants.Type.@KEY_TYPE@, InputConstants.KEY_V, @KEY_CATEGORY@));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (session != null) session.poll();
            while (key.consumeClick()) if (client.@CURRENT_SCREEN@ == null) open(client, null);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen || screen instanceof PauseScreen || screen instanceof PackSelectionScreen)
                Screens.@SCREEN_WIDGETS@(screen).add(Button.builder(Component.literal("v0idpacks"), button -> open(client, screen)).bounds(8, 8, 98, 20).build());
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { if (session != null) session.close(); });
    }
    EditorSession session() { return session; }
    void open(Minecraft client, Screen parent) {
        try {
            if (session == null) session = new EditorSession(client.getResourcePackDirectory(), FabricLoader.getInstance().getConfigDir().resolve("v0idpacks"), metadata(), this::activate, path -> {
                if(!PackCatalog.safeAsset(path)||!path.startsWith("assets/"))return null;
                String[] parts=path.split("/",3);
                var resource=client.getResourceManager().getResource(@IDENTIFIER@.fromNamespaceAndPath(parts[1],parts[2]));
                if(resource.isEmpty())return null;
                int limit=path.endsWith(".png")?32*1024*1024:1024*1024;
                try(InputStream stream=resource.get().open()) {
                    byte[] bytes=stream.readNBytes(limit+1);
                    if(bytes.length>limit)throw new IOException("Modellressource zu groß");
                    return bytes;
                }
            });
            client.@SET_SCREEN@(new WorkshopScreen(parent, session));
        } catch (Exception e) {
            LOGGER.error("Could not open v0idpacks", e);
            client.@SET_SCREEN@(new net.minecraft.client.gui.screens.AlertScreen(() -> client.@SET_SCREEN@(parent), Component.literal("v0idpacks"), Component.literal("Editor konnte nicht geöffnet werden: " + e.getMessage())));
        }
    }
    private JsonObject metadata() throws IOException {
        // Embedded by the build from the actual game JAR's version.json.
        try (InputStream input = getClass().getResourceAsStream("/v0idpacks-pack-format.json")) {
            if (input == null) throw new IOException("Versionsmetadaten fehlen");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private void activate(Path output) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            try {
                var repository = client.getResourcePackRepository();
                List<String> selected = new ArrayList<>(repository.getSelectedIds());
                repository.reload();
                String id = "file/" + output.getFileName();
                if (repository.getPack(id) == null) throw new IOException("Export wurde von Minecraft nicht erkannt");
                String project = PackPresentation.readProjectId(client.getResourcePackDirectory(), id);
                selected.removeIf(s -> PackPresentation.belongsToProject(client.getResourcePackDirectory(), s, project));
                selected.add(id);
                repository.setSelected(selected);
                client.options.resourcePacks.clear();
                for (var pack : repository.getSelectedPacks()) if (!pack.isFixedPosition()) client.options.resourcePacks.add(pack.getId());
                client.options.incompatibleResourcePacks.removeIf(s -> PackPresentation.belongsToProject(client.getResourcePackDirectory(), s, project));
                client.options.save();
                client.reloadResourcePacks().whenComplete((unused, failure) -> {
                    if (failure != null) LOGGER.error("Resource pack reload failed", failure);
                    client.execute(() -> session.activationFinished(failure != null ? failure.toString() : repository.getSelectedIds().contains(id) ? null : "Minecraft hat das Pack beim Neuladen zurückgesetzt."));
                });
            } catch (Exception e) { LOGGER.error("Pack activation failed", e); session.activationFinished(e.getMessage()); }
        });
    }
}
