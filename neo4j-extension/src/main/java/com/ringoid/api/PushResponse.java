package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PushResponse {
    private List<PushObject> users;
    private long resultCount;

    public PushResponse() {
        this.users = new ArrayList<>();
    }

    public long getResultCount() {
        return resultCount;
    }

    public void setResultCount(long resultCount) {
        this.resultCount = resultCount;
    }

    public List<PushObject> getUsers() {
        return users;
    }

    public void setUsers(List<PushObject> users) {
        this.users = users;
    }

    @Override
    public String toString() {
        return "PushResponse{" +
                "users=" + users +
                ", resultCount=" + resultCount +
                '}';
    }
}
