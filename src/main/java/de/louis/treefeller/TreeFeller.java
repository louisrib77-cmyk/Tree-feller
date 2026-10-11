package de.louis.treefeller;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class TreeFeller extends JavaPlugin implements Listener {

    private boolean requireAxe;
    private boolean sneakDisables;
    private boolean replant;
    private boolean freeSapling;
    private int maxLogs;
    private int minLeaves;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadSettings() {
        requireAxe = getConfig().getBoolean("require-axe", true);
        sneakDisables = getConfig().getBoolean("sneak-disables", true);
        replant = getConfig().getBoolean("replant", true);
        freeSapling = getConfig().getBoolean("free-sapling", true);
        maxLogs = getConfig().getInt("max-logs", 200);
        minLeaves = getConfig().getInt("min-leaves", 3);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block base = event.getBlock();
        Material logType = base.getType();

        if (!Tag.LOGS_THAT_BURN.isTagged(logType)) return;
        if (logType.name().startsWith("STRIPPED_")) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (!player.hasPermission("treefeller.use")) return;
        if (sneakDisables && player.isSneaking()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (requireAxe && !tool.getType().name().endsWith("_AXE")) return;

        // Nur der unterste Stamm (auf Erde) löst das Fällen aus
        Block below = base.getRelative(BlockFace.DOWN);
        if (!isSoil(below.getType())) return;

        Set<Block> tree = collectTree(base, logType);
        if (tree == null) return; // kein natürlicher Baum

        Material sapling = saplingFor(logType);
        boolean canReplant = replant && sapling != null;
        if (canReplant && !freeSapling) {
            canReplant = player.getInventory().contains(sapling);
        }

        // Das Basisblock-Event bricht Minecraft selbst ab, wir brechen den Rest
        for (Block log : tree) {
            if (log.equals(base)) continue;
            if (tool.getType() == Material.AIR) break;
            log.breakNaturally(tool);
            if (requireAxe || tool.getType().getMaxDurability() > 0) {
                tool.damage(1, player);
                if (player.getInventory().getItemInMainHand().getType() == Material.AIR) break;
            }
        }

        if (canReplant) {
            final Block soil = below;
            final boolean consume = !freeSapling;
            getServer().getScheduler().runTask(this, () -> {
                if (base.getType() == Material.AIR && isSoil(soil.getType())) {
                    if (consume) {
                        if (!player.getInventory().removeItem(new ItemStack(sapling, 1)).isEmpty()) return;
                    }
                    base.setType(sapling);
                }
            });
        }
    }

    /** Sammelt alle verbundenen Stämme. Gibt null zurück, wenn es kein natürlicher Baum ist. */
    private Set<Block> collectTree(Block start, Material logType) {
        Set<Block> logs = new LinkedHashSet<>();
        Set<Block> leaves = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        logs.add(start);

        while (!queue.isEmpty() && logs.size() < maxLogs) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = current.getRelative(dx, dy, dz);
                        Material type = n.getType();
                        if (type == logType) {
                            if (logs.add(n)) {
                                queue.add(n);
                                if (logs.size() >= maxLogs) break;
                            }
                        } else if (Tag.LEAVES.isTagged(type)) {
                            leaves.add(n);
                        }
                    }
                }
            }
        }

        // Schutz: Holzhäuser aus Stämmen haben kaum/keine Blätter
        if (leaves.size() < minLeaves) return null;
        return logs;
    }

    private boolean isSoil(Material m) {
        return switch (m) {
            case DIRT, GRASS_BLOCK, PODZOL, COARSE_DIRT, ROOTED_DIRT, MUD, MUDDY_MANGROVE_ROOTS, MOSS_BLOCK -> true;
            default -> false;
        };
    }

    private Material saplingFor(Material log) {
        String name = log.name();
        if (name.equals("MANGROVE_LOG")) return Material.MANGROVE_PROPAGULE;
        if (!name.endsWith("_LOG")) return null;
        return Material.matchMaterial(name.substring(0, name.length() - 4) + "_SAPLING");
    }
}
