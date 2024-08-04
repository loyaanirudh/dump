package com.example.test.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class Step {
    private String id;
    private String action;
    private String method;
    private String query;
    private String expectedOutputFile;
    private Map<String, Parameter> parameters;
    private List<String> dependencies;
}
