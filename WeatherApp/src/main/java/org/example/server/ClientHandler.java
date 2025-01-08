package org.example.server;

import java.io.*;
import java.net.*;
import java.sql.*;

public class ClientHandler implements Runnable {
    private final Socket socket;

    // Stare de autentificare și rolul utilizatorului
    private boolean isAuthenticated = false;
    private String userRole = ""; // Poate fi "admin" sau "user"
    private String username = ""; // Numele utilizatorului autentificat

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    // Metodă pentru obținerea informațiilor despre vreme din baza de date
    private String getWeatherForLocation(String location) {
        String query = "SELECT temperature, forecast, state FROM locations WHERE name = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:weather.db");
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, location);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                double temperature = rs.getDouble("temperature");
                String forecast = rs.getString("forecast");
                String[] forecastArray = forecast.split(",");
                String state = rs.getString("state");

                // Returnează un singur răspuns formatat
                return String.format(
                        "Weather in %s:\n- State: %s\n- Today: %.1f°C\n- Forecast: %s°C, %s°C, %s°C",
                        location,
                        state,
                        temperature,
                        forecastArray.length > 0 ? forecastArray[0] : "N/A",
                        forecastArray.length > 1 ? forecastArray[1] : "N/A",
                        forecastArray.length > 2 ? forecastArray[2] : "N/A"
                );
            } else {
                return "No weather information available for " + location;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error accessing the database.";
        }
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String request;
            while ((request = in.readLine()) != null) {
                System.out.println("Received: " + request);

                if (request.startsWith("LOGIN")) {
                    String[] parts = request.split(" ");
                    if (parts.length < 3) {
                        out.println("Error: Invalid login format.");
                        out.println();
                        continue;
                    }
                    String username = parts[1];
                    String password = parts[2];

                    String role = Database.authenticateUser(username, password);
                    if (role != null) {
                        isAuthenticated = true;
                        userRole = role;
                        this.username = username;
                        out.println("Login successful. Role: " + role);
                    } else {
                        isAuthenticated = false;
                        userRole = "";
                        out.println("Login failed: Invalid username or password.");
                    }
                    out.println();
                } else if (request.startsWith("SET_LOCATION")) {
                    if (!isAuthenticated) {
                        out.println("Error: You must log in first.");
                        out.println();
                        continue;
                    }

                    String newLocation = request.substring("SET_LOCATION".length()).trim();
                    System.out.println("Received request to set location to: " + newLocation);

                    if (!Database.isLocationValid(newLocation)) {
                        System.out.println("Location validation failed for: " + newLocation);
                        out.println("Error: Invalid location.");
                        out.println();
                        continue;
                    }

                    boolean success = Database.updateUserLocation(username, newLocation);
                    if (success) {
                        System.out.println("Location updated successfully for user: " + username);
                        out.println("Location updated successfully to " + newLocation + ".");
                    } else {
                        System.out.println("Failed to update location for user: " + username);
                        out.println("Error: Unable to update location.");
                    }
                    out.println();
                }
                    else if (request.startsWith("GET_WEATHER")) {
                    if (!isAuthenticated) {
                        out.println("Error: You must log in first.");
                        out.println();
                        continue;
                    }

                    String location = request.substring("GET_WEATHER".length()).trim();
                    if (location.isEmpty()) {
                        location = Database.getUserLocation(username);
                        if (location == null) {
                            out.println("Error: No location specified and no location set for your account.");
                            out.println();
                            continue;
                        }
                    }

                    String response = getWeatherForLocation(location);
                    out.println(response);
                    out.println();

                } else if (request.startsWith("ADD_USER")) {
                    if (!isAuthenticated) {
                        out.println("Error: You must log in first.");
                        out.println();
                        continue;
                    }
                    if (!userRole.equals("admin")) {
                        out.println("Error: Only admin can add users.");
                        out.println();
                        continue;
                    }

                    String[] parts = request.split(" ");
                    if (parts.length < 5) {
                        out.println("Error: Invalid add user format.");
                        out.println();
                        continue;
                    }
                    String username = parts[1];
                    String password = parts[2];
                    String role = parts[3];
                    String location = parts[4];

                    try {
                        Database.addUser(username, password, role, location);
                        out.println("User added successfully!");
                    } catch (Exception e) {
                        e.printStackTrace();
                        out.println("Error adding user: " + e.getMessage());
                    }
                    out.println();

                } else if (request.startsWith("IMPORT_JSON")) {
                    if (!isAuthenticated) {
                        out.println("Error: You must log in first.");
                        out.println();
                        continue;
                    }
                    if (!userRole.equals("admin")) {
                        out.println("Error: Only admin can import JSON.");
                        out.println();
                        continue;
                    }

                    String filePath = request.substring("IMPORT_JSON".length()).trim();
                    try {
                        Database.importWeatherDataFromJson(filePath);
                        out.println("JSON data imported successfully!");
                    } catch (Exception e) {
                        e.printStackTrace();
                        out.println("Error importing JSON data: " + e.getMessage());
                    }
                    out.println();

                } else if (request.startsWith("CHANGE_PASSWORD")) {
                    if (!isAuthenticated) {
                        out.println("Error: You must log in first.");
                        out.println();
                        continue;
                    }

                    String[] parts = request.split(" ");
                    if (parts.length < 3) {
                        out.println("Error: Invalid change password format.");
                        out.println();
                        continue;
                    }
                    String oldPassword = parts[1];
                    String newPassword = parts[2];

                    boolean success = Database.changePassword(username, oldPassword, newPassword);
                    if (success) {
                        out.println("Password changed successfully!");
                    } else {
                        out.println("Error: Incorrect old password.");
                    }
                    out.println();

                } else if (request.startsWith("LIST_USERS")) {
                    if (!isAuthenticated) {
                        out.println("Error: You must log in first.");
                        out.println();
                        continue;
                    }
                    if (!userRole.equals("admin")) {
                        out.println("Error: Only admin can list users.");
                        out.println();
                        continue;
                    }

                    String users = Database.listUsers();
                    out.println(users);
                    out.println();

                }
                else if (request.startsWith("REGISTER")) {
                    String[] parts = request.split(" ");
                    if (parts.length < 4) {
                        out.println("Error: Invalid register format. Use REGISTER <username> <password> <role>.");
                        out.println(); // Linie goală pentru a marca sfârșitul răspunsului
                        continue;
                    }

                    String username = parts[1];
                    String password = parts[2];
                    String role = parts[3];

                    if (!role.equals("admin") && !role.equals("user")) {
                        out.println("Error: Role must be 'admin' or 'user'.");
                        out.println(); // Linie goală pentru a marca sfârșitul răspunsului
                        continue;
                    }

                    try {
                        // Folosește metoda din Database pentru a adăuga utilizatorul
                        Database.addUser(username, password, role, null); // Locația inițială este null
                        out.println("Registration successful! You can now log in.");
                    } catch (Exception e) {
                        e.printStackTrace();
                        out.println("Error registering user: " + e.getMessage());
                    }
                    out.println(); // Linie goală pentru a marca sfârșitul răspunsului
                }
                else if (request.startsWith("LOGOUT")) {
                    if (!isAuthenticated) {
                        out.println("Error: You are not logged in.");
                        out.println();
                        continue;
                    }

                    isAuthenticated = false;
                    userRole = "";
                    username = "";
                    out.println("Logout successful. You are now logged out.");
                    out.println();

                } else if (request.startsWith("FIND_NEAREST_LOCATION")) {
                    if (!isAuthenticated) {
                        out.println("Error: You must log in first.");
                        out.println();
                        continue;
                    }

                    // Format: FIND_NEAREST_LOCATION <x> <y>
                    String[] parts = request.split(" ");
                    if (parts.length < 3) {
                        out.println("Error: Invalid format. Use FIND_NEAREST_LOCATION <x> <y>.");
                        out.println();
                        continue;
                    }

                    try {
                        double x = Double.parseDouble(parts[1]); // Conversie coordonate x
                        double y = Double.parseDouble(parts[2]); // Conversie coordonate y

                        String response = Database.findNearestLocation(x, y); // Apelează metoda din Database
                        out.println(response);
                    } catch (NumberFormatException e) {
                        out.println("Error: Coordinates must be numbers (including decimals).");
                    }
                    out.println();

                } else {
                    out.println("Error: Invalid request.");
                    out.println();
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
