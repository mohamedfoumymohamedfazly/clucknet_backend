package com.clucknet.backend.service;

public interface ThresholdEvaluationService {

    // Triggers comparison of temperature, humidity, NH3, and LPG levels against MySQL rules
    void evaluateTelemetry(Long zoneId, String deviceId, Double temperature, Double humidity, Double nh3, Double lpg);
}
