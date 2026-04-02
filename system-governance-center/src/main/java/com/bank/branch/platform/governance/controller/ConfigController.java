package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.ConfigDTO;
import com.bank.branch.platform.governance.api.dto.ConfigUpdateReqDTO;
import com.bank.branch.platform.governance.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置控制器
 * 提供系统配置的查询和更新接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sys/configs")
@Tag(name = "系统配置", description = "系统配置参数的查询与更新")
public class ConfigController {

    private final ConfigService configService;

    /**
     * 分页查询配置列表
     *
     * @param status   状态过滤，可为 null
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询配置列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<ConfigDTO> listConfigs(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[ConfigController.listConfigs] status={}, pageNo={}, pageSize={}", status, pageNo, pageSize);
        PageResult<ConfigDTO> result = configService.listConfigs(status, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 更新配置值
     *
     * @param configKey 配置键（路径参数）
     * @param req       配置更新请求DTO
     * @return 成功响应
     */
    @PutMapping("/{configKey}")
    @Operation(summary = "更新配置值")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> updateConfig(
            @PathVariable(value = "configKey") String configKey,
            @Valid @RequestBody ConfigUpdateReqDTO req) {
        log.info("[ConfigController.updateConfig] configKey={}", configKey);
        configService.updateConfig(configKey, req.getConfigValue());
        return ResponseWrapper.success();
    }
}
