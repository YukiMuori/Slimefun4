package io.github.thebusybiscuit.slimefun4.core.attributes;

import javax.annotation.Nonnull;

public enum MachineTier {
    BASIC("&eBase"),
    AVERAGE("&6Normale"),
    MEDIUM("&aIntermedio"),
    GOOD("&2Buono"),
    ADVANCED("&6Avanzato"),
    END_GAME("&4Finale");

    private final String prefix;

    MachineTier(@Nonnull String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String toString() {
        return prefix;
    }
}
