package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrepareNewFacesResponse {
    private Map<String, Integer> targetUserIndexMap;

    public PrepareNewFacesResponse() {
        this.targetUserIndexMap = new HashMap<>();
    }

    public Map<String, Integer> getTargetUserIndexMap() {
        return targetUserIndexMap;
    }

    public void setTargetUserIndexMap(Map<String, Integer> targetUserIndexMap) {
        this.targetUserIndexMap = targetUserIndexMap;
    }
}
