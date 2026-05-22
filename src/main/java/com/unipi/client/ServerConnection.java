package com.unipi.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/* Gestisce la connessione TCP NIO non bloccante verso il server
Usa SocketChannel + Selector in modalità non bloccante
Il metodo sendAndReceive() è sincrono dal punto di vista del chiamante
(CLI), ma internamente usa il Selector per attendere la risposta
senza bloccare il thread in modo "busy-wait"
Il delimitatore di messaggio è \n, coerente con ServerMain */

public class ServerConnection {

    private static final int BUFFER_SIZE    = 4096;
    private static final int TIMEOUT_MS     = 5000;

    private final SocketChannel channel;
    private final Selector      selector;
    private final ByteBuffer    readBuffer;
    private final StringBuilder partialMessage = new StringBuilder();

    public ServerConnection(String host, int port) throws IOException {
        selector = Selector.open();

        channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(host, port));
        channel.register(selector, SelectionKey.OP_CONNECT);

        // Attendi completamento connessione
        if (selector.select(TIMEOUT_MS) == 0) {
            throw new IOException("Timeout connessione al server " + host + ":" + port);
        }
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            it.remove();
            if (key.isConnectable()) {
                if (channel.isConnectionPending()) {
                    channel.finishConnect();
                }
            }
        }

        // Dopo la connessione, registra per READ
        channel.register(selector, SelectionKey.OP_READ);

        readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        System.out.println("[CLIENT] Connesso a " + host + ":" + port);
    }

    /* Invia un messaggio JSON al server e attende la risposta     
    @param request JsonObject da inviare
    @return JsonObject risposta del server, o null in caso di errore */

    public synchronized JsonObject sendAndReceive(JsonObject request) throws IOException {
        // Invia il messaggio con delimitatore \n
        String msg = request.toString() + "\n";
        ByteBuffer writeBuffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));

        // Scrittura non bloccante: cicla finché non ha scritto tutto
        while (writeBuffer.hasRemaining()) {
            channel.write(writeBuffer);
        }

        //si può togliere commento per verificare cosa viene mandato 
        //System.out.println("[OUT] " + request);

        // Attendi risposta tramite Selector (non bloccante)
        return readResponse();
    }

    /* Legge dal canale finché non trova un messaggio completo (delimitato da \n)
    Usa il Selector per attendere dati disponibili senza bloccare */

    private JsonObject readResponse() throws IOException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            // Controlla prima se c'è già un messaggio completo nel buffer parziale
            String complete = extractCompleteMessage(partialMessage);
            if (complete != null) {
                System.out.println("[IN] " + complete);
                return JsonParser.parseString(complete).getAsJsonObject();
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;

            int ready = selector.select(remaining);
            if (ready == 0) continue;

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (key.isReadable()) {
                    readBuffer.clear();
                    int bytesRead = channel.read(readBuffer);

                    if (bytesRead == -1) {
                        throw new IOException("Connessione chiusa dal server");
                    }
                    if (bytesRead > 0) {
                        readBuffer.flip();
                        byte[] data = new byte[readBuffer.remaining()];
                        readBuffer.get(data);
                        partialMessage.append(new String(data, StandardCharsets.UTF_8));
                    }
                }
            }
        }

        // Ultimo tentativo dopo l'ultimo ciclo di lettura
        String complete = extractCompleteMessage(partialMessage);
        if (complete != null) {
            //si può togliere commento per verificare cosa viene ricevuto 
            //System.out.println("[IN] " + complete);
            return JsonParser.parseString(complete).getAsJsonObject();
        }

        throw new IOException("Timeout: nessuna risposta dal server entro " + TIMEOUT_MS + "ms");
    }

    private String extractCompleteMessage(StringBuilder buffer) {
        int idx = buffer.indexOf("\n");
        if (idx == -1) return null;
        String msg = buffer.substring(0, idx).trim();
        buffer.delete(0, idx + 1);
        return msg.isEmpty() ? null : msg;
    }

    public void close() {
        try { channel.close();  } catch (IOException ignored) {}
        try { selector.close(); } catch (IOException ignored) {}
    }
}
