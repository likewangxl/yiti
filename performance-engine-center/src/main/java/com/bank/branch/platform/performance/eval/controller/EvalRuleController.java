package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.CreateRuleReq;
import com.bank.branch.platform.performance.eval.dto.GroupReq;
import com.bank.branch.platform.performance.eval.dto.UpdateRuleReq;
import com.bank.branch.platform.performance.eval.entity.EvalRule;
import com.bank.branch.platform.performance.eval.entity.EvalRuleGroup;
import com.bank.branch.platform.performance.eval.service.EvalRuleService;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评价关系规则管理控制器.
 * <p>提供评价规则的增删改查接口，每条规则绑定一个被评价人标签，并关联若干评价人组。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/rules")
@Tag(name = "Eval Rule", description = "评价关系规则管理")
@Validated
@RequiredArgsConstructor
public class EvalRuleController {

    private final EvalRuleService evalRuleService;

    /**
     * 分页查询规则列表.
     *
     * @param keyword  规则名称关键词（可选）
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 20，最大 100
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询规则列表")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalRule>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        log.debug("[EvalRuleController.list] keyword={}, page={}, pageSize={}", keyword, page, pageSize);
        return ResponseWrapper.success(evalRuleService.list(keyword, page, pageSize));
    }

    /**
     * 查询规则详情（含评价人组）.
     *
     * @param ruleId 规则ID（路径参数）
     * @return Map 含 "rule"（主记录）和 "groups"（评价人组列表）
     * @throws PerfException 规则不存在时抛 EVAL_RULE_NOT_FOUND（PERF-40058）
     */
    @GetMapping("/{ruleId}")
    @Operation(summary = "查询规则详情（含评价人组）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<Map<String, Object>> getById(@PathVariable("ruleId") Long ruleId) {
        log.debug("[EvalRuleController.getById] ruleId={}", ruleId);
        EvalRule rule = evalRuleService.getById(ruleId);
        if (rule == null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, ruleId);
        }
        List<EvalRuleGroup> groups = evalRuleService.getGroupsByRuleId(ruleId);
        Map<String, Object> result = Map.of("rule", rule, "groups", groups);
        return ResponseWrapper.success(result);
    }

    /**
     * 新建评价规则.
     *
     * @param req 新建请求体（ruleName、beEvalTagId、groups）
     * @return 创建后的规则实体
     */
    @PostMapping
    @Operation(summary = "新建评价规则")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<EvalRule> create(@RequestBody @Valid CreateRuleReq req) {
        log.info("[EvalRuleController.create] ruleName={} beEvalTagId={}", req.getRuleName(), req.getBeEvalTagId());
        List<EvalRuleService.GroupParam> groupParams = toGroupParams(req.getGroups());
        EvalRule rule = evalRuleService.create(req.getRuleName(), req.getBeEvalTagId(), groupParams);
        return ResponseWrapper.success(rule);
    }

    /**
     * 编辑评价规则.
     *
     * @param ruleId 规则ID（路径参数）
     * @param req    更新请求体（ruleName、groups）
     * @return 空成功响应
     */
    @PutMapping("/{ruleId}")
    @Operation(summary = "编辑评价规则")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> update(
            @PathVariable("ruleId") Long ruleId,
            @RequestBody @Valid UpdateRuleReq req) {
        log.info("[EvalRuleController.update] ruleId={} ruleName={}", ruleId, req.getRuleName());
        List<EvalRuleService.GroupParam> groupParams = toGroupParams(req.getGroups());
        evalRuleService.update(ruleId, req.getRuleName(), groupParams);
        return ResponseWrapper.success();
    }

    /**
     * 删除评价规则.
     *
     * @param ruleId 规则ID（路径参数）
     * @return 空成功响应
     */
    @DeleteMapping("/{ruleId}")
    @Operation(summary = "删除评价规则")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.DELETE)
    public ResponseWrapper<Void> delete(@PathVariable("ruleId") Long ruleId) {
        log.info("[EvalRuleController.delete] ruleId={}", ruleId);
        evalRuleService.delete(ruleId);
        return ResponseWrapper.success();
    }

    // =============================================
    // 私有工具方法
    // =============================================

    /**
     * 将 GroupReq 列表转换为 Service 层 GroupParam 列表.
     *
     * @param reqs 请求体中的评价人组列表（null 时返回空列表）
     * @return GroupParam 列表
     */
    private List<EvalRuleService.GroupParam> toGroupParams(List<GroupReq> reqs) {
        if (reqs == null) {
            return List.of();
        }
        return reqs.stream()
                .map(r -> new EvalRuleService.GroupParam(
                        r.getGroupType(),
                        r.getEvalTagId(),
                        r.getWeight(),
                        r.getSortOrder(),
                        r.getScoreMode()))
                .collect(Collectors.toList());
    }
}
