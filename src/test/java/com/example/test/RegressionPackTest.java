package com.example.test;

import com.example.test.utility.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegressionPackTest {

    @ParameterizedTest
    @ValueSource(strings = {"src/test/resources/regression_test1.json", "src/test/resources/regression_test2.json"})
    void executeRegressionPack(String configFilePath) {
        TestUtils.runTest(configFilePath);
    }
}
