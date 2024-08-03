package test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RegressionPackExecutor {

    private final StepExecutor stepExecutor = new StepExecutor();

    public void executeTest(TestConfig testConfig) {
        List<CompletableFuture<Void>> cleanupFutures = executeStepsInParallel(testConfig.getCleanupSteps());
        List<CompletableFuture<Void>> loadingFutures = executeStepsInParallel(testConfig.getLoadingSteps());
        List<CompletableFuture<Void>> verificationFutures = executeStepsInParallel(testConfig.getVerificationSteps());

        CompletableFuture<Void> allCleanups = CompletableFuture.allOf(cleanupFutures.toArray(new CompletableFuture[0]));
        allCleanups.join();

        CompletableFuture<Void> allLoadings = CompletableFuture.allOf(loadingFutures.toArray(new CompletableFuture[0]));
        allLoadings.join();

        CompletableFuture<Void> allVerifications = CompletableFuture.allOf(verificationFutures.toArray(new CompletableFuture[0]));
        allVerifications.join();
    }

    private List<CompletableFuture<Void>> executeStepsInParallel(List<Step> steps) {
        Map<String, CompletableFuture<Void>> futuresMap = new HashMap<>();

        for (Step step : steps) {
            List<CompletableFuture<Void>> dependencies = new ArrayList<>();
            for (String dependencyId : step.getDependencies()) {
                dependencies.add(futuresMap.get(dependencyId));
            }

            CompletableFuture<Void> stepFuture = CompletableFuture.allOf(dependencies.toArray(new CompletableFuture[0]))
                    .thenRunAsync(() -> stepExecutor.executeStep(step));
            futuresMap.put(step.getId(), stepFuture);
        }

        return new ArrayList<>(futuresMap.values());
    }
}
