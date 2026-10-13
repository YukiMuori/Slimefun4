package io.github.thebusybiscuit.slimefun4.core.multiblocks;

import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockInteractEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Upgrade levels (I-IV) for the basic {@link MultiBlockMachine}s.
 * The level lives in the chunk's {@link PersistentDataContainer}, keyed by the coordinates of the
 * clicked "core" block, so it survives restarts and needs no Slimefun block data on vanilla blocks.
 * Every way the core block can leave its spot either refunds the upgrades (break, explosion, fire)
 * or is blocked (pistons), so a level can never exist twice.
 */
public final class MachineTier implements Listener {

    public static final int MAX = 4;

    private static final String[] ROMAN = {"", "I", "II", "III", "IV"};
    private static final int[] COLORS = {0xFFFFFF, 0xCD7F32, 0xD8D8D8, 0x4AEDD9, 0x9A7B7B};
    private static final int[] YIELD = {0, 5, 10, 15, 25};
    // ticks per working step of the waiting machines (base 20)
    private static final long[] STEP = {20, 17, 14, 11, 9};

    private static final Set<String> MACHINES = Set.of(
            "GRIND_STONE",
            "ORE_CRUSHER",
            "COMPRESSOR",
            "PRESSURE_CHAMBER",
            "SMELTERY",
            "JUICER",
            "ORE_WASHER",
            "TABLE_SAW",
            "ENHANCED_CRAFTING_TABLE",
            "ARMOR_FORGE",
            "MAGIC_WORKBENCH",
            "AUTOMATED_PANNING_MACHINE");

    /*
     * Double output only where it cannot be turned back into its input, else it is an infinite dupe:
     * Grind Stone (bone block <-> bone meal, quartz, lumps...), Ore Crusher (carbon -> coal -> Compressor),
     * Pressure Chamber (coal block -> carbon -> Ore Crusher -> coal), Compressor, Smeltery (dust <-> ingot),
     * crafting tables (blocks <-> ingots). Juices, washed dusts, planks/sticks and panning loot are one-way.
     */
    private static final Set<String> YIELD_MACHINES =
            Set.of("JUICER", "ORE_WASHER", "TABLE_SAW", "AUTOMATED_PANNING_MACHINE");

    private static final SlimefunItemStack[] UPGRADES = new SlimefunItemStack[MAX + 1];

    static {
        Material[] materials = {
            null, Material.COPPER_INGOT, Material.IRON_INGOT, Material.DIAMOND, Material.NETHERITE_INGOT
        };
        String[] names = {"", "&6Bronzo", "&fFerro", "&bDiamante", "&8Netherite"};

        for (int i = 1; i <= MAX; i++) {
            UPGRADES[i] = new SlimefunItemStack(
                    "SKY_MACHINE_UPGRADE_" + i,
                    materials[i],
                    "&ePotenziamento macchina " + names[i] + " " + ROMAN[i],
                    "",
                    "&7Click destro sul blocco che attivi",
                    "&7una macchina multiblocco base",
                    i == 1
                            ? "&7per portarla al &eLivello I"
                            : "&7di Livello " + ROMAN[i - 1] + " per il &eLivello " + ROMAN[i],
                    "",
                    "&e» Fino a " + (1 << i) + " lavorazioni per click",
                    "&e» Attese ridotte del " + (100 - STEP[i] * 5) + "%",
                    "&e» " + YIELD[i] + "% di resa doppia",
                    "&8  (Spremiagrumi, Lavatrice, Sega, Setacciatrice)",
                    "",
                    "&7Rompendo il blocco riavrai i potenziamenti");
        }
    }

    // state of the machine click being handled right now, main thread only
    private static int current;
    private static boolean crafted;
    private static boolean silent;

    private MachineTier() {}

    public static void registerItems(@Nonnull Slimefun plugin) {
        SlimefunItem table = SlimefunItem.getById("ENHANCED_CRAFTING_TABLE");

        if (table == null) {
            return;
        }

        ItemGroup group = table.getItemGroup();
        ItemStack redstone = new ItemStack(Material.REDSTONE);
        ItemStack[][] recipes = {
            null,
            {
                SlimefunItems.BRONZE_INGOT,
                redstone,
                SlimefunItems.BRONZE_INGOT,
                redstone,
                new ItemStack(Material.PISTON),
                redstone,
                SlimefunItems.BRONZE_INGOT,
                redstone,
                SlimefunItems.BRONZE_INGOT
            },
            {
                SlimefunItems.STEEL_INGOT,
                new ItemStack(Material.REDSTONE_BLOCK),
                SlimefunItems.STEEL_INGOT,
                new ItemStack(Material.IRON_BLOCK),
                SlimefunItems.BASIC_CIRCUIT_BOARD,
                new ItemStack(Material.IRON_BLOCK),
                SlimefunItems.STEEL_INGOT,
                new ItemStack(Material.REDSTONE_BLOCK),
                SlimefunItems.STEEL_INGOT
            },
            {
                SlimefunItems.SYNTHETIC_DIAMOND, SlimefunItems.REINFORCED_ALLOY_INGOT, SlimefunItems.SYNTHETIC_DIAMOND,
                SlimefunItems.ELECTRIC_MOTOR, new ItemStack(Material.DIAMOND_BLOCK), SlimefunItems.ELECTRIC_MOTOR,
                SlimefunItems.SYNTHETIC_DIAMOND, SlimefunItems.REINFORCED_ALLOY_INGOT, SlimefunItems.SYNTHETIC_DIAMOND
            },
            {
                SlimefunItems.CARBONADO,
                new ItemStack(Material.NETHERITE_INGOT),
                SlimefunItems.CARBONADO,
                new ItemStack(Material.NETHERITE_INGOT),
                SlimefunItems.ADVANCED_CIRCUIT_BOARD,
                new ItemStack(Material.NETHERITE_INGOT),
                SlimefunItems.CARBONADO,
                new ItemStack(Material.NETHERITE_INGOT),
                SlimefunItems.CARBONADO
            }
        };

        for (int i = 1; i <= MAX; i++) {
            new SlimefunItem(group, UPGRADES[i], RecipeType.ENHANCED_CRAFTING_TABLE, recipes[i]).register(plugin);
        }

        plugin.getServer().getPluginManager().registerEvents(new MachineTier(), plugin);
    }

    public static void registerResearches() {
        int[] costs = {0, 10, 20, 30, 45};

        for (int i = 1; i <= MAX; i++) {
            SlimefunItem item = SlimefunItem.getById("SKY_MACHINE_UPGRADE_" + i);

            if (item != null) {
                Research research = new Research(
                        new NamespacedKey(Slimefun.instance(), "sky_machine_upgrade_" + i),
                        9300 + i,
                        "Potenziamento macchina " + ROMAN[i],
                        costs[i]);
                research.addItems(item);
                research.register();
            }
        }
    }

    // --- level storage ---

    private static @Nonnull NamespacedKey key(@Nonnull Block b) {
        return new NamespacedKey(Slimefun.instance(), "tier_" + b.getX() + "_" + b.getY() + "_" + b.getZ());
    }

    public static int getLevel(@Nonnull Block b) {
        Integer level = b.getChunk().getPersistentDataContainer().get(key(b), PersistentDataType.INTEGER);
        return level == null ? 0 : Math.max(0, Math.min(MAX, level));
    }

    private static void refund(@Nonnull Block b) {
        int level = getLevel(b);

        if (level > 0) {
            // remove first, then drop: the level and the items never coexist
            b.getChunk().getPersistentDataContainer().remove(key(b));
            Location loc = b.getLocation().add(0.5, 0.5, 0.5);

            for (int i = 1; i <= level; i++) {
                b.getWorld().dropItemNaturally(loc, UPGRADES[i].clone());
            }
        }
    }

    // --- context of the current machine click, used by MultiBlockMachine and the machines ---

    /** Starts handling a click and returns how many times the machine should work (1/2/4/8). */
    public static int begin(@Nonnull MultiBlockMachine machine, @Nonnull Block b) {
        current = MACHINES.contains(machine.getId()) ? getLevel(b) : 0;
        crafted = false;
        silent = false;
        return 1 << current;
    }

    /** True if the previous run crafted something; later runs stay silent (no "empty" spam). */
    public static boolean nextRun() {
        boolean result = crafted;
        crafted = false;
        silent = true;
        return result;
    }

    public static void end() {
        current = 0;
        crafted = false;
        silent = false;
    }

    public static int current() {
        return current;
    }

    public static boolean isSilent() {
        return silent;
    }

    /** Ticks of one working step (base 20) for the machines that wait, shorter at higher levels. */
    public static long step() {
        return STEP[current];
    }

    public static @Nonnull Color color(int level) {
        return Color.fromRGB(COLORS[level]);
    }

    public static @Nonnull Component label(int level) {
        return Component.text("Livello " + ROMAN[level], TextColor.color(COLORS[level]));
    }

    private static int upgradeLevel(@Nullable SlimefunItem item) {
        if (item != null) {
            for (int i = 1; i <= MAX; i++) {
                if (item.getId().equals(UPGRADES[i].getItemId())) {
                    return i;
                }
            }
        }

        return 0;
    }

    // --- listeners ---

    @EventHandler(ignoreCancelled = true)
    public void onApply(MultiBlockInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        SlimefunItem sfItem = SlimefunItem.getByItem(hand);
        int target = upgradeLevel(sfItem);

        if (target == 0) {
            return;
        }

        e.setCancelled(true);
        Block b = e.getClickedBlock();

        if (!MACHINES.contains(e.getMultiBlock().getSlimefunItem().getId())) {
            msg(p, "&cQuesto potenziamento funziona solo sulle macchine multiblocco base.");
            return;
        }

        if (!sfItem.canUse(p, true)
                || !Slimefun.getProtectionManager().hasPermission(p, b.getLocation(), Interaction.PLACE_BLOCK)) {
            return;
        }

        int level = getLevel(b);

        if (level >= MAX) {
            msg(p, "&eQuesta macchina è già al livello massimo (IV).");
        } else if (target != level + 1) {
            msg(
                    p,
                    "&cQuesta macchina è al Livello " + (level == 0 ? "base" : ROMAN[level])
                            + ": serve il Potenziamento " + ROMAN[level + 1] + ".");
        } else {
            hand.setAmount(hand.getAmount() - 1);
            b.getChunk().getPersistentDataContainer().set(key(b), PersistentDataType.INTEGER, target);

            Location loc = b.getLocation().add(0.5, 1, 0.5);
            b.getWorld()
                    .spawnParticle(
                            Particle.DUST, loc, 30, 0.4, 0.4, 0.4, new Particle.DustOptions(color(target), 1.5F));
            b.getWorld().playSound(loc, Sound.BLOCK_SMITHING_TABLE_USE, 1, 1);
            msg(p, "&aMacchina potenziata al &eLivello " + ROMAN[target] + "&a!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(MultiBlockCraftEvent e) {
        if (current > 0
                && YIELD_MACHINES.contains(e.getMachine().getId())
                && ThreadLocalRandom.current().nextInt(100) < YIELD[current]) {
            ItemStack output = e.getOutput();

            if (!output.getType().isAir()) {
                ItemStack doubled = output.clone();
                doubled.setAmount(output.getAmount() * 2);
                e.setOutput(doubled);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrafted(MultiBlockCraftEvent e) {
        crafted = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        refund(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        refund(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().forEach(MachineTier::refund);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().forEach(MachineTier::refund);
    }

    // a moved core block would leave its level behind, so upgraded cores cannot be pushed or pulled
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        blockPiston(e.getBlocks(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        blockPiston(e.getBlocks(), e);
    }

    private static void blockPiston(List<Block> blocks, org.bukkit.event.Cancellable e) {
        if (blocks.stream().anyMatch(b -> getLevel(b) > 0)) {
            e.setCancelled(true);
        }
    }

    private static void msg(Player p, String text) {
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
    }
}
