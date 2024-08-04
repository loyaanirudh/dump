package com.example.test.executor;

import com.example.test.model.TestConfig;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RegressionPackExecutor {

    private final StepExecutor stepExecutor = new StepExecutor();

    public void executeTest(TestConfig testConfig) {
        List<CompletableFuture<Void>> cleanupFutures = stepExecutor.executeStepsInParallel(testConfig.getCleanupSteps());
        CompletableFuture<Void> allCleanups = CompletableFuture.allOf(cleanupFutures.toArray(new CompletableFuture[0]));
        allCleanups.join();

        List<CompletableFuture<Void>> loadingFutures = stepExecutor.executeStepsInParallel(testConfig.getLoadingSteps());
        CompletableFuture<Void> allLoadings = CompletableFuture.allOf(loadingFutures.toArray(new CompletableFuture[0]));
        allLoadings.join();

        List<CompletableFuture<Void>> verificationFutures = stepExecutor.executeStepsInParallel(testConfig.getVerificationSteps());
        CompletableFuture<Void> allVerifications = CompletableFuture.allOf(verificationFutures.toArray(new CompletableFuture[0]));
        allVerifications.join();
    }
}
