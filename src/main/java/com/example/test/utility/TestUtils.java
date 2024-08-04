package com.example.test.utility;

import com.example.test.executor.RegressionPackExecutor;
import com.example.test.model.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class TestUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final RegressionPackExecutor executor = new RegressionPackExecutor();

    public static void runTest(String configFilePath) {
        try {
            TestConfig testConfig = objectMapper.readValue(new File(configFilePath), TestConfig.class);
            executor.executeTest(testConfig);
        } catch (Exception e) {
            throw new RuntimeException("Error running test with config: " + configFilePath, e);
        }
    }
}
