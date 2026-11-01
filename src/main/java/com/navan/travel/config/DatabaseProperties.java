package com.navan.travel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.datasource")
public class DatabaseProperties {

    private String url = "jdbc:sqlite:data/travel_invoice.db";

    public DatabaseProperties() {
    }

    public DatabaseProperties(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
