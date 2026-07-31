package com.yqz.openblog.audit.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AuditJson {
    private final ObjectMapper objectMapper;

    public AuditJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"_json_error\":\"" + e.getMessage() + "\"}";
        }
    }
}
