package com.example.test.utility;

import com.example.test.model.Parameter;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class MethodInvoker {

    public void invokeMethod(String methodName, Map<String, Parameter> parameters) {
        try {
            String className = methodName.substring(0, methodName.lastIndexOf('.'));
            String method = methodName.substring(methodName.lastIndexOf('.') + 1);
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Class<?>[] paramTypes = new Class<?>[parameters.size()];
            Object[] paramValues = new Object[parameters.size()];
            int i = 0;
            for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
                paramTypes[i] = Class.forName(entry.getValue().getType());
                paramValues[i] = convertParameter(entry.getValue());
                i++;
            }
            Method methodToInvoke = clazz.getMethod(method, paramTypes);
            methodToInvoke.invoke(instance, paramValues);
        } catch (Exception e) {
            throw new RuntimeException("Error invoking method: " + methodName, e);
        }
    }

    private Object convertParameter(Parameter parameter) {
        try {
            Class<?> clazz = Class.forName(parameter.getType());
            if (clazz.isEnum()) {
                return Enum.valueOf((Class<Enum>) clazz, parameter.getValue());
            } else if (clazz.equals(LocalDate.class)) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                return LocalDate.parse(parameter.getValue(), formatter);
            } else if (clazz.equals(String.class)) {
                return parameter.getValue();
            } else if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
                return Boolean.parseBoolean(parameter.getValue());
            } else {
                Method valueOfMethod = clazz.getMethod("valueOf", String.class);
                return valueOfMethod.invoke(null, parameter.getValue());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error converting parameter: " + parameter.getType(), e);
        }
    }
}
