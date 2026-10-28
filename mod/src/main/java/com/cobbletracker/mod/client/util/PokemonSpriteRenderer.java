package com.cobbletracker.mod.client.util;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbletracker.mod.CobbleTracker;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector4f;

/** GUI icons rendered through Cobblemon's Pokémon item renderer. */
public final class PokemonSpriteRenderer {

    private PokemonSpriteRenderer() {
    }

    private static final Map<String, ItemStack> ICON_CACHE = new java.util.LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) {
            return size() > 512;
        }
    };

    /** Returns false without drawing if the model is unavailable. */
    public static boolean drawPokemon(GuiGraphics guiGraphics, String species, boolean shiny, int x, int y, int size) {
        ItemStack stack = iconFor(species, shiny);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var pose = guiGraphics.pose();
        pose.pushPose();
        float scale = size / 16.0f;
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1.0f);
        guiGraphics.renderItem(stack, 0, 0);
        pose.popPose();
        return true;
    }

    public static void drawPokemonOrToken(GuiGraphics guiGraphics, Font font, String species, boolean shiny,
            int x, int y, int size) {
        if (!drawPokemon(guiGraphics, species, shiny, x, y, size)) {
            SpriteClientUtils.drawSpeciesIcon(guiGraphics, font, species, x, y, size);
        }
    }

    private static ItemStack iconFor(String species, boolean shiny) {
        if (species == null || species.isEmpty()) {
            return null;
        }
        String key = species + "|" + (shiny ? "s" : "");
        ItemStack cached = ICON_CACHE.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        ItemStack built = build(species, shiny);
        ICON_CACHE.put(key, built == null ? ItemStack.EMPTY : built);
        return built;
    }

    private static ItemStack build(String species, boolean shiny) {
        try {
            Species resolved = PokemonSpecies.getByIdentifier(ResourceLocation.parse(species));
            if (resolved == null) {
                return null;
            }
            Set<String> aspects = new HashSet<>();
            if (shiny) {
                aspects.add("shiny");
            }
            return PokemonItem.from(resolved, aspects, 1, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
        } catch (Throwable t) {
            CobbleTracker.LOGGER.debug("No Cobblemon sprite for {} (using token fallback)", species);
            return null;
        }
    }
}
