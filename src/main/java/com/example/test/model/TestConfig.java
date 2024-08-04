package com.example.test.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TestConfig {
    private List<Step> cleanupSteps;
    private List<Step> loadingSteps;
    private List<Step> verificationSteps;
}
