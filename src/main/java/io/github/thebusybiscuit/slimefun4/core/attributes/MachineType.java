package io.github.thebusybiscuit.slimefun4.core.attributes;

import javax.annotation.Nonnull;

public enum MachineType {
    CAPACITOR("Condensatore"),
    GENERATOR("Generatore"),
    MACHINE("Macchina");

    private final String suffix;

    MachineType(@Nonnull String suffix) {
        this.suffix = suffix;
    }

    @Override
    public String toString() {
        return suffix;
    }
}
