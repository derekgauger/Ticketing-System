package org.incendo.cloudpaper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;

import static org.incendo.cloudpaper.Plugin.LOGGER;

public class TicketManager implements CommandExecutor, TabCompleter, Listener {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final MiniMessage miniMessage;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final FileConfiguration config;
    private final DiscordManager discordManager;
    private final PermissonsManager permissonsManager;

    public TicketManager(Plugin plugin, DatabaseManager databaseManager, PermissonsManager permissonsManager, FileConfiguration config) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.permissonsManager = permissonsManager;
        this.miniMessage = MiniMessage.miniMessage();
        this.config = config;
        this.discordManager = new DiscordManager(config);
        Objects.requireNonNull(Bukkit.getPluginCommand("ticket")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(commandSender instanceof Player)) {
            LOGGER.log(Level.WARNING, "Only players in-game can execute this command!");
            return true;
        }
        Player player = (Player) commandSender;
        if (label.equalsIgnoreCase("ticket")) {
            int cooldownTime = config.getInt("command-cooldown");
            if (cooldowns.containsKey(player.getUniqueId()) && cooldowns.get(player.getUniqueId()) > System.currentTimeMillis()) {
                player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-cooldown-msg")), Placeholder.parsed("cooldowntime", String.valueOf(cooldownTime))));
                return true;
            }
            if (args.length == 0) {
                handleTicketChatUI(player);
            } else {
                try {
                    String subCommand = args[0];
                    if (subCommand.equalsIgnoreCase("create")) {
                        handleTicketCreation(player, args);
                    } else if (subCommand.equalsIgnoreCase("list")) {
                        handleTicketList(player);
                    } else if (subCommand.equalsIgnoreCase("update")) {
                        handleTicketUpdate(player, args);
                    } else if (subCommand.equalsIgnoreCase("close")) {
                        handleTicketClose(player, args);
                    } else if (subCommand.equalsIgnoreCase("reopen")) {
                        handleTicketReopen(player, args);
                    } else if (subCommand.equalsIgnoreCase("teleport") || subCommand.equalsIgnoreCase("tp")) {
                        handleTeleport(player, args);
                    } else if (subCommand.equalsIgnoreCase("help")) {
                        sendHelpMessage(player);
                    } else if (subCommand.equalsIgnoreCase("group")) {
                        handleTicketGroups(player, args);
                    } else if (subCommand.equalsIgnoreCase("claim")) {
                        handleTicketClaim(player, args);
                    }
                    if (!subCommand.equalsIgnoreCase("help")) {
                        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownTime * 1000L);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }


    /**
     * Method to handle ticket claiming
     * @param player Player who is claiming the ticket
     * @param args Arguments passed to the command
     */
    private void handleTicketClaim(Player player, String[] args) {
        if (!permissonsManager.checkPermission(player, "ticket.claim")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-no-permission"))));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket claim <id></red>"));
            return;
        }
        int id = Integer.parseInt(args[1]);
        if (!databaseManager.ticketExists(id)) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("invalid-ticket-id")))); // Notify player of invalid ticket ID
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(id);
        if (ticketInfo.get("Status").contains("closed")) {
            player.sendMessage(miniMessage.deserialize("<red>Ticket is already closed!</red>"));
            return;
        }
        if (ticketInfo.get("Status").contains("claimed")) {
            player.sendMessage(miniMessage.deserialize("<red>Ticket is already claimed!</red>"));
            return;
        }
        databaseManager.updateTicket(id, ticketInfo.get("Description"), "claimed by " + player.getName());
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("ticket-claimed"))));
        discordManager.PostTicketToDiscord("Claim", String.valueOf(id), player.getName(), player.getName() + " claimed a ticket", config.getString("discord-id"));
    }


    /**
     * Method to handle ticket group assignment
     * @param player Player who issued the command
     * @param args Arguments passed to the command
     */
    private void handleTicketGroups(Player player, String[] args) {
        if (!permissonsManager.checkPermission(player, "ticket.group")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-no-permission"))));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket group <group_name> <username></red>"));
            return;
        }
        String groupName = args[1];
        String username = args[2];
        Player target = Bukkit.getPlayer(username);
        UUID targetUUID;
        String targetUsername;
        if (target == null) {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(username);
            targetUUID = offlineTarget.getUniqueId();
            targetUsername = Bukkit.getOfflinePlayer(username).getName();
        } else {
            targetUUID = target.getUniqueId();
            targetUsername = target.getName();
        }
        if (!permissonsManager.getRoleNames().contains(groupName)) {
            player.sendMessage(miniMessage.deserialize("<red>Invalid group name '" + groupName + "'."));
            return;
        }
        permissonsManager.removePlayerFromAllGroups(targetUUID);
        permissonsManager.addPlayerToGroup(targetUUID, groupName);
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("group-updated")), Placeholder.parsed("username", username), Placeholder.parsed("groupname", groupName)));
    }


    /**
     * Method to handle ticket reopening
     * @param player Player who issued the command
     * @param args Arguments passed to the command
     */
    private void handleTicketReopen(Player player, String[] args) {
        if (!permissonsManager.checkPermission(player, "ticket.reopen")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-no-permission"))));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket reopen <id></red>"));
            return;
        }
        int id = Integer.parseInt(args[1]);
        if (!databaseManager.ticketExists(id)) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("invalid-ticket-id")))); // Notify player of invalid ticket ID
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(id);
        if (ticketInfo.get("Status").contains("open")) {
            player.sendMessage(miniMessage.deserialize("<red>Ticket is already open!</red>"));
            return;
        }
        databaseManager.updateTicket(id, ticketInfo.get("Description"), "open");
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("ticket-reopened"))));
        discordManager.PostTicketToDiscord("Reopen", String.valueOf(id), player.getUniqueId().toString(), player.getName() + " reopened a ticket", config.getString("discord-id"));
    }


    /**
     * Method to handle ticket closing
     * @param player Player who issued the command
     * @param args Arguments passed to the command
     */
    private void handleTicketClose(Player player, String[] args) {
        if (!permissonsManager.checkPermission(player, "ticket.close")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-no-permission"))));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket close <id></red>"));
            return;
        }
        int id = Integer.parseInt(args[1]);
        if (!databaseManager.ticketExists(id)) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("invalid-ticket-id")))); // Notify player of invalid ticket ID
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(id);
        if (!checkPlayerOwnsTicket(player, ticketInfo)) {
            return;
        }
        if (ticketInfo.get("Status").contains("closed")) {
            player.sendMessage(miniMessage.deserialize("<red>Ticket is already closed!</red>"));
            return;
        }
        String newStatus = "closed by admin";
        if (ticketInfo.get("player_uuid").equalsIgnoreCase(player.getUniqueId().toString())) {
            newStatus = "closed by creator";
        }
        databaseManager.updateTicket(id, ticketInfo.get("Description"), newStatus);
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("ticket-closed"))));
        discordManager.PostTicketToDiscord("Close", String.valueOf(id), player.getUniqueId().toString(), newStatus, config.getString("discord-id"));
    }


    /**
     * Method to handle teleporting to a ticket location
     * @param player Player who issued the command
     * @param args Arguments passed to the command
     */
    private void handleTeleport(Player player, String[] args) {
        if (!permissonsManager.checkPermission(player, "ticket.teleport")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-no-permission"))));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket teleport <id></red>"));
            return;
        }
        int id = Integer.parseInt(args[1]);
        if (!databaseManager.ticketExists(id)) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("invalid-ticket-id"))));
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(id);
        double x = Double.parseDouble(ticketInfo.get("x_coord"));
        double y = Double.parseDouble(ticketInfo.get("y_coord"));
        double z = Double.parseDouble(ticketInfo.get("z_coord"));
        World world = getWorldByWorldName(ticketInfo.get("world"));
        if (world == null) {
            LOGGER.log(Level.SEVERE, "Invalid world name '" + ticketInfo.get("world") + "' found in the database!");
            return;
        }
        Location location = new Location(world, x, y, z, Float.parseFloat(ticketInfo.get("yaw")), Float.parseFloat(ticketInfo.get("pitch")));
        player.teleport(location);
        String locationString = "World: " + ticketInfo.get("world") + ", X: " + (int) x + ", Y: " + (int) y + ", Z: " + (int) z;
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("teleported-to-ticket")), Placeholder.parsed("locationstring", locationString)));
    }


    /**
     * Method to get a world by its name
     * @param worldName Name of the world
     * @return World object
     */
    private World getWorldByWorldName(String worldName) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(worldName)) {
                return world;
            }
        }
        return null;
    }


    /**
     * Method to send a help message to the player
     * @param player Player who issued the command
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("helpMessage"))));
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("commandListMessage"))));
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("helpMenuMessage"))));
        if (permissonsManager.checkPermission(player, "ticket.create")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("createTicketMessage"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.update")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("updateTicketMessage"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.close")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("closeTicketMessage"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.reopen")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("reopenTicketMessage"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.list.admin")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("listAllTicketsMessage"))));
        } else if (permissonsManager.checkPermission(player, "ticket.list.default")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("listOwnedTicketsMessage"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.teleport")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("teleportTicketMessage"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.claim")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("claimTicketMessage"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.group")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("assignGroupMessage"))));
        }
    }


    /**
     * Method to handle ticket chat UI
     * @param player Player who issued the command
     */
    private void handleTicketChatUI(Player player) {
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("header-ui"))));
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("help-ui"))));
        if (permissonsManager.checkPermission(player, "ticket.create")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("create-ui"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.update")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("update-ui"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.close")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("close-ui"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.reopen")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("reopen-ui"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.list.admin")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("list-ui"))));
        } else if (permissonsManager.checkPermission(player, "ticket.list.default")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("list-admin-ui"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.teleport")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("teleport-ui"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.claim")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("group-ui"))));
        }
        if (permissonsManager.checkPermission(player, "ticket.group")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("claim-ui"))));
        }
    }


    /**
     * Method to handle ticket creation
     * @param player Player who issued the command
     * @param args Arguments provided with the command
     */
    private void handleTicketCreation(Player player, String[] args) {
        if (!permissonsManager.checkPermission(player, "ticket.create")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-no-permission"))));
            return;
        }
        if (args.length < 2) { // If insufficient arguments provided
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket create <newDescription></red>")); // Provide usage instructions
            return;
        }
        String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length)); // Extract ticket description from arguments
        double x = player.getLocation().getX(); // Get player's X coordinate
        double y = player.getLocation().getY(); // Get player's Y coordinate
        double z = player.getLocation().getZ(); // Get player's Z coordinate
        double pitch = player.getLocation().getPitch();
        double yaw = player.getLocation().getYaw();
        long creationTime = System.currentTimeMillis(); // Get current system time
        int newTicketNum = databaseManager.insertTicket(player.getUniqueId(), player.getName(), description, "open", player.getWorld().getName(), x, y, z, pitch, yaw, creationTime); // Insert ticket into the database
        player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("ticket-created")))); // Notify player that ticket has been submitted
        discordManager.PostTicketToDiscord("Create", String.valueOf(newTicketNum), player.getUniqueId().toString(), description, config.getString("discord-id"));
    }

    /**
     * Method to handle ticket list
     * @param player Player who issued the command
     */
    private void handleTicketList(Player player) {
        if (!permissonsManager.checkPermission(player, "ticket.list.admin") && !permissonsManager.checkPermission(player, "ticket.list.default")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-no-permission"))));
            return;
        }
        // Check if player has permission to view all tickets
        List<HashMap<String, String>> tickets = new ArrayList<>();
        String headMessage = config.getString("ticket-list-admin-head");
        String noTicketsMessage = config.getString("ticket-list-admin-no-tickets");;
        if (permissonsManager.checkPermission(player, "ticket.list.admin")) {
            tickets = databaseManager.getAllTickets(); // Retrieve all tickets from the database
        } else if (permissonsManager.checkPermission(player, "ticket.list.default")){
            UUID playerUUID = player.getUniqueId();
            tickets = databaseManager.getPlayerTickets(playerUUID); // Retrieve player's tickets from the database
            headMessage = config.getString("ticket-list-default-head");;
            noTicketsMessage = config.getString("ticket-list-default-no-tickets");;
        }
        if (tickets.isEmpty()) { // If there are no tickets
            assert noTicketsMessage != null;
            player.sendMessage(miniMessage.deserialize(noTicketsMessage)); // Notify player that there are no tickets
        } else {
            assert headMessage != null;
            player.sendMessage(miniMessage.deserialize(headMessage)); // Notify player that a list of tickets will be displayed
            for (HashMap<String, String> ticket : tickets) { // Iterate through each ticket
                if (!ticket.get("Status").contains("closed")) {
                    player.sendMessage(createTicketMessage(ticket)); // Send ticket information to player
                }
            }
        }
    }


    /**
     * Method to handle ticket update
     * @param player Player who issued the command
     * @param args Arguments provided with the command
     */
    private void handleTicketUpdate(Player player, String[] args) {
        if (!permissonsManager.checkPermission(player, "ticket.update")) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("command-no-permission"))));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(miniMessage.deserialize("<red>Usage: /ticket update <id> <newDescription></red>"));
            return;
        }
        String id = args[1]; // Extract ticket ID from arguments
        String attribute = "description"; // Extract attribute to edit from arguments
        if (!databaseManager.ticketExists(Integer.parseInt(id))) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("invalid-ticket-id")))); // Notify player of invalid ticket ID
            return;
        }
        HashMap<String, String> ticketInfo = databaseManager.getTicketInfo(Integer.parseInt(id));;
        if (!checkPlayerOwnsTicket(player, ticketInfo) && !permissonsManager.checkPermission(player, "ticket.update.others")) {
            return;
        }
        if (attribute.equalsIgnoreCase("description")) { // If player wants to edit ticket description
            String newDescription = String.join(" ", Arrays.copyOfRange(args, 2, args.length)); // Extract new ticket description from arguments
            databaseManager.updateTicket(Integer.parseInt(id), newDescription, ticketInfo.get("Status")); // Update ticket description in the database
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("ticket-updated")))); // Notify player of successful update
        } else {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("ticket-update-failed")))); // Notify player of invalid attribute
        }
    }


    /**
     * Method to create a ticket message
     * @param ticketInfo Ticket information
     */
    private Component createTicketMessage(HashMap<String, String> ticketInfo) {
        return miniMessage.deserialize(Objects.requireNonNull(config.getString("listed-ticked")),
                Placeholder.parsed("description", ticketInfo.get("Description")),
                Placeholder.parsed("id", ticketInfo.get("ID")),
                Placeholder.parsed("username", ticketInfo.get("username")),
                Placeholder.parsed("formatteddate", ticketInfo.get("formattedDate")),
                Placeholder.parsed("status", ticketInfo.get("Status")));
    }


    /**
     * Method to check if a player owns a ticket
     * @param player Player to check
     * @param ticketInfo Ticket information to check
     * @return True if player owns ticket, false otherwise
     */
    public boolean checkPlayerOwnsTicket(Player player, HashMap<String, String> ticketInfo) {
        if (!ticketInfo.get("player_uuid").equalsIgnoreCase(player.getUniqueId().toString())) {
            player.sendMessage(miniMessage.deserialize(Objects.requireNonNull(config.getString("ticket-not-owned-by-you"))));
            return false;
        }
        return true;
    }


    /**
     * Method to add a completion to the tab completion list
     * @param player Player who issued the command
     * @param completions List of completions
     * @param command Command to add
     */
    public void addCompletion(Player player, List<String> completions, String command) {
        if (permissonsManager.checkPermission(player, "ticket." + command)) {
            completions.add(command);
        }
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }
        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("ticket")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                addCompletion(player, completions, "create");
                addCompletion(player, completions, "update");
                addCompletion(player, completions, "close");
                addCompletion(player, completions, "reopen");
                addCompletion(player, completions, "teleport");
                addCompletion(player, completions, "claim");
                addCompletion(player, completions, "group");
                if (permissonsManager.checkPermission(player, "ticket.list.admin") || permissonsManager.checkPermission(player, "ticket.list.default")) {
                    completions.add("list");
                }
                completions.add("help");
                return completions;
            } else if (args.length == 2) {
                String subcommand = args[0];
                subcommand = subcommand.toLowerCase();
                List<String> completions = new ArrayList<>();
                if (permissonsManager.checkPermission(player, "ticket." + subcommand)) {
                    List<String> justIDCommands = new ArrayList<>(Arrays.asList("update", "close", "reopen", "teleport", "tp", "claim"));
                    if (subcommand.equalsIgnoreCase("create")) {
                        completions.add("description");
                    } else if (justIDCommands.contains(subcommand)) {
                        completions.add("<id>");
                    } else if (subcommand.equalsIgnoreCase("group")) {
                        completions.addAll(permissonsManager.getRoleNames());
                    }
                }
                return completions;
            } else if (args.length == 3) {
                String subcommand = args[0];
                subcommand = subcommand.toLowerCase();
                List<String> completions = new ArrayList<>();
                if (permissonsManager.checkPermission(player, "ticket." + subcommand)) {
                    if (subcommand.equalsIgnoreCase("update")) {
                        completions.add("<description>");
                    } else if (subcommand.equalsIgnoreCase("group")) {
                        List<Player> players = (List<Player>) Bukkit.getOnlinePlayers();
                        for (Player p : players) {
                            completions.add(p.getName());
                        }
                    }
                    return completions;
                }
            }
        }
        return null;
    }
}
