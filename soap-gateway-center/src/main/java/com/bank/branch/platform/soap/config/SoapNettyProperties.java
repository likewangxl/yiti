package com.bank.branch.platform.soap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "platform.soap.netty")
public class SoapNettyProperties {

    private int port = 30522;
    private int bossThreads = 1;
    private int workerThreads = 4;
    private int maxContentLength = 10 * 1024 * 1024;
    private int readIdleSeconds = 60;
    private boolean logRequest = false;
}
