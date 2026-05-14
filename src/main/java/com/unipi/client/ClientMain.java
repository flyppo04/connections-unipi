package com.unipi.client;

import java.io.*;
import java.util.*;

/**
 * Punto di ingresso del client.
 * Legge la configurazione da client.config, inizializza i componenti
 * e avvia il loop principale CLI.
 */
public class ClientMain {

    public static void main(String[] args) {
        Properties config = loadConfig("client.config");

        String host    =                  config.getProperty("server.host",       "localhost");
        int    tcpPort = Integer.parseInt(config.getProperty("server.tcp.port", "5000"));
        int    udpPort = Integer.parseInt(config.getProperty("client.udp.port", "6000"));

        try {
            ServerConnection connection = new ServerConnection(host, tcpPort);
            UdpListener      udpListener = new UdpListener(udpPort);
            ClientCLI        cli        = new ClientCLI(connection, udpListener, udpPort);

            Thread udpThread = new Thread(udpListener, "udp-listener");
            udpThread.setDaemon(true);
            udpThread.start();

            cli.run();

        } catch (IOException e) {
            System.err.println("[CLIENT] Errore di avvio: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Properties loadConfig(String path) {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(path)) {
            p.load(in);
            System.out.println("[CONFIG] Caricata da " + path);
        } catch (FileNotFoundException e) {
            System.out.println("[CONFIG] " + path + " non trovato, uso default.");
        } catch (IOException e) {
            System.err.println("[CONFIG] Errore lettura: " + e.getMessage());
        }
        return p;
    }
}
