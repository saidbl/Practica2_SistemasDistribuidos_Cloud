package client;

import common.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class Client {

    public static void main(String[] args) throws Exception {

        ResourceType[] types = ResourceType.values();
        ResourceType selectedType = types[new Random().nextInt(types.length)];

        String nodeId = selectedType + "-" + UUID.randomUUID().toString().substring(0,4);

        CloudResource resource = new CloudResource(nodeId, selectedType);

        Socket socket = new Socket("localhost", 5000);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

        out.writeObject(new Message("REGISTER", resource));

        new Thread(() -> sendHeartbeat(out, nodeId)).start();

        Random random = new Random();

        while (true) {

            int load = 0;

            int value;
            String state = resource.getStatus();
            switch(resource.getType()) {

                case VM -> {
                    value = random.nextInt(100); 
                    if (value > 80){
                            state = "CRITICAL";
                        }else
                        if(value < 30){
                            state = "LOW";
                        }else{
                            state = "NORMAL";
                        }
                    load = value;
                }
                case CONTAINER -> {
                    value = random.nextInt(120); 
                        if (value > 70){
                            state = "CRITICAL";
                        }else
                        if(value < 20){
                            state = "LOW";
                        }else{
                            state = "NORMAL";
                        }
                    load = value;
                }

                case DATABASE -> {
                    value = random.nextInt(150);
                    if (value >= 100){
                            state = "CRITICAL";
                        }else{
                            state = "NORMAL";
                        }
                    load = value;
                }

                case API_GATEWAY -> {
                    value = random.nextInt(300); 
                    if (value > 200){
                            state = "CRITICAL";
                        }else
                        if(value < 80){
                            state = "LOW";
                        }else{
                            state = "NORMAL";
                        }
                    load = value;
                }

                case STORAGE_NODE -> {
                    value = random.nextInt(100); 
                    if (value >= 85){
                            state = "CRITICAL";

                        }else{
                            state = "NORMAL";
                        }
                    load = value;
                }
            }
            MetricReport report = new MetricReport(nodeId, load, state);

            synchronized (out) {
                out.writeObject(new Message("METRIC", report));
            }

            Thread.sleep(3000);
        }
    }

    private static void sendHeartbeat(ObjectOutputStream out, String nodeId) {
        try {
            while (true) {
                synchronized (out) {
                    out.writeObject(new Message("HEARTBEAT", nodeId));
                }
                Thread.sleep(2000);
            }
        } catch (Exception ignored) {}
    }
}
