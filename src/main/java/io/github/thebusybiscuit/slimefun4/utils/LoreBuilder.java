package io.github.thebusybiscuit.slimefun4.utils;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactivity;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * This utility class provides a few handy methods and constants to build the lore of any
 * {@link SlimefunItemStack}. It is mostly used directly inside the class {@link SlimefunItems}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunItems
 *
 */
public final class LoreBuilder {

    public static final String HAZMAT_SUIT_REQUIRED = "&8\u21E8 &4Richiede la tuta anti-radiazioni!";
    public static final String RAINBOW = "&dCicla tra i colori dell'arcobaleno!";
    public static final String RIGHT_CLICK_TO_USE = "&eClic destro&7 per usare";
    public static final String RIGHT_CLICK_TO_OPEN = "&eClic destro&7 per aprire";
    public static final String CROUCH_TO_USE = "&eAccovacciati&7 per usare";
    private static final DecimalFormat hungerFormat =
            new DecimalFormat("#.0", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private LoreBuilder() {}

    public static @Nonnull String radioactive(@Nonnull Radioactivity radioactivity) {
        return radioactivity.getLore();
    }

    public static @Nonnull String machine(@Nonnull MachineTier tier, @Nonnull MachineType type) {
        // Italian word order: colour of the tier, then "Macchina Base"
        String t = tier.toString();
        return t.substring(0, 2) + type + " " + t.substring(2);
    }

    public static @Nonnull String speed(float speed) {
        return "&8\u21E8 &b\u26A1 &7Velocità: &b" + speed + 'x';
    }

    public static @Nonnull String powerBuffer(int power) {
        return power(power, " di capacità");
    }

    public static @Nonnull String powerPerSecond(int power) {
        return power(power, "/s");
    }

    public static @Nonnull String power(int power, @Nonnull String suffix) {
        return "&8\u21E8 &e\u26A1 &7" + power + " J" + suffix;
    }

    public static @Nonnull String powerCharged(int charge, int capacity) {
        return "&8\u21E8 &e\u26A1 &7" + charge + " / " + capacity + " J";
    }

    public static @Nonnull String material(String material) {
        return "&8\u21E8 &7Materiale: &b" + material;
    }

    public static @Nonnull String hunger(double value) {
        return "&7&oRipristina &b&o" + hungerFormat.format(value) + " &7&opunti fame";
    }

    public static @Nonnull String range(int blocks) {
        return "&7Raggio: &c" + blocks + " blocchi";
    }

    public static @Nonnull String usesLeft(int usesLeft) {
        return "&7Usi rimasti: &e" + usesLeft;
    }
}
