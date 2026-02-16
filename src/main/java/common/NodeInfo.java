package common;

import java.io.ObjectOutputStream;
import java.net.Socket;

public class NodeInfo {

    private String nodeId;
    private Socket socket;
    private ObjectOutputStream out;
    private long lastHeartbeat;

    public NodeInfo(String nodeId, Socket socket, ObjectOutputStream out) {
        this.nodeId = nodeId;
        this.socket = socket;
        this.out = out;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getNodeId() { return nodeId; }
    public Socket getSocket() { return socket; }
    public ObjectOutputStream getOut() { return out; }
    public long getLastHeartbeat() { return lastHeartbeat; }

    public void updateHeartbeat() {
        lastHeartbeat = System.currentTimeMillis();
    }
}
