package server;

import common.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    private static final int MAX_VM = 5;
    private static final int MAX_CONTAINER = 8;
    private static final int MAX_DATABASE = 3;
    private static final int MAX_API_GATEWAY = 4;
    private static final int MAX_STORAGE_NODE = 4;

    private static final int PUBLIC_PORT = 5000;   
    private static final int BUS_PORT = 6000;      
    private static final long HEARTBEAT_TIMEOUT_MS = 8000;

    private static final Map<String, ResourceType> nodeType = new ConcurrentHashMap<>();
    private static final Map<String, Integer> lastValue = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private static final Map<ResourceType, Long> lastScaleTime = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 5000;


    private static final Map<ResourceType, CopyOnWriteArrayList<String>> cluster = new ConcurrentHashMap<>();

    private static final AtomicInteger handlerPortSeq = new AtomicInteger(7000);

    public static void main(String[] args) throws Exception {
        System.out.println("CLOUD SERVER MAIN PID: " + ProcessHandle.current().pid());
        System.out.println("Public port: " + PUBLIC_PORT + " | Internal bus port: " + BUS_PORT);

        for (ResourceType t : ResourceType.values()) cluster.put(t, new CopyOnWriteArrayList<>());

        new Thread(Server::runInternalBus, "InternalBus").start();
        new Thread(Server::monitorHeartbeats, "HeartbeatMonitor").start();

        try (ServerSocket publicSocket = new ServerSocket(PUBLIC_PORT)) {
            while (true) {
                Socket s = publicSocket.accept();

                int handlerPort = handlerPortSeq.getAndIncrement();

                spawnHandler(handlerPort);

                try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                    out.writeObject(new Message("REDIRECT", handlerPort));
                    out.flush();
                } catch (Exception ignored) {
                } finally {
                    try { s.close(); } catch (Exception ignored) {}
                }
            }
        }
    }

    private static void spawnHandler(int handlerPort) throws IOException {
        String cp = "target/classes";

        new ProcessBuilder(
                "java", "-cp", cp,
                "server.ClientHandler",
                String.valueOf(handlerPort),
                String.valueOf(BUS_PORT)
        ).inheritIO().start();

        try {
            Thread.sleep(300); 
        } catch (InterruptedException ignored) {}
    }


    private static void runInternalBus() {
        try (ServerSocket bus = new ServerSocket(BUS_PORT)) {
            System.out.println("[BUS] Listening on " + BUS_PORT);

            while (true) {
                Socket s = bus.accept();
                new Thread(() -> handleBusConnection(s), "BusConn-" + s.getPort()).start();
            }
        } catch (Exception e) {
            System.out.println("[BUS] ERROR: " + e.getMessage());
        }
    }

    private static void handleBusConnection(Socket s) {
        try (ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
            while (true) {
                Message msg = (Message) in.readObject();
                switch (msg.getType()) {
                    case "REGISTER" -> onRegister((Registration) msg.getPayload());
                    case "HEARTBEAT" -> onHeartbeat((String) msg.getPayload());
                    case "METRIC" -> onMetric((MetricReport) msg.getPayload());
                    default -> { }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private static void onRegister(Registration reg) {
        nodeType.put(reg.getNodeId(), reg.getType());

        cluster.get(reg.getType()).addIfAbsent(reg.getNodeId());

        lastHeartbeat.put(reg.getNodeId(), System.currentTimeMillis());

        System.out.println("[REGISTER] node=" + reg.getNodeId()
                + " type=" + reg.getType()
                + " role=" + reg.getRole()
                + " | total(" + reg.getType() + ")=" + cluster.get(reg.getType()).size());
    }

    private static void onHeartbeat(String nodeId) {
        lastHeartbeat.put(nodeId, System.currentTimeMillis());
    }

    private static void onMetric(MetricReport r) {
        lastValue.put(r.getNodeId(), r.getValue());

        String state = calculateState(r.getType(), r.getValue());

        System.out.println("[METRIC] " + r.getType() + " " + r.getNodeId()
                + " value=" + r.getValue()
                + " state=" + state);

        applyScalingPolicy(r.getType(), state);
    }
    private static String calculateState(ResourceType type, int v) {
        return switch (type) {
            case VM -> (v > 80) ? "CRITICAL" : (v < 30) ? "LOW" : "NORMAL";
            case CONTAINER -> (v > 70) ? "CRITICAL" : (v < 20) ? "LOW" : "NORMAL";
            case DATABASE -> (v >= 100) ? "CRITICAL" : "NORMAL";
            case API_GATEWAY -> (v > 200) ? "CRITICAL" : (v < 80) ? "LOW" : "NORMAL";
            case STORAGE_NODE -> (v >= 85) ? "CRITICAL" : "NORMAL";
        };
    }

    private static void applyScalingPolicy(ResourceType type, String state) {

        long now = System.currentTimeMillis();
        long last = lastScaleTime.getOrDefault(type, 0L);

        if (now - last < COOLDOWN_MS) return;

        if ("CRITICAL".equals(state)) {

            lastScaleTime.put(type, now);

            switch (type) {
                case VM -> scaleUp(type, 1);
                case CONTAINER -> scaleUp(type, 2);
                case DATABASE -> scaleUp(type, 1);
                case API_GATEWAY -> scaleUp(type, 1);
                case STORAGE_NODE -> scaleUp(type, 1);
            }
        }

        if ("LOW".equals(state)) {

            lastScaleTime.put(type, now);

            switch (type) {
                case VM, CONTAINER, API_GATEWAY -> scaleDown(type, 1);
                default -> {}
            }
        }
    }

    private static void scaleUp(ResourceType type, int count) {

        int maxLimit = switch(type) {
            case VM -> MAX_VM;
            case CONTAINER -> MAX_CONTAINER;
            case DATABASE -> MAX_DATABASE;
            case API_GATEWAY -> MAX_API_GATEWAY;
            case STORAGE_NODE -> MAX_STORAGE_NODE;
        };

        CopyOnWriteArrayList<String> list = cluster.get(type);

        if (list.size() >= maxLimit) {
            System.out.println("[SCALE-UP BLOCKED] Max limit reached for " + type);
            return;
        }

        for (int i = 0; i < count; i++) {

            if (list.size() >= maxLimit) break;

            String replicaId = type + "-AUTO-" +
                    UUID.randomUUID().toString().substring(0, 4);

            list.add(replicaId);

            System.out.println("[SCALE-UP] Created " + replicaId +
                    " | total(" + type + ")=" + list.size());
        }
    }


    private static void scaleDown(ResourceType type, int count) {
        CopyOnWriteArrayList<String> list = cluster.get(type);

        for (int i = 0; i < count; i++) {
            if (list.size() <= 1) return;

            int idx = findAutoReplicaIndex(list);
            String removed = (idx >= 0) ? list.remove(idx) : list.remove(list.size() - 1);

            System.out.println("[SCALE-DOWN] Removed " + removed
                    + " | total(" + type + ")=" + list.size());
        }
    }

    private static int findAutoReplicaIndex(List<String> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).contains("-AUTO-")) return i;
        }
        return -1;
    }

    private static void monitorHeartbeats() {
        while (true) {
            long now = System.currentTimeMillis();

            for (String nodeId : new ArrayList<>(lastHeartbeat.keySet())) {
                long last = lastHeartbeat.getOrDefault(nodeId, 0L);
                if (now - last > HEARTBEAT_TIMEOUT_MS) {
                    ResourceType t = nodeType.get(nodeId);
                    System.out.println("[FAILURE] Node down: " + nodeId + " type=" + t);

                    lastHeartbeat.remove(nodeId);
                    lastValue.remove(nodeId);
                    nodeType.remove(nodeId);

                    if (t != null) cluster.get(t).remove(nodeId);
                }
            }

            try { Thread.sleep(2000); } catch (Exception ignored) {}
        }
    }
}
