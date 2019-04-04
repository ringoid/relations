package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PushRequest {
    private int skip;
    private int limit;
    private int maxPeriod;
    private int offlinePeriod;
    private int minForMen;
    private int minForWomen;
    private int minH;
    private int maxH;

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getMaxPeriod() {
        return maxPeriod;
    }

    public void setMaxPeriod(int maxPeriod) {
        this.maxPeriod = maxPeriod;
    }

    public int getOfflinePeriod() {
        return offlinePeriod;
    }

    public void setOfflinePeriod(int offlinePeriod) {
        this.offlinePeriod = offlinePeriod;
    }

    public int getMinForMen() {
        return minForMen;
    }

    public void setMinForMen(int minForMen) {
        this.minForMen = minForMen;
    }

    public int getMinForWomen() {
        return minForWomen;
    }

    public void setMinForWomen(int minForWomen) {
        this.minForWomen = minForWomen;
    }

    public int getMinH() {
        return minH;
    }

    public void setMinH(int minH) {
        this.minH = minH;
    }

    public int getMaxH() {
        return maxH;
    }

    public void setMaxH(int maxH) {
        this.maxH = maxH;
    }
}
