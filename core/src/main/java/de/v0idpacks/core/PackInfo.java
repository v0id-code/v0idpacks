package de.v0idpacks.core;

import java.awt.image.BufferedImage;
import java.util.Locale;

public record PackInfo(String id,String displayName,String description,int textureCount,BufferedImage icon) {
    public boolean matches(String query) {
        String haystack=MinecraftFormatting.plain(displayName+" "+description).toLowerCase(Locale.ROOT);
        for(String word:MinecraftFormatting.plain(query).strip().toLowerCase(Locale.ROOT).split("\\s+"))if(!haystack.contains(word))return false;
        return true;
    }
}
