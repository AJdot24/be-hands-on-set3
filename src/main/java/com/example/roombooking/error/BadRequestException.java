package com.example.roombooking.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BadRequestException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public BadRequestException(Map<String, String> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
