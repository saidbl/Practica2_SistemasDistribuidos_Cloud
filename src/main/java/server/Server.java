package server;

import common.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {

    private static final int PORT = 5000;
    private static final long TIMEOUT = 8000;

    private static final Map<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private static final Map<ResourceType, List<CloudResource>> cluster = new ConcurrentHashMap<>();


    public static void main(String[] args) throws Exception {

        System.out.println(" CLOUD SERVER iniciado en puerto " + PORT);
        for (ResourceType type : ResourceType.values()) {
            cluster.put(type, new CopyOnWriteArrayList<>());
        }

        ServerSocket serverSocket = new ServerSocket(PORT);

        new Thread(Server::monitorNodes).start();

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> handleClient(socket)).start();
        }
    }

    private static void handleClient(Socket socket) {

        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            while (true) {

                Message msg = (Message) in.readObject();

                switch (msg.getType()) {

                    case "REGISTER" -> {
                        CloudResource resource = (CloudResource) msg.getPayload();
                        cluster.get(resource.getType()).add(resource);

                        nodes.put(resource.getId(),
                                new NodeInfo(resource.getId(), socket, out));

                        System.out.println(" Nodo conectado: " + resource.getId()
                                + " Tipo: " + resource.getType());
                    }

                    case "HEARTBEAT" -> {
                        String nodeId = (String) msg.getPayload();
                        NodeInfo node = nodes.get(nodeId);
                        if (node != null) node.updateHeartbeat();
                    }

                    case "METRIC" -> processMetric((MetricReport) msg.getPayload());
                }
            }

        } catch (Exception e) {
            System.out.println("️ Nodo desconectado.");
        }
    }

    private static void processMetric(MetricReport report) {

        for (List<CloudResource> list : cluster.values()) {

            for (CloudResource resource : list) {

                if (resource.getId().equals(report.getNodeId())) {

                    resource.setLoad(report.getValue());

                    System.out.println("[METRIC] "
                            + resource.getType()
                            + " " + resource.getId()
                            + " Load=" + report.getValue()
                            + " Status="+ report.getState());
                        switch (resource.getType()) {

                            case VM -> {
                                System.out.println(resource.getStatus());
                                if(report.getState().equals("CRITICAL")) scaleUp(resource);
                                if(report.getState().equals("LOW")) scaleDown(resource);
                            }

                            case CONTAINER -> {
                                if(report.getState().equals("CRITICAL")) {
                                    scaleUp(resource);
                                    scaleUp(resource);
                                }
                                if(report.getState().equals("LOW")) scaleDown(resource);
                            }

                            case DATABASE -> {
                                if(report.getState().equals("CRITICAL")) scaleUp(resource);
                            }

                            case API_GATEWAY -> {
                                if(report.getState().equals("CRITICAL")) scaleUp(resource);
                                if(report.getState().equals("LOW")) scaleDown(resource);
                            }

                            case STORAGE_NODE -> {
                                if(report.getState().equals("CRITICAL")) scaleUp(resource);
                            }
                        }
                    return;
                }
            }
        }
    }

    private static void scaleUp(CloudResource resource) {

        List<CloudResource> resources = cluster.get(resource.getType());

        String newId = resource.getType() + "-AUTO-" + UUID.randomUUID().toString().substring(0,4);

        CloudResource newReplica = new CloudResource(newId, resource.getType());

        resources.add(newReplica);

        System.out.println(" NUEVA INSTANCIA CREADA: " + newId);
        System.out.println("Total instancias " + resource.getType() + ": " + resources.size());
    }
    
    private static void scaleDown(CloudResource resource) {

        List<CloudResource> resources = cluster.get(resource.getType());

        if (resources.size() > 1) {

            CloudResource removed = resources.remove(resources.size() - 1);

            System.out.println("Instancia eliminada: " + removed.getId());
            System.out.println("Total instancias " + resource.getType() + ": " + resources.size());
        }
    }




    private static void monitorNodes() {

        while (true) {

            long now = System.currentTimeMillis();

            nodes.values().removeIf(node -> {
                if (now - node.getLastHeartbeat() > TIMEOUT) {

                    System.out.println(" Nodo caído: " + node.getNodeId());

                    nodes.remove(node.getNodeId());
                    return true;
                }
                return false;
            });

            try { Thread.sleep(3000); } catch (Exception ignored) {}
        }
    }
}
