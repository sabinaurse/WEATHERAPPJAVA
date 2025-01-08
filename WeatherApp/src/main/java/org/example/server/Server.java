package org.example.server;

import java.io.*;
import java.net.*;

public class Server {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        // Inițializează baza de date
        Database.initializeDatabase();

        String jsonFilePath = "D:/weather.json"; // Înlocuiește cu calea ta.
        try {
            Database.importWeatherDataFromJson(jsonFilePath);
            System.out.println("Initial data imported from JSON.");
        } catch (Exception e) {
            System.err.println("Failed to import initial data from JSON: " + e.getMessage());
        }

        // Pornește serverul
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
