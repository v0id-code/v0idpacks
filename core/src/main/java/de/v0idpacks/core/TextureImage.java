package de.v0idpacks.core;

import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;

public record TextureImage(BufferedImage frame, int width, int height, boolean animated) {
    public static TextureImage decode(PackCatalog.Data data) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data.png()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Ungültige PNG-Datei");
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) throw new IOException("Keine PNG-Datei");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width > 8192 || height > 32768 || (long) width * height > 16_777_216)
                    throw new IOException("Textur überschreitet 16 Megapixel");
                int fw = width, fh = height, index = 0;
                boolean animated = false;
                if (data.metadata().length > 0) {
                    try {
                        JsonObject meta = JsonParser.parseString(new String(data.metadata(), StandardCharsets.UTF_8)).getAsJsonObject();
                        if (meta.has("animation")) {
                            animated = true;
                            JsonObject anim = meta.getAsJsonObject("animation");
                            fw = anim.has("width") ? anim.get("width").getAsInt() : (anim.has("height") ? width : Math.min(width, height));
                            fh = anim.has("height") ? anim.get("height").getAsInt() : (anim.has("width") ? height : Math.min(width, height));
                            if (anim.has("frames") && !anim.getAsJsonArray("frames").isEmpty()) {
                                JsonElement first = anim.getAsJsonArray("frames").get(0);
                                index = first.isJsonObject() ? first.getAsJsonObject().get("index").getAsInt() : first.getAsInt();
                            }
                            if (fw <= 0 || fh <= 0 || fw > width || fh > height || width % fw != 0 || height % fh != 0
                                    || index < 0 || index >= (long)(width / fw) * (height / fh))
                                throw new IOException("Ungültige Animationsabmessungen");
                        }
                    } catch (RuntimeException e) { throw new IOException("Ungültige Textur-Metadaten", e); }
                }
                BufferedImage image = reader.read(0);
                int x = (index % (width / fw)) * fw, y = (index / (width / fw)) * fh;
                BufferedImage frame = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
                frame.setRGB(0, 0, fw, fh, image.getRGB(x, y, fw, fh, null, 0, fw), 0, fw);
                return new TextureImage(frame, width, height, animated);
            } finally { reader.dispose(); }
        }
    }
}
