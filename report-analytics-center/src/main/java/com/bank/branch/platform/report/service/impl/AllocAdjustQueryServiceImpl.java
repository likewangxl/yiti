package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AllocAdjustApplyQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustApplyDetailVO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustApplyRowVO;
import com.bank.branch.platform.report.dto.resp.AllocAdjustItemVO;
import com.bank.branch.platform.report.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.report.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptAllocAdjustApplyMapper;
import com.bank.branch.platform.report.mapper.RptAllocAdjustItemMapper;
import com.bank.branch.platform.report.service.AllocAdjustQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link AllocAdjustQueryService} 实现：只读，全部走 MyBatis-Plus 条件查询，对外只返回 VO.
 *
 * <p>分页依赖 common-db 全局注册的 {@code PaginationInnerInterceptor}（MySQL 方言）。
 * 申请时间为 datetime，按 created_time 降序；entity → VO 用 {@link BeanUtils#copyProperties}
 * （createdTime 因 LocalDateTime→String 类型不同由 BeanUtils 跳过，单独格式化补回）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustQueryServiceImpl implements AllocAdjustQueryService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RptAllocAdjustApplyMapper applyMapper;
    private final RptAllocAdjustItemMapper itemMapper;
    /** 申请人工号 → 姓名解析（PT_USER.username → displayName）. */
    private final UserApi userApi;
    /** 当前用户（数据范围主体）. */
    private final CurrentUserApi currentUserApi;
    /** BizType.REPORT 数据范围标签. */
    private final BizScopeApi bizScopeApi;

    @Override
    public PageResult<AllocAdjustApplyRowVO> pageList(AllocAdjustApplyQueryReqDTO req, PageRequest page) {
        AllocAdjustApplyQueryReqDTO q = req == null ? new AllocAdjustApplyQueryReqDTO() : req;
        LambdaQueryWrapper<PerfAllocAdjustApply> w = new LambdaQueryWrapper<>();
        // 申请人工号：精确
        if (StringUtils.hasText(q.getApplicant())) {
            w.eq(PerfAllocAdjustApply::getCreatedBy, q.getApplicant().trim());
        }
        // 客户关键词：客户号 或 客户名称 模糊
        if (StringUtils.hasText(q.getCustKeyword())) {
            String kw = q.getCustKeyword().trim();
            w.and(c -> c.like(PerfAllocAdjustApply::getCustId, kw)
                    .or().like(PerfAllocAdjustApply::getCustName, kw));
        }
        // 状态：精确
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(PerfAllocAdjustApply::getStatus, q.getStatus().trim());
        }
        // 申请时间区间
        if (StringUtils.hasText(q.getCreatedStart())) {
            w.ge(PerfAllocAdjustApply::getCreatedTime, q.getCreatedStart().trim());
        }
        if (StringUtils.hasText(q.getCreatedEnd())) {
            w.le(PerfAllocAdjustApply::getCreatedTime, q.getCreatedEnd().trim());
        }
        // 列表不展示草稿
        w.ne(PerfAllocAdjustApply::getStatus, "DRAFT");
        // BizType.REPORT 数据范围控制
        applyReportScope(w);
        w.orderByDesc(PerfAllocAdjustApply::getCreatedTime);

        Page<PerfAllocAdjustApply> mpPage = new Page<>(page.getPageNo(), page.getPageSize());
        Page<PerfAllocAdjustApply> result = applyMapper.selectPage(mpPage, w);
        List<AllocAdjustApplyRowVO> rows = result.getRecords().stream()
                .map(this::toRowVO).collect(Collectors.toList());
        enrichApplicantNames(rows);
        return PageResult.of(page.getPageNo(), page.getPageSize(), result.getTotal(), rows);
    }

    /**
     * 批量解析申请人(created_by = PT_USER.USER_ID) → 姓名 + 工号，回填 createdByName / createdByNo.
     *
     * <p>created_by 存的是 USER_ID（如 E10001 / U_xxx），不是工号，故按 USER_ID 走
     * {@link UserApi#getUserByEmpIds(List)} 解析，取 {@code displayName}(姓名) 与 {@code username}(工号)。
     * 一次分页一次批量查询；解析不到（历史脏数据 / 已删用户）的留空，前端降级仅显示 created_by。</p>
     */
    private void enrichApplicantNames(List<AllocAdjustApplyRowVO> rows) {
        if (userApi == null || rows == null || rows.isEmpty()) {
            return;
        }
        List<String> userIds = rows.stream()
                .map(AllocAdjustApplyRowVO::getCreatedBy)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, UserDTO> userByEmpId = new HashMap<>();
        try {
            List<UserDTO> users = userApi.getUserByEmpIds(userIds);
            if (users != null) {
                for (UserDTO u : users) {
                    if (u != null && StringUtils.hasText(u.getEmpId())) {
                        userByEmpId.put(u.getEmpId(), u);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[AllocAdjustQuery] 申请人姓名解析失败，降级仅显示 created_by: {}", e.getMessage());
            return;
        }
        for (AllocAdjustApplyRowVO r : rows) {
            UserDTO u = userByEmpId.get(r.getCreatedBy());
            if (u != null) {
                r.setCreatedByName(u.getDisplayName());
                r.setCreatedByNo(u.getUsername());
            }
        }
    }

    /**
     * 应用 BizType.REPORT 数据范围（DATA_SCOPE）到列表查询.
     *
     * <p>ALL → 不过滤；ORG_SUBTREE / ORG → 按 owner_org_id 过滤本机构(及下属)；
     * SELF / SELF_CREATED / SELF_ASSIGNED / WORKFLOW_PARTICIPANT → 收敛到「本人创建」
     * （created_by = 当前用户 USER_ID）。无 currentUser/bizScope（测试上下文）时不过滤。</p>
     */
    private void applyReportScope(LambdaQueryWrapper<PerfAllocAdjustApply> w) {
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
                java.util.Set<String> codes = scope.orgSubtreeCodes();
                if (codes != null && !codes.isEmpty()) {
                    w.in(PerfAllocAdjustApply::getOwnerOrgId, codes);
                } else {
                    w.eq(PerfAllocAdjustApply::getOwnerOrgId,
                            scope.orgCode() != null ? scope.orgCode() : "__none__");
                }
            }
            case ORG -> w.eq(PerfAllocAdjustApply::getOwnerOrgId,
                    scope.orgCode() != null ? scope.orgCode() : "__none__");
            default -> w.eq(PerfAllocAdjustApply::getCreatedBy, empId != null ? empId : "__none__");
        }
    }

    @Override
    public AllocAdjustApplyDetailVO detail(String id) {
        PerfAllocAdjustApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new RptException(RptErrorCode.ALLOC_ADJUST_APPLY_NOT_FOUND);
        }
        List<AllocAdjustItemVO> items = itemMapper.selectList(
                        new LambdaQueryWrapper<PerfAllocAdjustItem>()
                                .eq(PerfAllocAdjustItem::getApplyId, id))
                .stream().map(this::toItemVO).collect(Collectors.toList());

        AllocAdjustApplyDetailVO vo = new AllocAdjustApplyDetailVO();
        vo.setApply(toRowVO(apply));
        vo.setItems(items);
        return vo;
    }

    private AllocAdjustApplyRowVO toRowVO(PerfAllocAdjustApply e) {
        AllocAdjustApplyRowVO vo = new AllocAdjustApplyRowVO();
        BeanUtils.copyProperties(e, vo, "createdTime");
        LocalDateTime ct = e.getCreatedTime();
        vo.setCreatedTime(ct == null ? null : ct.format(DT_FMT));
        return vo;
    }

    private AllocAdjustItemVO toItemVO(PerfAllocAdjustItem e) {
        AllocAdjustItemVO vo = new AllocAdjustItemVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
