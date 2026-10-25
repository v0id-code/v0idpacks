package de.v0idpacks.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** UI preferences are independent of the texture library and its backup format. */
public final class UiPreferences {
    private final Path path;
    private volatile boolean animationsEnabled = true;
    private final String warning;
    public UiPreferences(Path config) {
        path=config.resolve("ui.json");String problem="";
        if(Files.exists(path))try {
            JsonObject json=JsonParser.parseString(new String(SafeFiles.read(path,16*1024),StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.has("animations")) {
                JsonElement value=json.get("animations");
                if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Ungültige Einstellung");
                animationsEnabled=value.getAsBoolean();
            }
        } catch(IOException|RuntimeException e) {problem="Animationseinstellung nicht lesbar; Standard ist aktiv.";}
        warning=problem;
    }
    public boolean animationsEnabled(){return animationsEnabled;}
    public String warning(){return warning;}
    public synchronized void setAnimationsEnabled(boolean enabled) throws IOException {
        JsonObject json=new JsonObject();json.addProperty("animations",enabled);
        SafeFiles.atomicWrite(path,(json+"\n").getBytes(StandardCharsets.UTF_8));animationsEnabled=enabled;
    }
}
