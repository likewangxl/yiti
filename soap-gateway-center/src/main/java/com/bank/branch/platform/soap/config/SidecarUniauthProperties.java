package com.bank.branch.platform.soap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 边车统一认证相关配置(仅取本网关响应所需字段)。
 *
 * <p>绑定 {@code platform.sidecar.uniauth.*}(见 bootstrap/application-dev.yml)。
 * 其中 {@code source-sys-id} 为本系统(宿主应用)系统号,用于 SOAP 响应里的
 * TargetSysId / BackendSysId 以及 BackendSeqNo 前 4 位。</p>
 *
 * <p>该前缀下还有 path/consumer-id 等其它字段,由 auth 模块的 UniAuthProperties 各自绑定;
 * 本类只声明 {@code sourceSysId},未声明字段默认忽略,互不影响。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.sidecar.uniauth")
public class SidecarUniauthProperties {

    /** 本系统(宿主应用)系统号,如 37150001。 */
    private String sourceSysId;
}
