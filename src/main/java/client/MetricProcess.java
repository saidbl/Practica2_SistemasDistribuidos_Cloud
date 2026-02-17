package client;

import common.*;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class MetricProcess {

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int publicPort = Integer.parseInt(args[1]);
        String nodeId = args[2];
        ResourceType type = ResourceType.valueOf(args[3]);

        System.out.println("METRIC PROCESS PID: " + ProcessHandle.current().pid() + " node=" + nodeId);

        int handlerPort = getRedirectPort(host, publicPort);

        try (Socket s = new Socket(host, handlerPort)) {
            ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());

            out.writeObject(new Message("REGISTER", new Registration(nodeId, type, "METRIC")));
            out.flush();

            Random r = new Random();

            while (true) {
                int value = generateMetric(type, r);

                MetricReport report = new MetricReport(nodeId, type, value);

                synchronized (out) {
                    out.writeObject(new Message("METRIC", report));
                    out.flush();
                }

                Thread.sleep(3000);
            }
        }
    }

    private static int generateMetric(ResourceType type, Random r) {
        return switch (type) {
            case VM -> r.nextInt(100);        
            case CONTAINER -> r.nextInt(120); 
            case DATABASE -> r.nextInt(150);  
            case API_GATEWAY -> r.nextInt(300);
            case STORAGE_NODE -> r.nextInt(100); 
        };
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
