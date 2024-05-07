package org.incendo.cloudpaper;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import static org.incendo.cloudpaper.Plugin.LOGGER;

public class PermissonsManager implements Listener {

    private File groupConfigFile;
    private FileConfiguration groupConfig;

    public PermissonsManager(Plugin plugin) {
        groupConfigFile = new File(plugin.getDataFolder(), "group-permissions.yml");
        if (!groupConfigFile.exists()) {
            plugin.saveResource("group-permissions.yml", false);
        }
        groupConfig = YamlConfiguration.loadConfiguration(groupConfigFile);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        for (String groupName : groupConfig.getConfigurationSection("groups").getKeys(false)) {
            List<String> members = groupConfig.getStringList("groups." + groupName + ".members");
            if (members.contains(playerUUID.toString())) {
                return;
            }
        }
        addPlayerToGroup(playerUUID, "default");
    }


    public FileConfiguration getGroupConfig() {
        return groupConfig;
    }

    public void save() {
        try {
            groupConfig.save(groupConfigFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error when trying to save the group-permissions.yml");
        }
    }


    public void addPlayerToGroup(UUID playerUUID, String groupName) {
        List<String> members = groupConfig.getStringList("groups." + groupName + ".members");
        if (!members.contains(playerUUID.toString())) {
            members.add(playerUUID.toString());
            groupConfig.set("groups." + groupName + ".members", members);
            save();
        }
    }

    public void removePlayerFromGroup(UUID playerUUID, String groupName) {
        List<String> members = groupConfig.getStringList("groups." + groupName + ".members");
        if (members.contains(playerUUID.toString())) {
            members.remove(playerUUID.toString());
            groupConfig.set("groups." + groupName + ".members", members);
            save();
        }
    }

    public void removePlayerFromAllGroups(UUID playerUUID) {
        for (String groupName : groupConfig.getConfigurationSection("groups").getKeys(false)) {
            List<String> members = groupConfig.getStringList("groups." + groupName + ".members");
            if (members.contains(playerUUID.toString())) {
                members.remove(playerUUID.toString());
                groupConfig.set("groups." + groupName + ".members", members);
            }
        }
        save();
    }

    public Set<String> getRoleNames() {
        return groupConfig.getConfigurationSection("groups").getKeys(false);
    }

    public boolean checkPermission(Player player, String permission) {
        if (player.isOp()) {
            return true;
        }
        for (String groupName : groupConfig.getConfigurationSection("groups").getKeys(false)) {
            if (groupConfig.getStringList("groups." + groupName + ".members").contains(player.getUniqueId().toString())) {
                if (groupConfig.getStringList("groups." + groupName + ".permissions").contains(permission)) {
                    return true;
                }
            }
        }
        return false;
    }
}
