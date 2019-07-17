package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Filter {
    private Integer minAge;
    private Integer maxAge;
    private Integer minDistance;
    private Integer maxDistance;

    public Integer getMinAge() {
        return minAge;
    }

    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    public Integer getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(Integer maxDistance) {
        this.maxDistance = maxDistance;
    }

    public Integer getMinDistance() {
        return minDistance;
    }

    public void setMinDistance(Integer minDistance) {
        this.minDistance = minDistance;
    }

    @Override
    public String toString() {
        return "Filter{" +
                "minAge=" + minAge +
                ", maxAge=" + maxAge +
                ", minDistance=" + minDistance +
                ", maxDistance=" + maxDistance +
                '}';
    }
}
