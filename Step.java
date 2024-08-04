package test;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Step {
    private String id;
    private String action;
    private String method;
    private String query;
    private String expectedOutputFile;
    private Map<String, Parameter> parameters;
    private List<String> dependencies;

    // Getters and setters...

    @Override
    public String toString() {
        return "Step{" +
                "id='" + id + '\'' +
                ", action='" + action + '\'' +
                ", method='" + method + '\'' +
                ", query='" + query + '\'' +
                ", expectedOutputFile='" + expectedOutputFile + '\'' +
                ", parameters=" + parameters +
                ", dependencies=" + dependencies +
                '}';
    }
}
