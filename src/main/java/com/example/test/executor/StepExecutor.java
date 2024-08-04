package com.example.test.executor;

import com.example.test.model.Step;
import com.example.test.utility.MethodInvoker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class StepExecutor {

    private final MethodInvoker methodInvoker = new MethodInvoker();

    public List<CompletableFuture<Void>> executeStepsInParallel(List<Step> steps) {
        Map<String, CompletableFuture<Void>> futuresMap = steps.stream()
                .collect(Collectors.toMap(Step::getId, step -> {
                    List<CompletableFuture<Void>> dependencies = step.getDependencies() != null
                            ? step.getDependencies().stream().map(futuresMap::get).collect(Collectors.toList())
                            : List.of();

                    return CompletableFuture.allOf(dependencies.toArray(new CompletableFuture[0]))
                            .thenRunAsync(() -> methodInvoker.invokeMethod(step.getMethod(), step.getParameters()));
                }));

        return futuresMap.values().stream().collect(Collectors.toList());
    }
}
