package io.github.thebusybiscuit.slimefun4.core.multiblocks;

import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Purely cosmetic, client-interpolated {@link ItemDisplay} animations for {@link MultiBlockMachine}s.
 * It never touches inventories: machines craft exactly as before, this only shows the input
 * being processed and the result hopping into the output block.
 * Displays are non-persistent, so they are never saved with a chunk or across restarts.
 */
public final class MachineAnimation {

    public enum Style {
        GRIND,
        SAW,
        CRUSH,
        PRESS,
        SMELT,
        CRAFT,
        WASH
    }

    private static final float SCALE = 0.45F;
    private static final int INPUT_TICKS = 12;
    /** Tick (after start) at which the result lands in the output block. */
    public static final int LANDING_TICK = INPUT_TICKS + 12;

    private static final int TOTAL_TICKS = LANDING_TICK + 1;

    // one running animation per machine, keyed by the block the animation is centered in
    private static final Map<Location, List<Entity>> active = new HashMap<>();

    // time scale of the animation being built right now: upgraded machines animate a bit faster
    private static float pace = 1;

    private MachineAnimation() {}

    /**
     * Plays the animation right away, centered just above the given machine block.
     */
    @ParametersAreNonnullByDefault
    public static void play(
            Block machine, Block outputBlock, Style style, @Nullable ItemStack output, ItemStack... inputs) {
        play(above(machine), outputBlock, style, 0, output, inputs);
    }

    public static @Nonnull Location above(@Nonnull Block b) {
        return b.getLocation().add(0.5, 1.3, 0.5);
    }

    /**
     * Plays the animation after {@code delay} ticks. Machines that already wait before outputting
     * use {@code waitTicks - LANDING_TICK} so the result lands when it really arrives.
     * Items are copied now, so callers may consume them right after this call.
     */
    @ParametersAreNonnullByDefault
    public static void play(
            Location center,
            Block outputBlock,
            Style style,
            long delay,
            @Nullable ItemStack output,
            ItemStack... inputs) {
        if (Slimefun.getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            return;
        }

        ItemStack shownOutput = output == null || output.getType().isAir() ? null : output.clone();
        List<ItemStack> shownInputs = Arrays.stream(inputs)
                .filter(i -> i != null && !i.getType().isAir())
                .limit(9)
                .map(ItemStack::clone)
                .toList();

        int tier = MachineTier.current();

        if (delay > 0) {
            // keep landing when the output really arrives, the faster animation just starts later
            delay += LANDING_TICK - Math.round(LANDING_TICK * pace(tier));
        }

        Slimefun.runSync(() -> start(center, outputBlock, style, tier, shownOutput, shownInputs), Math.max(0, delay));
    }

    private static void start(
            Location center,
            Block outputBlock,
            Style style,
            int tier,
            @Nullable ItemStack output,
            List<ItemStack> inputs) {
        Location key = center.getBlock().getLocation();

        try {
            if (!Slimefun.getCfg().getOrSetDefault("options.machine-animations", true)
                    || active.containsKey(key)
                    || !center.isChunkLoaded()) {
                return;
            }

            List<Entity> spawned = new ArrayList<>();
            active.put(key, spawned);
            pace = pace(tier);
            Slimefun.runSync(() -> stop(key), scaled(TOTAL_TICKS));
            animate(center, outputBlock, style, output, inputs, spawned);

            if (tier > 0) {
                showTier(center, outputBlock, tier, spawned);
            }
        } catch (RuntimeException x) {
            // never let a cosmetic effect break the machine
            stop(key);
            Slimefun.logger().log(Level.WARNING, "Could not play a machine animation", x);
        } finally {
            pace = 1;
        }
    }

    /**
     * Removes every display still alive, used on shutdown.
     */
    public static void removeAll() {
        active.values().forEach(list -> list.forEach(Entity::remove));
        active.clear();
    }

    private static void stop(Location key) {
        List<Entity> list = active.remove(key);

        if (list != null) {
            list.forEach(Entity::remove);
        }
    }

    @ParametersAreNonnullByDefault
    private static void animate(
            Location center,
            Block outputBlock,
            Style style,
            @Nullable ItemStack output,
            List<ItemStack> items,
            List<Entity> spawned) {
        List<Entity> inputPhase = new ArrayList<>();
        int n = items.size();

        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            float cx = (float) Math.cos(a) * 0.4F;
            float cz = (float) Math.sin(a) * 0.4F;

            switch (style) {
                case GRIND -> {
                    ItemDisplay d = spawn(center, items.get(i), tf(0, -0.1F, 0, 0, SCALE, SCALE), spawned, inputPhase);
                    spin(d, 0, -0.15F, 0, SCALE, 0.12F);
                }
                case SAW -> {
                    ItemDisplay d =
                            spawn(center, items.get(i), tf(-0.35F, 0, 0, 90, SCALE, SCALE), spawned, inputPhase);
                    move(d, 1, 11, tf(0.25F, -0.05F, 0, 90, SCALE, 0.08F));
                }
                case CRUSH -> {
                    ItemDisplay d = spawn(center, items.get(i), tf(0, 0, 0, 0, SCALE, SCALE), spawned, inputPhase);
                    move(d, 1, 5, tf(0, -0.05F, 0, 20, 0.55F, 0.25F));
                    move(d, 7, 5, tf(0, -0.15F, 0, -20, 0.7F, 0.02F));
                }
                case PRESS -> {
                    ItemDisplay d = spawn(center, items.get(i), tf(0, -0.1F, 0, 0, SCALE, SCALE), spawned, inputPhase);
                    move(d, 3, 2, tf(0, -0.15F, 0, 0, 0.5F, 0.25F));
                    move(d, 11, 2, tf(0, -0.2F, 0, 0, 0.55F, 0.05F));
                }
                case SMELT -> {
                    ItemDisplay d = spawn(center, items.get(i), tf(cx, 0, cz, 0, SCALE, SCALE), spawned, inputPhase);
                    d.setBrightness(new Display.Brightness(15, 15));
                    move(d, 1, 11, tf(0, -0.05F, 0, 0, 0.1F, 0.1F));
                }
                case CRAFT -> {
                    ItemDisplay d = spawn(center, items.get(i), tf(cx, 0, cz, 0, SCALE, SCALE), spawned, inputPhase);

                    for (int k = 1; k <= 4; k++) {
                        double ak = a + Math.toRadians(70 * k);
                        float r = 0.4F * (1 - k / 4.5F);
                        float s = SCALE * (1 - k / 5F);
                        move(
                                d,
                                1 + 3 * (k - 1),
                                3,
                                tf((float) Math.cos(ak) * r, 0.05F * k, (float) Math.sin(ak) * r, 70 * k, s, s));
                    }
                }
                case WASH -> {
                    ItemDisplay d = spawn(center, items.get(i), tf(0, 0, 0, 0, SCALE, SCALE), spawned, inputPhase);
                    move(d, 1, 3, tf(0, -0.15F, 0, 120, SCALE, SCALE));
                    move(d, 4, 3, tf(0, 0.05F, 0, 240, SCALE, SCALE));
                    move(d, 7, 3, tf(0, -0.15F, 0, 0, SCALE, SCALE));
                    move(d, 10, 2, tf(0, -0.25F, 0, 120, 0.1F, 0.1F));
                }
            }
        }

        if (style == Style.GRIND) {
            ItemDisplay stone = spawn(
                    center, new ItemStack(Material.GRINDSTONE), tf(0, 0.2F, 0, 0, 0.5F, 0.5F), spawned, inputPhase);
            spin(stone, 0, 0.2F, 0, 0.5F, 0.5F);
        } else if (style == Style.PRESS) {
            ItemDisplay piston = spawn(center, new ItemStack(Material.PISTON), press(0.5F), spawned, inputPhase);
            move(piston, 1, 3, press(0.15F));
            move(piston, 5, 3, press(0.5F));
            move(piston, 9, 3, press(0.1F));
        }

        Slimefun.runSync(() -> inputPhase.forEach(Entity::remove), scaled(INPUT_TICKS) + 1L);

        if (output != null) {
            Vector3f to = outputBlock
                    .getLocation()
                    .add(0.5, 0.5, 0.5)
                    .toVector()
                    .subtract(center.toVector())
                    .toVector3f();
            ItemDisplay out = spawn(center, output, tf(0, 0, 0, 0, 0, 0), spawned, null);

            if (style == Style.SMELT) {
                out.setBrightness(new Display.Brightness(15, 15));
            }

            // pop out, small arc, then drop into the output block
            move(out, INPUT_TICKS, 4, tf(0, 0.25F, 0, 0, SCALE, SCALE));
            move(out, INPUT_TICKS + 4, 4, tf(to.x / 2, Math.max(0, to.y) + 0.5F, to.z / 2, 180, SCALE, SCALE));
            move(out, INPUT_TICKS + 8, 4, tf(to.x, to.y, to.z, 300, 0.15F, 0.15F));
        }
    }

    // three 120 degree steps, each under 180 so the client's slerp keeps spinning the same way
    private static void spin(ItemDisplay d, float x, float y, float z, float s, float endScale) {
        for (int k = 1; k <= 3; k++) {
            float sk = s + (endScale - s) * k / 3F;
            move(d, 1 + 4 * (k - 1), 4, tf(x, y, z, 120 * k, sk, sk));
        }
    }

    private static Transformation press(float y) {
        // piston item flipped upside down so its head faces the input
        return new Transformation(
                new Vector3f(0, y, 0),
                new Quaternionf().rotateX((float) Math.PI),
                new Vector3f(0.6F, 0.6F, 0.6F),
                new Quaternionf());
    }

    private static Transformation tf(float x, float y, float z, float yawDeg, float s, float sy) {
        return new Transformation(
                new Vector3f(x, y, z),
                new Quaternionf().rotateY((float) Math.toRadians(yawDeg)),
                new Vector3f(s, sy, s),
                new Quaternionf());
    }

    private static float pace(int tier) {
        return 1 - 0.075F * tier;
    }

    private static long scaled(long ticks) {
        return Math.round(ticks * pace);
    }

    /** Level label above the core and particles in the level's colour, at the start and at landing. */
    private static void showTier(Location center, Block outputBlock, int tier, List<Entity> spawned) {
        spawned.add(center.getWorld().spawn(center.clone().add(0, 0.75, 0), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.text(MachineTier.label(tier));
        }));

        Particle.DustOptions dust = new Particle.DustOptions(MachineTier.color(tier), 1.2F);
        center.getWorld().spawnParticle(Particle.DUST, center, 6 + 4 * tier, 0.35, 0.25, 0.35, dust);
        Location landing = outputBlock.getLocation().add(0.5, 0.5, 0.5);
        Slimefun.runSync(
                () -> {
                    if (landing.isChunkLoaded()) {
                        landing.getWorld().spawnParticle(Particle.DUST, landing, 4 + 4 * tier, 0.3, 0.3, 0.3, dust);
                    }
                },
                scaled(LANDING_TICK));
    }

    private static void move(Display d, long tick, int duration, Transformation t) {
        int scaledDuration = (int) Math.max(1, scaled(duration));
        Slimefun.runSync(
                () -> {
                    if (d.isValid()) {
                        d.setInterpolationDelay(0);
                        d.setInterpolationDuration(scaledDuration);
                        d.setTransformation(t);
                    }
                },
                scaled(tick));
    }

    private static @Nonnull ItemDisplay spawn(
            Location loc, ItemStack item, Transformation t, List<Entity> spawned, @Nullable List<Entity> phase) {
        ItemStack shown = item.asOne();

        ItemDisplay d = loc.getWorld().spawn(loc, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(shown);
            e.setTransformation(t);
        });

        spawned.add(d);

        if (phase != null) {
            phase.add(d);
        }

        return d;
    }
}
