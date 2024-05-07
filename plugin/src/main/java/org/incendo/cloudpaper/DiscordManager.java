package org.incendo.cloudpaper;

import org.bukkit.configuration.file.FileConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.incendo.cloudpaper.Plugin.LOGGER;

public class DiscordManager {

    private final String webhookUrl;
    private final FileConfiguration config;

    public DiscordManager(FileConfiguration config) {
        this.config = config;
        this.webhookUrl = this.config.getString("webhook");
    }

    public void PostTicketToDiscord(String event, String id, String userId, String message, String discordId) {
       try {
           URL url = new URL(webhookUrl);
           HttpURLConnection http = (HttpURLConnection) url.openConnection();
           http.addRequestProperty("Content-Type", "application/json");
           http.addRequestProperty("User-Agent", "Java-DiscordWebhook-BY-Gelox_");
           http.setDoOutput(true);
           http.setRequestMethod("POST");
           String jsonInputString =
                   "{\n\t\"event\": \"" + event + "\", \n" +
                   "\t\"id\": \"" + id + "\", \n" +
                   "\t\"user-uuid\": \"" + userId + "\", \n" +
                   "\t\"message\": \"" + message + "\", \n" +
                   "\t\"discord-id\": \"" + discordId + "\"\n}";
           jsonInputString = jsonInputString.replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
           String discordJson = "{\"content\": \"" + jsonInputString + "\", \"username\": \"" + config.getString("discord-webhook-username") + "\"}";

           OutputStream stream = http.getOutputStream();
           stream.write(discordJson.getBytes("UTF-8"));
           stream.flush();
           stream.close();

           http.getInputStream().close(); // You can also check the HTTP response code here
           http.disconnect();

           LOGGER.info(event + " message sent to Discord successfully!");
       } catch (Exception e) {
           LOGGER.severe("Failed to send message to Discord: " + e.getMessage());
       }
    }
}
