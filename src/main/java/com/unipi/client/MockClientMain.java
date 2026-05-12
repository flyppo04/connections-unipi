package com.unipi.client;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MockClientMain {
    private static final String HOST = "localhost";
    private static final int PORT = 5000; // cambia con la tua porta

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT);
             DatagramSocket udpSocket = new DatagramSocket()
            ) {
            int udpPort = udpSocket.getLocalPort();
            System.out.println("Connesso a " + HOST + ":" + PORT);
            System.out.println("UDP in ascolto sulla porta " + udpPort);

            // Thread 1: LETTORE sempre attivo (per notifiche asincrone)
            Thread readerThread = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    
                    String line;
                    while ((line = in.readLine()) != null) {
                        handleServerMessage(line);
                    }
                } catch (IOException e) {
                    System.err.println("[ERROR] Lettore disconnesso: " + e.getMessage());                }
            });

            readerThread.setDaemon(true);
            readerThread.start();


            // thread UDP listener
            Thread udpThread = new Thread(() -> {
                byte[] buf = new byte[4096];
                while (!udpSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        udpSocket.receive(packet);
                        String msg = new String(
                            packet.getData(),
                            0,
                            packet.getLength(),
                            StandardCharsets.UTF_8
                        );
                        System.out.println("\n[UDP] " + msg);
                        System.out.print("> ");
                    } catch (IOException e) {
                        if (!udpSocket.isClosed()) {
                            System.err.println("[ERROR] UDP listener: " + e.getMessage());
                        }
                        break;
                    }
                }
            });
            
            udpThread.setDaemon(true);
            udpThread.start();

            // Thread 2: INPUT da tastiera
            try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                                                                               StandardCharsets.UTF_8)
                                                       )
                ) {

                System.out.println("  Comandi disponibili:");
                System.out.println("  register: {\"operation\":\"register\",\"name\":\"test\",\"psw\":\"123\"}");
                System.out.println("  login: {\"operation\":\"login\",\"username\":\"test\",\"psw\":\"123\"}");
                System.out.println("  submitProposal: {\"operation\":\"submitProposal\",\"words\":[\"W1\",\"W2\",\"W3\",\"W4\"]}");
                System.out.println("  updateCredentials: {\"operation\":\"updateCredentials\",\"oldPsw\":\"...\",\"newName\":\"...\",\"newPsw\":\"...\"}");
                System.out.println("  requestGameInfo: {\"operation\":\"requestGameInfo\",\"gameId\":-1}");
                System.out.println("  requestGameStats: {\"operation\":\"requestGameInfo\",\"gameId\":-1}");
                System.out.println("  requestLeaderboard: {\"operation\":\"requestLeaderboard\"}");
                System.out.println("  requestLeaderboard: {\"operation\":\"requestLeaderboard\",\"topPlayers\":\"10\"}");
                System.out.println("  requestLeaderboard: {\"operation\":\"requestLeaderboard\",\"playerName\":\"mario\"}");
                System.out.println("  requestPlayerStats: {\"operation\":\"requestPlayerStats\"}");
                System.out.println("  logout: {\"operation\":\"logout\"}");
                System.out.println("  exit: per chiudere");

                while (true) {
                    System.out.print("\n> ");
                    String line = scanner.nextLine().trim();
                    if ("exit".equalsIgnoreCase(line)) break;

                    if (line.isEmpty()) continue;

                    out.write(line);
                    out.newLine();
                    out.flush();
                    System.out.println(" Inviato: " + line);
                }
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Errore client: " + e.getMessage());        }
    }

    private static void handleServerMessage(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            if (obj.has("type") && "newGame".equals(obj.get("type").getAsString())) {
                handleNewGame(obj);
                return;
            }

            System.out.println("Risposta server: " + json);
        } catch (Exception e) {
            System.out.println("Messaggio server: " + json);
        }
    }

    private static void handleNewGame(JsonObject obj) {
        System.out.println();
        System.out.println(" === NUOVA PARTITA AVVISTATA! ===");
        System.out.println("Game ID: " + obj.get("gameId").getAsInt());
        System.out.println("Tempo rimanente: " + obj.get("remainingTime").getAsInt() + "s");
        System.out.println("Stato iniziale:");
        System.out.println("  Errori: " + obj.get("errors").getAsInt());
        System.out.println("  Score: " + obj.get("score").getAsInt());
        System.out.println("  Gruppi corretti: " + obj.get("correctGroups").getAsInt());

        JsonObject wordsObj = obj.getAsJsonArray("words").getAsJsonObject();
        System.out.println("Parole (16):");
        obj.getAsJsonArray("words").forEach(word -> 
            System.out.println("  • " + word.getAsString())
        );
        System.out.println("=====================================");
    }
}