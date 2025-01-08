package org.example.server;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;


public class Database {
    private static final String DB_URL = "jdbc:sqlite:weather.db";

    // Metoda pentru inițializarea bazei de date
    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement stmt = conn.createStatement();

            // Șterge tabela existentă dacă nu este în formatul dorit (opțional)
            String dropLocationsTable = "DROP TABLE IF EXISTS locations";
            stmt.execute(dropLocationsTable);

            // Creează tabela pentru locații cu noile coloane
            String createLocationsTable = "CREATE TABLE IF NOT EXISTS locations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "latitude REAL, " +
                    "longitude REAL, " +
                    "temperature REAL, " +
                    "forecast TEXT, " +
                    "state TEXT)";
            stmt.execute(createLocationsTable);

            // Creează tabela pentru utilizatori
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT NOT NULL UNIQUE, " +
                    "password TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "location TEXT)";
            stmt.execute(createUsersTable);

            System.out.println("Database initialized successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Metoda pentru importarea datelor dintr-un fișier JSON
    public static void importWeatherDataFromJson(String filePath) {
        try (FileReader reader = new FileReader(filePath);
             Connection conn = DriverManager.getConnection(DB_URL)) {

            // Parsează JSON-ul
            Gson gson = new Gson();
            Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> locations = gson.fromJson(reader, listType);

            // Pregătește interogarea SQL
            String insertData = "INSERT OR IGNORE INTO locations (name, latitude, longitude, temperature, forecast, state) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(insertData);

            for (Map<String, Object> location : locations) {
                String name = (String) location.get("name");
                int latitude = ((Number) location.get("latitude")).intValue();
                int longitude = ((Number) location.get("longitude")).intValue();
                double temperature = ((Number) location.get("temperature")).doubleValue();
                List<Double> forecastList = (List<Double>) location.get("forecast");
                String forecast = forecastList.stream()
                        .map(String::valueOf)
                        .reduce((day1, day2) -> day1 + "," + day2)
                        .orElse("");
                String state = (String) location.get("state");

                pstmt.setString(1, name);
                pstmt.setInt(2, latitude);
                pstmt.setInt(3, longitude);
                pstmt.setDouble(4, temperature);
                pstmt.setString(5, forecast);
                pstmt.setString(6, state);
                pstmt.executeUpdate();
            }

            System.out.println("Weather data imported successfully from JSON!");

        } catch (IOException e) {
            System.err.println("Error reading JSON file: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Error updating database: " + e.getMessage());
        }
    }

    public static boolean isLocationValid(String location) {
        String query = "SELECT COUNT(*) FROM locations WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, location);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0; // Verifică dacă locația există
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


    public static void addUser(String username, String password, String role, String location) {
        String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
        String insertQuery = "INSERT INTO users (username, password, role, location) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement checkStmt = conn.prepareStatement(checkQuery);
             PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {

            // Verifică dacă utilizatorul există deja
            checkStmt.setString(1, username);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                throw new RuntimeException("User " + username + " already exists.");
            }

            // Adaugă utilizatorul în baza de date
            insertStmt.setString(1, username);
            insertStmt.setString(2, password);
            insertStmt.setString(3, role);
            insertStmt.setString(4, location); // Locația poate fi null
            insertStmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error adding user to database: " + e.getMessage());
        }
    }


    public static String authenticateUser(String username, String password) {
        String query = "SELECT role FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password); // Într-un sistem real, parola trebuie comparată criptată!
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("role"); // Returnează rolul utilizatorului (e.g., "admin" sau "user")
            } else {
                return null; // Nicio potrivire
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean changePassword(String username, String oldPassword, String newPassword) {
        String query = "UPDATE users SET password = ? WHERE username = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, newPassword);
            pstmt.setString(2, username);
            pstmt.setString(3, oldPassword);

            int rowsUpdated = pstmt.executeUpdate();
            return rowsUpdated > 0; // Returnează true dacă parola a fost actualizată
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String listUsers() {
        String query = "SELECT username, role FROM users";
        StringBuilder result = new StringBuilder("Users:\n");

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String username = rs.getString("username");
                String role = rs.getString("role");
                result.append("- ").append(username).append(" (").append(role).append(")\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error retrieving users.";
        }
        return result.toString();
    }

    public static boolean updateUserLocation(String username, String newLocation) {
        if (!isLocationValid(newLocation)) {
            System.out.println("Invalid location: " + newLocation);
            return false; // Return false dacă locația nu este validă
        }

        String query = "UPDATE users SET location = ? WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, newLocation);
            pstmt.setString(2, username);

            int rowsUpdated = pstmt.executeUpdate();
            if (rowsUpdated > 0) {
                System.out.println("User location updated successfully for: " + username);
                return true;
            } else {
                System.out.println("No user found with username: " + username);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error updating user location in database: " + e.getMessage());
            return false;
        }
    }

    public static String getUserLocation(String username) {
        String query = "SELECT location FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("location"); // Returnează locația curentă
            } else {
                return null; // Utilizatorul nu are locație setată
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }


    public static String findNearestLocation(double x, double y) {
        String query = "SELECT name, latitude, longitude, " +
                "       (POWER(latitude - ?, 2) + POWER(longitude - ?, 2)) AS distance " +
                "FROM locations " +
                "ORDER BY distance ASC " +
                "LIMIT 1";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setDouble(1, x);
            pstmt.setDouble(2, y);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String nearestLocation = rs.getString("name");
                    double distance = Math.sqrt(rs.getDouble("distance"));
                    return String.format("Nearest location: %s (%.2f units away)", nearestLocation, distance);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return "No locations found.";
    }



}
