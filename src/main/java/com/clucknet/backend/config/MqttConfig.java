package com.clucknet.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

@Configuration
@Slf4j
public class MqttConfig {

    @Value("${aws.iot.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${aws.iot.mqtt.client-id}")
    private String clientId;

    @Value("${aws.iot.mqtt.connection-timeout-seconds}")
    private int connectionTimeout;

    @Value("${aws.iot.mqtt.keep-alive-interval-seconds}")
    private int keepAliveInterval;

    @Value("${aws.iot.mqtt.clean-session}")
    private boolean cleanSession;

    @Value("${aws.iot.mqtt.ssl.enabled}")
    private boolean sslEnabled;

    @Value("${aws.iot.mqtt.ssl.key-store-path}")
    private String keyStorePath;

    @Value("${aws.iot.mqtt.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${aws.iot.mqtt.ssl.trust-store-path}")
    private String trustStorePath;

    @Value("${aws.iot.mqtt.ssl.trust-store-password}")
    private String trustStorePassword;

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setCleanSession(cleanSession);
        options.setAutomaticReconnect(true); // Robust auto-reconnect flag

        if (sslEnabled || brokerUrl.startsWith("ssl://")) {
            try {
                options.setSocketFactory(getSslSocketFactory());
                log.info("Secure TLS Socket Factory loaded successfully for AWS IoT Core.");
            } catch (Exception ex) {
                log.error("Failed to load secure TLS cert keystores. Falling back: {}", ex.getMessage());
            }
        } else {
            log.warn("MQTT initialized in standard non-SSL TCP mode. Suitable ONLY for local development simulation.");
        }

        return options;
    }

    @Bean
    public MqttClient mqttClient(MqttConnectOptions options) {
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            MqttClient client = new MqttClient(brokerUrl, clientId, persistence);
            log.info("MQTT Client instantiated successfully for broker: {}", brokerUrl);
            return client;
        } catch (MqttException ex) {
            log.error("Failed to instantiate MQTT Client: {}", ex.getMessage());
            throw new RuntimeException("MQTT Client creation failure.", ex);
        }
    }

    // Build standard SSLSocketFactory using mutual X.509 certificate keystores
    private SSLSocketFactory getSslSocketFactory() throws Exception {
        // Load Client Private Key Certificate Keystore (PKCS12 format)
        KeyStore clientKeyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            clientKeyStore.load(fis, keyStorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientKeyStore, keyStorePassword.toCharArray());

        // Load AWS CA Root Certificate Truststore (JKS format)
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(trustStorePath)) {
            trustStore.load(fis, trustStorePassword.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Build TLS 1.2/1.3 mutual auth Context
        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext.getSocketFactory();
    }
}

