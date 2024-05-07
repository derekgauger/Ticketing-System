package org.incendo.cloudpaper;

import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

import static org.incendo.cloudpaper.Plugin.LOGGER;

public class DatabaseManager {

    private Connection connection;
    private final FileConfiguration config;

    public DatabaseManager(FileConfiguration config) {
        this.config = config;
    }

    public Connection getConnection() {
        return this.connection;
    }

    // Method to connect to the database
    public void connectToDatabase() {
        String databaseType = config.getString("database-type");
        if (databaseType == null) {
            LOGGER.log(Level.SEVERE, "database-type is not setup in the config file.");
            return;
        }
        try {
            if (databaseType.equalsIgnoreCase("mysql")) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } else if (databaseType.equalsIgnoreCase("mariadb")) {
                Class.forName("org.mariadb.jdbc.Driver");
            } else if (databaseType.equalsIgnoreCase("sqlite")) {
                Class.forName("org.sqlite.JDBC");
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            String host = config.getString("database-host");
            String port = config.getString("database-port");
            String name = config.getString("database-name");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + name;
            if (databaseType.equalsIgnoreCase("mariadb")) {
                url = "jdbc:mariadb://" + host + ":" + port + "/" + name;
            } else if (databaseType.equalsIgnoreCase("sqlite")) {
                 url = "jdbc:sqlite://" + host + ":" + port + "/" + name;
            }
            String user = config.getString("database-username"); // Database username
            String password = config.getString("database-password");; // Database password
            connection = DriverManager.getConnection(url, user, password); // Establish connection
            LOGGER.info("Successfully connected to the database");
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if connection fails
        }
    }

    // Method to disconnect from the database
    public void disconnectFromDatabase() {
        try {
            if (connection != null && !connection.isClosed()) { // If connection is not null and is not closed
                connection.close(); // Close the connection
                LOGGER.info("Disconnected from the database."); // Log disconnection
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if disconnection fails
        }
    }

    // Method to create the 'tickets' table in the database if it doesn't exist
    public void createTicketsTable() {
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS tickets (" +
                        "ID INT PRIMARY KEY AUTO_INCREMENT NOT NULL," +
                        "player_uuid VARCHAR(36) NOT NULL," +
                        "username VARCHAR(45) NOT NULL," +
                        "Description VARCHAR(255) NOT NULL," +
                        "Status VARCHAR(45) NOT NULL," +
                        "world VARCHAR(45) NOT NULL," +
                        "x_coord DOUBLE NOT NULL," +
                        "y_coord DOUBLE NOT NULL," +
                        "z_coord DOUBLE NOT NULL," +
                        "pitch DOUBLE NOT NULL," +
                        "yaw DOUBLE NOT NULL," +
                        "creation_time BIGINT NOT NULL" +
                        ")"
        )) {
            statement.executeUpdate(); // Execute SQL statement to create the table
            LOGGER.info("A table called 'tickets' was successfully created in the database");
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if table creation fails
        }
    }

    public int insertTicket(UUID playerUUID, String username, String description, String status, String world, double x, double y, double z, double pitch, double yaw, long creationTime) {
        int ticketId = -1;
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "INSERT INTO tickets (player_uuid, username, Description, Status, world, x_coord, y_coord, z_coord, pitch, yaw, creation_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)){
            preparedStatement.setString(1, playerUUID.toString());
            preparedStatement.setString(2, username);
            preparedStatement.setString(3, description);
            preparedStatement.setString(4, status);
            preparedStatement.setString(5, world);
            preparedStatement.setDouble(6, x);
            preparedStatement.setDouble(7, y);
            preparedStatement.setDouble(8, z);
            preparedStatement.setDouble(9, pitch);
            preparedStatement.setDouble(10, yaw);
            preparedStatement.setLong(11, creationTime);
            preparedStatement.executeUpdate(); // Execute SQL statement to insert the ticket
            ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
            if (generatedKeys.next()) {
                ticketId = generatedKeys.getInt(1);
            }

            LOGGER.info("A ticket with ID " + ticketId + " was successfully inserted into the database table.");
        } catch(SQLException e) {
            e.printStackTrace(); // Print stack trace if ticket insertion fails
        }
        return ticketId;
    }


    // Method to update an entry in the database table
    public void updateTicket(int ticketId, String description, String status) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "UPDATE tickets SET Description = ?, Status = ? WHERE ID = ?")) {
            preparedStatement.setString(1, description); // Set ticket description
            preparedStatement.setString(2, status); // Set ticket status
            preparedStatement.setString(3, String.valueOf(ticketId)); // Set ticket status
            preparedStatement.executeUpdate(); // Execute SQL statement to update the ticket
            LOGGER.info("Ticket with ID " + ticketId + " was successfully updated in the database.");
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if ticket update fails
        }
    }

    // Method to retrieve all information from a row in the database based on ID and player UUID
    public HashMap<String, String> getTicketInfo(int ticketId) {
        HashMap<String, String> ticketInfo = new HashMap<>(); // Create a HashMap to store ticket information
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE ID = ?")) {
            preparedStatement.setInt(1, ticketId); // Set ticket ID in the SQL query
            ResultSet resultSet = preparedStatement.executeQuery(); // Execute SQL query
            if (resultSet.next()) { // Check if a row is found
                int id = resultSet.getInt("ID");
                String description = resultSet.getString("Description");
                String status = resultSet.getString("Status");
                String player_uuid = resultSet.getString("player_uuid");
                String world = resultSet.getString("world");
                String x_coord = resultSet.getString("x_coord");
                String y_coord = resultSet.getString("y_coord");
                String z_coord = resultSet.getString("z_coord");
                String pitch = resultSet.getString("pitch");
                String yaw = resultSet.getString("yaw");
                ticketInfo.put("ID", String.valueOf(id));
                ticketInfo.put("Description", description);
                ticketInfo.put("Status", status);
                ticketInfo.put("player_uuid", player_uuid);
                ticketInfo.put("world", world);
                ticketInfo.put("x_coord", x_coord);
                ticketInfo.put("y_coord", y_coord);
                ticketInfo.put("z_coord", z_coord);
                ticketInfo.put("pitch", pitch);
                ticketInfo.put("yaw", yaw);
            } else {
                LOGGER.info("No ticket found with the specified ID and player UUID.");
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if an error occurs
        }
        return ticketInfo; // Return the HashMap containing ticket information
    }

    // Method to retrieve player's tickets from the database
    public List<HashMap<String, String>> getPlayerTickets(UUID playerUUID) {
        List<HashMap<String, String>> tickets = new ArrayList<>();
        try(PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE player_uuid = ?")){
            preparedStatement.setString(1, playerUUID.toString()); // Set player UUID in the SQL query
            ResultSet resultSet = preparedStatement.executeQuery(); // Execute SQL query
            while (resultSet.next()) {
                HashMap<String, String> ticket = new HashMap<>();
                int id = resultSet.getInt("ID");
                String description = resultSet.getString("Description");
                String status = resultSet.getString("Status");
                String player_uuid = resultSet.getString("player_uuid");
                String username = resultSet.getString("username");
                String world = resultSet.getString("world");
                String x_coord = resultSet.getString("x_coord");
                String y_coord = resultSet.getString("y_coord");
                String z_coord = resultSet.getString("z_coord");
                String pitch = resultSet.getString("pitch");
                String yaw = resultSet.getString("yaw");
                String creation_time = resultSet.getString("creation_time");
                LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(creation_time)), ZoneId.systemDefault());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedDate = dateTime.format(formatter);
                ticket.put("ID", String.valueOf(id));
                ticket.put("Description", description);
                ticket.put("Status", status);
                ticket.put("player_uuid", player_uuid);
                ticket.put("username", username);
                ticket.put("world", world);
                ticket.put("x_coord", x_coord);
                ticket.put("y_coord", y_coord);
                ticket.put("z_coord", z_coord);
                ticket.put("pitch", pitch);
                ticket.put("yaw", yaw);
                ticket.put("creation_time", creation_time);
                ticket.put("formattedDate", formattedDate);
                tickets.add(ticket);
            }
        } catch (SQLException e){
            e.printStackTrace();
        }
        return tickets;
    }




    public List<HashMap<String, String>> getAllTickets() {
        List<HashMap<String, String>> tickets = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT * FROM tickets")) {
            ResultSet resultSet = preparedStatement.executeQuery(); // Execute SQL query
            while (resultSet.next()) { // Iterate through query results
                HashMap<String, String> ticket = new HashMap<>();
                int id = resultSet.getInt("ID");
                String description = resultSet.getString("Description");
                String status = resultSet.getString("Status");
                String player_uuid = resultSet.getString("player_uuid");
                String username = resultSet.getString("username");
                String world = resultSet.getString("world");
                String x_coord = resultSet.getString("x_coord");
                String y_coord = resultSet.getString("y_coord");
                String z_coord = resultSet.getString("z_coord");
                String pitch = resultSet.getString("pitch");
                String yaw = resultSet.getString("yaw");
                String creation_time = resultSet.getString("creation_time");
                LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(creation_time)), ZoneId.systemDefault());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedDate = dateTime.format(formatter);
                ticket.put("ID", String.valueOf(id));
                ticket.put("Description", description);
                ticket.put("Status", status);
                ticket.put("player_uuid", player_uuid);
                ticket.put("username", username);
                ticket.put("world", world);
                ticket.put("x_coord", x_coord);
                ticket.put("y_coord", y_coord);
                ticket.put("z_coord", z_coord);
                ticket.put("pitch", pitch);
                ticket.put("yaw", yaw);
                ticket.put("creation_time", creation_time);
                ticket.put("formattedDate", formattedDate);
                tickets.add(ticket);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if ticket retrieval fails
        }
        return tickets; // Return the list of tickets
    }
    

    public boolean ticketExists(int ticketId) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tickets WHERE ID = ?")) {
            preparedStatement.setInt(1, ticketId); // Set ticket ID in the SQL query
            ResultSet resultSet = preparedStatement.executeQuery(); // Execute SQL query

            if (resultSet.next()) {
                int count = resultSet.getInt(1); // Get the count of rows
                return count > 0; // Return true if count is greater than 0, indicating the ticket exists
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Print stack trace if ticket retrieval fails
        }
        return false; // Return false if an error occurs or the ticket doesn't exist
    }
}
