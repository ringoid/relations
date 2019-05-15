package com.ringoid.api.internaltmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConvertResponse {
    private List<ConverObject> objects;

    public ConvertResponse() {
        this.objects = new ArrayList<>();
    }

    public List<ConverObject> getObjects() {
        return objects;
    }

    public void setObjects(List<ConverObject> objects) {
        this.objects = objects;
    }
}

