package com.clucknet.backend.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDbConfig {

    @Value("${influxdb.url}")
    private String url;

    @Value("${influxdb.token}")
    private String token;

    @Value("${influxdb.org}")
    private String org;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Bean(destroyMethod = "close")
    public InfluxDBClient influxDBClient() {
        // Create standard high performance thread-safe InfluxDB Client
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }

    public String getOrg() {
        return org;
    }

    public String getBucket() {
        return bucket;
    }
}
