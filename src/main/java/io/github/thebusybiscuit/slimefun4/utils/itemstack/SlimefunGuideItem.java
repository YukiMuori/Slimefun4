package io.github.thebusybiscuit.slimefun4.utils.itemstack;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.bakedlibs.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This is just a helper {@link ItemStack} class for the {@link SlimefunGuide} {@link ItemStack}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunGuide
 * @see SlimefunGuideImplementation
 *
 */
public class SlimefunGuideItem extends ItemStack {

    public SlimefunGuideItem(@Nonnull SlimefunGuideImplementation implementation, @Nonnull String name) {
        super(Material.ENCHANTED_BOOK);

        ItemMeta meta = getItemMeta();
        meta.setDisplayName(ChatColors.color(name));

        List<String> lore = new ArrayList<>();
        SlimefunGuideMode type = implementation.getMode();
        lore.add(type == SlimefunGuideMode.CHEAT_MODE ? ChatColors.color("&4&lSolo per amministratori") : "");
        lore.add(ChatColors.color("&eClic destro &8\u21E8 &7Sfoglia gli oggetti"));
        lore.add(ChatColors.color("&eShift + Clic destro &8\u21E8 &7Impostazioni / Info"));

        meta.setLore(lore);

        PersistentDataAPI.setString(meta, Slimefun.getRegistry().getGuideDataKey(), type.name());
        Slimefun.getItemTextureService().setTexture(meta, "SLIMEFUN_GUIDE");

        setItemMeta(meta);
    }
}
