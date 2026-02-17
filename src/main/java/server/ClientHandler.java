package server;

import common.*;

import java.io.*;
import java.net.*;

public class ClientHandler {

    public static void main(String[] args) throws Exception {
        int handlerPort = Integer.parseInt(args[0]);
        int busPort = Integer.parseInt(args[1]);

        System.out.println("CLIENT HANDLER PID: " + ProcessHandle.current().pid()
                + " | handlerPort=" + handlerPort);

        Socket busSocket = new Socket("localhost", busPort);
        ObjectOutputStream busOut = new ObjectOutputStream(busSocket.getOutputStream());

        try (ServerSocket ss = new ServerSocket(handlerPort)) {
            Socket client = ss.accept();

            try (ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
                while (true) {
                    Message msg = (Message) in.readObject();

                    synchronized (busOut) {
                        busOut.writeObject(msg);
                        busOut.flush();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        } finally {
            try { busSocket.close(); } catch (Exception ignored) {}
        }
    }
}
