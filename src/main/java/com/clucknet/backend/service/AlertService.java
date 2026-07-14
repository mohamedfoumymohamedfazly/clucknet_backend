package com.clucknet.backend.service;

import com.clucknet.backend.entity.Alert;
import com.clucknet.backend.entity.AlertType;

import java.util.List;

public interface AlertService {

    // Instantiates, logs, and persists a fresh climate safety breach alert in MySQL
    Alert createAlert(Long zoneId, String deviceId, AlertType type, Double triggeredValue, Double thresholdValue);

    List<Alert> getActiveAlerts();

    Alert resolveAlert(Long alertId);
}
