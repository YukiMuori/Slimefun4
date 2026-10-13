package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import city.norain.slimefun4.utils.EnvUtil;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import io.papermc.lib.PaperLib;
import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;

/**
 * This is our class for the /sf versions subcommand.
 *
 * @author TheBusyBiscuit
 * @author Walshy
 *
 */
class VersionsCommand extends SubCommand {

    /**
     * This is the Java version we recommend to use.
     * Bump as necessary and adjust the warning.
     */
    private static final int RECOMMENDED_JAVA_VERSION = 16;

    /**
     * This is the notice that will be displayed when an
     * older version of Java is detected.
     */
    private static final String JAVA_VERSION_NOTICE = "Da Minecraft 1.17 serve Java 16+!";

    @ParametersAreNonnullByDefault
    VersionsCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "versions", false);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (sender.hasPermission("slimefun.command.versions") || sender instanceof ConsoleCommandSender) {
            /*
             * After all these years... Spigot still displays as "CraftBukkit".
             * so we will just fix this inconsistency for them :)
             */
            String serverSoftware = PaperLib.isSpigot() && !PaperLib.isPaper() ? "Spigot" : Bukkit.getName();

            net.kyori.adventure.text.TextComponent.Builder builder = Component.text();

            builder.append(Component.text("Ambiente del server Slimefun:\n", Style.style(NamedTextColor.GRAY)))
                    .append(Component.text(serverSoftware, Style.style(NamedTextColor.GREEN))
                            .append(Component.text(
                                    " " + Bukkit.getVersion() + '\n', Style.style(NamedTextColor.DARK_GREEN))))
                    .append(Component.text("Slimefun ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(
                            Slimefun.getVersion()
                                    + (Slimefun.getVersion()
                                                    .toLowerCase(Locale.ROOT)
                                                    .contains("release")
                                            ? ""
                                            : " @" + EnvUtil.getBranch())
                                    + '\n',
                            Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("Data build ", Style.style(NamedTextColor.GREEN)))
                    .append(Component.text(EnvUtil.getBuildTime(), Style.style(NamedTextColor.DARK_GREEN)))
                    .append(Component.text("\n"));

            // @formatter:on

            if (Slimefun.getMetricsService().getVersion() != null) {
                // @formatter:off
                builder.append(Component.text("Componente Metrics ", Style.style(NamedTextColor.GREEN)))
                        .append(Component.text(
                                "#" + Slimefun.getMetricsService().getVersion() + '\n',
                                Style.style(NamedTextColor.DARK_GREEN)));
                // @formatter:on
            }

            addJavaVersion(builder);

            // Declare that we are NOT OFFICIAL build so no support from upstream
            builder.append(Component.text("\nFork cinese di StarWishsama", Style.style(NamedTextColor.WHITE)))
                    .append(Component.text(
                            "\nNon segnalare bug di questa versione su Discord/GitHub ufficiali\nusa la pagina del fork\n",
                            Style.style(NamedTextColor.RED)));

            if (Slimefun.getConfigManager().isBypassEnvironmentCheck()) {
                builder.append(Component.text(
                        "\n\nControllo compatibilità ambiente disattivato", Style.style(NamedTextColor.RED)));
            }

            if (Slimefun.getConfigManager().isBypassItemLengthCheck()) {
                builder.append(
                        Component.text("\n\nControllo lunghezza oggetti disattivato", Style.style(NamedTextColor.RED)));
            }

            builder.append(Component.text("\n"));
            addPluginVersions(builder);

            sender.sendMessage(builder.build());
        } else {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
        }
    }

    private void addJavaVersion(@Nonnull net.kyori.adventure.text.TextComponent.Builder builder) {
        int version = NumberUtils.getJavaVersion();

        if (version < RECOMMENDED_JAVA_VERSION) {
            Component hover = Component.text("La tua versione di Java è obsoleta!\n!"
                    + "Si consiglia Java "
                    + RECOMMENDED_JAVA_VERSION
                    + " o superiore.\n"
                    + JAVA_VERSION_NOTICE);

            builder.append(Component.text("Java " + version, NamedTextColor.RED).hoverEvent(HoverEvent.showText(hover)))
                    .append(Component.text("\n"));
        } else {
            builder.append(Component.text("Java ", NamedTextColor.GREEN))
                    .append(Component.text(version + "\n", NamedTextColor.DARK_GREEN));
        }
    }

    @SuppressWarnings("deprecation")
    private void addPluginVersions(@Nonnull net.kyori.adventure.text.TextComponent.Builder builder) {
        Collection<Plugin> addons = Slimefun.getInstalledAddons();

        if (addons.isEmpty()) {
            builder.append(Component.text("Nessun addon installato", NamedTextColor.GRAY)
                    .decorate(TextDecoration.ITALIC));
            return;
        }

        builder.append(Component.text("Addon installati: ", NamedTextColor.GRAY))
                .append(Component.text("(" + addons.size() + ")", NamedTextColor.DARK_GRAY));

        for (Plugin plugin : addons) {
            String version = plugin.getDescription().getVersion();

            HoverEvent<Component> hoverEvent;
            ClickEvent clickEvent = null;
            NamedTextColor primaryColor;
            NamedTextColor secondaryColor;

            if (Bukkit.getPluginManager().isPluginEnabled(plugin)) {
                primaryColor = NamedTextColor.GREEN;
                secondaryColor = NamedTextColor.DARK_GREEN;
                String authors = String.join(", ", plugin.getDescription().getAuthors());

                if (plugin instanceof SlimefunAddon addon && addon.getBugTrackerURL() != null) {

                    try {
                        String url = addon.getBugTrackerURL();
                        if (url != null) {
                            URI uri = URI.create(!url.contains("://") ? "https://" + url : url);
                            clickEvent = ClickEvent.openUrl(uri.toString());
                        }
                        Component hoverComp = Component.text()
                                .append(Component.text("Autori: ", NamedTextColor.YELLOW))
                                .append(Component.text(authors, NamedTextColor.YELLOW))
                                .append(Component.text(
                                        "\n> Clicca per aprire la pagina delle segnalazioni", NamedTextColor.GOLD))
                                .build();

                        hoverEvent = HoverEvent.showText(hoverComp);
                    } catch (IllegalArgumentException e) {
                        Component hoverComp = Component.text()
                                .append(Component.text("Autori: ", NamedTextColor.YELLOW))
                                .append(Component.text(authors, NamedTextColor.YELLOW))
                                .append(Component.text(
                                        "\n> Il link per le segnalazioni dell'addon non è valido!", NamedTextColor.RED))
                                .build();

                        hoverEvent = HoverEvent.showText(hoverComp);
                    }

                } else {
                    Component hoverComp = Component.text()
                            .append(Component.text("Autori: ", NamedTextColor.YELLOW))
                            .append(Component.text(authors, NamedTextColor.YELLOW))
                            .build();

                    hoverEvent = HoverEvent.showText(hoverComp);
                }
            } else {
                primaryColor = NamedTextColor.RED;
                secondaryColor = NamedTextColor.DARK_RED;

                if (plugin instanceof SlimefunAddon addon && addon.getBugTrackerURL() != null) {
                    try {
                        String url = addon.getBugTrackerURL();
                        if (url != null) {
                            URI uri = URI.create(!url.contains("://") ? "https://" + url : url);
                            clickEvent = ClickEvent.openUrl(uri.toString());
                        }
                        Component hoverComp = Component.text()
                                .append(Component.text(
                                        "Questo plugin è disattivato.\nControlla gli errori in console.",
                                        NamedTextColor.RED))
                                .append(Component.text(
                                        "\n> Clicca per aprire la pagina delle segnalazioni", NamedTextColor.DARK_RED))
                                .build();

                        hoverEvent = HoverEvent.showText(hoverComp);
                    } catch (IllegalArgumentException e) {
                        Component hoverComp = Component.text()
                                .append(Component.text(
                                        "Questo plugin è disattivato.\nControlla gli errori in console.",
                                        NamedTextColor.RED))
                                .append(Component.text(
                                        "\n> Il link per le segnalazioni del plugin non è valido",
                                        NamedTextColor.DARK_RED))
                                .build();

                        hoverEvent = HoverEvent.showText(hoverComp);
                    }
                } else {
                    Component hoverComp = Component.text("Plugin disattivato, controlla gli errori in console.");
                    hoverEvent = HoverEvent.showText(hoverComp);
                }
            }

            Component nameComp =
                    Component.text("\n  " + plugin.getName(), primaryColor).hoverEvent(hoverEvent);

            if (clickEvent != null) nameComp = nameComp.clickEvent(clickEvent);

            Component versionComp = Component.text(" v" + version, secondaryColor);

            builder.append(nameComp).append(versionComp);
        }
    }
}
