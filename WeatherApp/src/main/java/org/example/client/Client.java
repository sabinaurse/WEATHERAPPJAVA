package org.example.client;

import java.io.*;
import java.net.*;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to the server.");
            String userRole = ""; // Rolul utilizatorului, setat după autentificare

            // Meniu inițial: LOGIN sau REGISTER
            while (true) {
                System.out.println("Available commands:");
                System.out.println("  1. LOGIN <username> <password>");
                System.out.println("  2. REGISTER <username> <password> <role>");
                System.out.println("Choose an option (1 or 2):");

                String option = userInput.readLine().trim();
                if (option.equals("1")) {
                    System.out.print("Enter LOGIN <username> <password>: ");
                    String loginCommand = userInput.readLine();
                    out.println(loginCommand);
                } else if (option.equals("2")) {
                    System.out.print("Enter REGISTER <username> <password> <role>: ");
                    String registerCommand = userInput.readLine();
                    out.println(registerCommand);
                } else {
                    System.out.println("Invalid option. Please try again.");
                    continue;
                }

                out.flush();

                // Așteaptă răspunsul serverului
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isEmpty()) {
                        break;
                    }
                    response.append(line).append("\n");
                }

                System.out.println("Server response: \n" + response);

                if (response.toString().contains("Login successful")) {
                    // Setează rolul utilizatorului din răspunsul serverului
                    if (response.toString().contains("Role: admin")) {
                        userRole = "admin";
                    } else if (response.toString().contains("Role: user")) {
                        userRole = "user";
                    }
                    break;
                }
            }

            // După autentificare, afișează opțiunile disponibile în funcție de rol
            while (true) {
                System.out.println("Available commands:");
                if (userRole.equals("admin")) {
                    System.out.println("  ADD_USER <username> <password> <role>");
                    System.out.println("  IMPORT_JSON <file_path>");
                    System.out.println("  LIST_USERS");
                }
                System.out.println("  GET_WEATHER <location>");
                System.out.println("  CHANGE_PASSWORD <old_password> <new_password>");
                System.out.println("  LOGOUT");

                System.out.print("Enter a command: ");
                String command = userInput.readLine();
                out.println(command);
                out.flush();

                // Așteaptă răspunsul serverului
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isEmpty()) {
                        break;
                    }
                    response.append(line).append("\n");
                }

                System.out.println("Server response: \n" + response);

                // Dacă utilizatorul dă LOGOUT, încheie sesiunea
                if (command.startsWith("LOGOUT")) {
                    System.out.println("You have logged out. Exiting...");
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
