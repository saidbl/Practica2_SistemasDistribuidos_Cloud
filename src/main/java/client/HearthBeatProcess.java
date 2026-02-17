package client;

import common.*;

import java.io.*;
import java.net.Socket;

public class HearthBeatProcess {

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int publicPort = Integer.parseInt(args[1]);
        String nodeId = args[2];
        ResourceType type = ResourceType.valueOf(args[3]);

        System.out.println("HEARTBEAT PROCESS PID: " + ProcessHandle.current().pid() + " node=" + nodeId);

        int handlerPort = getRedirectPort(host, publicPort);

        try (Socket s = new Socket(host, handlerPort)) {
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());

            out.writeObject(new Message("REGISTER", new Registration(nodeId, type, "HEARTBEAT")));
            out.flush();

            while (true) {
                synchronized (out) {
                    out.writeObject(new Message("HEARTBEAT", nodeId));
                    out.flush();
                }
                Thread.sleep(2000);
            }
        }
    }

    private static int getRedirectPort(String host, int publicPort) throws Exception {
        try (Socket s = new Socket(host, publicPort)) {
            ObjectInputStream in = new ObjectInputStream(s.getInputStream());
            Message m = (Message) in.readObject();
            if (!"REDIRECT".equals(m.getType())) throw new RuntimeException("Expected REDIRECT");
            return (int) m.getPayload();
        }
    }
}
