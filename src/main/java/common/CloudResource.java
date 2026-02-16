package common;

import java.io.Serializable;

public class CloudResource implements Serializable {

    private String id;
    private ResourceType type;
    private int load = 0;
    private int replicas = 1;
    private String status = "NORMAL";

    public CloudResource(String id, ResourceType type) {
        this.id = id;
        this.type = type;
    }

    public String getId() { return id; }
    public ResourceType getType() { return type; }
    public int getLoad() { return load; }
    public int getReplicas() { return replicas; }
    public String getStatus() { return status; }

    public void setLoad(int load) { this.load = load; }
    public void setReplicas(int replicas) { this.replicas = replicas; }
    public void setStatus(String status) { this.status = status; }
}
