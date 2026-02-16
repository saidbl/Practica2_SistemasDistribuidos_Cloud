package common;

import java.io.Serializable;

public class MetricReport implements Serializable {

    private String nodeId;
    private int value;
    private String state;

    public MetricReport(String nodeId, int value, String state) {
        this.nodeId = nodeId;
        this.value = value;
        this.state = state;
    }

    public String getNodeId() { return nodeId; }
    public int getValue() { return value; }
    public String getState() { return state; }
}
