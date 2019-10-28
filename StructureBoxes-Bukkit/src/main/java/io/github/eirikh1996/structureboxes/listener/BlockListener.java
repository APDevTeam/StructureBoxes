package io.github.eirikh1996.structureboxes.listener;

import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import io.github.eirikh1996.structureboxes.StructureBoxes;
import io.github.eirikh1996.structureboxes.StructureManager;
import io.github.eirikh1996.structureboxes.localisation.I18nSupport;
import io.github.eirikh1996.structureboxes.settings.Settings;
import io.github.eirikh1996.structureboxes.Direction;
import io.github.eirikh1996.structureboxes.utils.IWorldEditLocation;
import io.github.eirikh1996.structureboxes.utils.MathUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static io.github.eirikh1996.structureboxes.utils.ChatUtils.COMMAND_PREFIX;
import static io.github.eirikh1996.structureboxes.utils.RegionUtils.isWithinRegion;
import static java.lang.Math.abs;

public class BlockListener implements Listener {
    private final HashMap<UUID, Long> playerTimeMap = new HashMap<>();

    @EventHandler
    public void onBlockPlace(final BlockPlaceEvent event){
        if (event.isCancelled()){
            return;
        }
        final UUID id = event.getPlayer().getUniqueId();
        if (!event.getBlockPlaced().getType().equals(Settings.StructureBoxItem) &&
        !event.getItemInHand().getItemMeta().hasLore()){
            return;
        }
        if (!event.getItemInHand().getItemMeta().getDisplayName().equals(Settings.StructureBoxLore) &&
                !Settings.AlternativeDisplayNames.contains(ChatColor.stripColor(event.getItemInHand().getItemMeta().getDisplayName()))){
            return;
        }
        List<String> lore = event.getItemInHand().getItemMeta().getLore();
        String schematicID = ChatColor.stripColor(lore.get(0));
        if (!schematicID.startsWith(ChatColor.stripColor(Settings.StructureBoxPrefix))){
            boolean hasAlternativePrefix = false;
            for (String prefix : Settings.AlternativePrefixes){
                if (!schematicID.startsWith(prefix)){
                    continue;
                }
                hasAlternativePrefix = true;
                schematicID = schematicID.replace(prefix, "");
                break;
            }
            if (!hasAlternativePrefix){
                return;
            }
        } else {
            schematicID = schematicID.replace(ChatColor.stripColor(Settings.StructureBoxPrefix), "");
        }
        if (Settings.RequirePermissionPerStructureBox && !event.getPlayer().hasPermission("structureboxes.place." + schematicID)){
            event.getPlayer().sendMessage(String.format(COMMAND_PREFIX + I18nSupport.getInternationalisedString("Place - No permission for this ID"), schematicID));
            return;
        }
        if (playerTimeMap.containsKey(id) && playerTimeMap.get(id) != null && (System.currentTimeMillis() - playerTimeMap.get(id)) < Settings.PlaceCooldownTime){
            event.getPlayer().sendMessage(COMMAND_PREFIX + I18nSupport.getInternationalisedString("Place - Cooldown"));
            return;
        }
        Clipboard clipboard = StructureBoxes.getInstance().getWorldEditHandler().loadClipboardFromSchematic(new BukkitWorld(event.getBlockPlaced().getWorld()), schematicID);
        if (clipboard == null){
            return;
        }
        final Location placed = event.getBlockPlaced().getLocation();
        Direction clipboardDir = StructureBoxes.getInstance().getWorldEditHandler().getClipboardFacingFromOrigin(clipboard, MathUtils.bukkit2SBLoc(placed));
        Direction playerDir = Direction.fromYaw(event.getPlayer().getLocation().getYaw());
        int angle = playerDir.getAngle(clipboardDir);
        final Location loc = event.getBlockPlaced().getLocation();
        boolean exemptFromRegionRestriction = false;
        if (!Settings.RestrictToRegionsExceptions.isEmpty()){
            for (String exception : Settings.RestrictToRegionsExceptions){
                if (exception == null){
                    continue;
                }
                if (ChatColor.stripColor(lore.get(0)).toLowerCase().contains(exception.toLowerCase())){
                    exemptFromRegionRestriction = true;
                    break;
                }

            }
        }
        if (Settings.Debug){
            Bukkit.broadcastMessage("Restrict to regions: " + Settings.RestrictToRegionsEnabled + " Outside region: " + !isWithinRegion(placed) + " Not Exempt: " + !exemptFromRegionRestriction + " unable to bypass : " + !event.getPlayer().hasPermission("structureboxes.bypassregionrestriction"));
        }

        if (Settings.RestrictToRegionsEnabled && !isWithinRegion(placed) && !exemptFromRegionRestriction && !event.getPlayer().hasPermission("structureboxes.bypassregionrestriction")){
            event.getPlayer().sendMessage(COMMAND_PREFIX + I18nSupport.getInternationalisedString("Place - Must be within region"));
            event.setCancelled(true);
            return;
        }
        if (Settings.Debug){
            Bukkit.broadcastMessage("Player direction: " + playerDir.name() + " Structure direction: " + clipboardDir.name());
        }
        final String schemID = schematicID;

                if (!StructureBoxes.getInstance().getWorldEditHandler().pasteClipboard(event.getPlayer().getUniqueId(), schemID, clipboard, angle, new IWorldEditLocation(placed))){

                    event.setCancelled(true);
                    return;
                }


                new BukkitRunnable() {
                    @Override
                    public void run() {
                        StructureManager.getInstance().removeStructure(StructureBoxes.getInstance().getWorldEditHandler().getStructureByPlayer(id));
                        loc.getBlock().setType(Material.AIR);
                    }
                }.runTask(StructureBoxes.getInstance());
        playerTimeMap.put(id, System.currentTimeMillis());




    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event){
        Block b = event.getBlock();
        io.github.eirikh1996.structureboxes.utils.Location structureLoc = new io.github.eirikh1996.structureboxes.utils.Location(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        if (!StructureManager.getInstance().isPartOfStructure(structureLoc)){
            return;
        }
        event.setCancelled(true);
    }


}
