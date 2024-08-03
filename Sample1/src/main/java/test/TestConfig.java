package test;

import lombok.Data;

import java.util.List;

@Data
public class TestConfig {
    private List<Step> cleanupSteps;
    private List<Step> loadingSteps;
    private List<Step> verificationSteps;

    // Getters and setters...

    @Override
    public String toString() {
        return "TestConfig{" +
                "cleanupSteps=" + cleanupSteps +
                ", loadingSteps=" + loadingSteps +
                ", verificationSteps=" + verificationSteps +
                '}';
    }
}
