package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AmasPriceApprovalQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AmasPriceApprovalVO;
import com.bank.branch.platform.report.entity.AmasPriceApproval;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.AmasPriceApprovalMapper;
import com.bank.branch.platform.report.service.AmasPriceApprovalQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link AmasPriceApprovalQueryService} 实现：只读，MyBatis-Plus 条件查询，对外只返回 VO.
 *
 * <p>查询：客户名称 / 申请人姓名 模糊。分页依赖 common-db 全局注册的
 * {@code PaginationInnerInterceptor}（MySQL 方言）。时间倒序对 varchar 的 {@code APPLY_TIME}
 * 做字符串降序。entity → VO 用 {@link BeanUtils#copyProperties}（同名字段）。</p>
 *
 * <p><strong>数据范围</strong>：按 {@code BizType.REPORT} 的 DATA_SCOPE 标签控制——
 * {@code ALL} 不过滤；{@code ORG_SUBTREE}「本级及下级机构」→ {@code APPLY_DEPTNO IN 机构子树编码集合}；
 * {@code ORG}「本机构」→ {@code APPLY_DEPTNO = 当前用户主机构编码}；其余（SELF 等）收敛到「本人申请」。
 * {@code APPLY_DEPTNO}（申请人部门编号）与机构表（{@code EXT_ORG_INFO}）部门编号关联，机构上下级关系
 * 由 {@code BizScopeApi.buildScopeContext} 经 OrgApi 递归机构子树后填充到 {@code orgSubtreeCodes}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmasPriceApprovalQueryServiceImpl implements AmasPriceApprovalQueryService {

    private final AmasPriceApprovalMapper priceApprovalMapper;
    /** 当前用户（数据范围主体）. */
    private final CurrentUserApi currentUserApi;
    /** BizType.REPORT 数据范围标签 + 机构子树. */
    private final BizScopeApi bizScopeApi;

    @Override
    public PageResult<AmasPriceApprovalVO> pageList(AmasPriceApprovalQueryReqDTO req, PageRequest page) {
        AmasPriceApprovalQueryReqDTO q = req == null ? new AmasPriceApprovalQueryReqDTO() : req;
        LambdaQueryWrapper<AmasPriceApproval> w = new LambdaQueryWrapper<>();
        // 客户名称：模糊
        if (StringUtils.hasText(q.getCustName())) {
            w.like(AmasPriceApproval::getCustName, q.getCustName().trim());
        }
        // 申请人姓名：模糊
        if (StringUtils.hasText(q.getApplyFullname())) {
            w.like(AmasPriceApproval::getApplyFullname, q.getApplyFullname().trim());
        }
        // 审批状态：精确
        if (StringUtils.hasText(q.getApprStatus())) {
            w.eq(AmasPriceApproval::getApprStatus, q.getApprStatus().trim());
        }
        // 申请时间区间（字符串比较）
        if (StringUtils.hasText(q.getApplyTimeStart())) {
            w.ge(AmasPriceApproval::getApplyTime, q.getApplyTimeStart().trim());
        }
        if (StringUtils.hasText(q.getApplyTimeEnd())) {
            w.le(AmasPriceApproval::getApplyTime, q.getApplyTimeEnd().trim());
        }
        // BizType.REPORT 数据范围控制（本机构 / 本级及下级，基于 APPLY_DEPTNO）
        applyReportScope(w);
        // 申请时间倒序
        w.orderByDesc(AmasPriceApproval::getApplyTime);

        Page<AmasPriceApproval> mpPage = new Page<>(page.getPageNo(), page.getPageSize());
        Page<AmasPriceApproval> result = priceApprovalMapper.selectPage(mpPage, w);
        List<AmasPriceApprovalVO> rows = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return PageResult.of(page.getPageNo(), page.getPageSize(), result.getTotal(), rows);
    }

    /**
     * 应用 BizType.REPORT 数据范围（DATA_SCOPE）到定价审批列表查询，按机构维度（APPLY_DEPTNO）过滤.
     *
     * <ul>
     *   <li>{@code ALL} → 不过滤（全行）；</li>
     *   <li>{@code ORG_SUBTREE}（本级及下级机构）→ {@code APPLY_DEPTNO IN orgSubtreeCodes}，子树为空 fail-close；</li>
     *   <li>{@code ORG}（本机构）→ {@code APPLY_DEPTNO = orgCode}，无机构 fail-close；</li>
     *   <li>其余（SELF 等）→ 收敛到「本人申请」{@code APPLY_USERNAME = 当前工号}。</li>
     * </ul>
     * 无 currentUser/bizScope（测试上下文）时不过滤。
     */
    private void applyReportScope(LambdaQueryWrapper<AmasPriceApproval> w) {
        if (currentUserApi == null || bizScopeApi == null) {
            return;
        }
        String empId = currentUserApi.getCurrentEmpId();
        DataScopeContext scope = bizScopeApi.buildScopeContext(empId, BizType.REPORT, BizAction.LIST);
        DataScopeType type = scope != null ? scope.scopeType() : null;
        if (type == null || type == DataScopeType.ALL) {
            return;
        }
        switch (type) {
            case ORG_SUBTREE -> {
                // 本级及下级机构：APPLY_DEPTNO 关联机构表部门编号，取机构子树编码集合
                Set<String> codes = scope.orgSubtreeCodes();
                if (CollectionUtils.isEmpty(codes)) {
                    w.apply("1 = 0");
                } else {
                    w.in(AmasPriceApproval::getApplyDeptno, codes);
                }
            }
            case ORG -> {
                // 本机构：APPLY_DEPTNO = 当前用户主机构编码
                String org = scope.orgCode();
                if (StringUtils.hasText(org)) {
                    w.eq(AmasPriceApproval::getApplyDeptno, org);
                } else {
                    w.apply("1 = 0");
                }
            }
            default -> {
                CurrentUserContext uc = currentUserApi.getCurrentUserContext();
                String username = uc != null ? uc.username() : null;
                w.eq(AmasPriceApproval::getApplyUsername, StringUtils.hasText(username) ? username : "__none__");
            }
        }
    }

    @Override
    public AmasPriceApprovalVO detail(String priceApprId) {
        AmasPriceApproval e = priceApprovalMapper.selectById(priceApprId);
        if (e == null) {
            throw new RptException(RptErrorCode.AMAS_APPROVAL_NOT_FOUND);
        }
        return toVO(e);
    }

    private AmasPriceApprovalVO toVO(AmasPriceApproval e) {
        AmasPriceApprovalVO vo = new AmasPriceApprovalVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
