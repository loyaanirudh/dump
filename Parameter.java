package test;

import lombok.Data;

@Data
public class Parameter {
    private String value;
    private String type;

    // Getters and setters...

    @Override
    public String toString() {
        return "Parameter{" +
                "value='" + value + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
