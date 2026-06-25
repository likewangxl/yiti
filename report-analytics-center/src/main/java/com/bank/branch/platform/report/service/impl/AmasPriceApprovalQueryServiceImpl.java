package com.bank.branch.platform.report.service.impl;

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
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link AmasPriceApprovalQueryService} 实现：只读，MyBatis-Plus 条件查询，对外只返回 VO.
 *
 * <p>查询：客户名称 / 申请人姓名 模糊。分页依赖 common-db 全局注册的
 * {@code PaginationInnerInterceptor}（MySQL 方言）。时间倒序对 varchar 的 {@code APPLY_TIME}
 * 做字符串降序。entity → VO 用 {@link BeanUtils#copyProperties}（同名字段）。</p>
 *
 * <p><strong>数据范围</strong>：不做行级数据范围过滤——本查询对有菜单/接口授权（{@code @BizAuth}
 * RBAC）的用户开放全量定价审批数据，不再按机构层级或申请人工号收敛。原 {@code BizType.REPORT}
 * 的 DATA_SCOPE（{@code APPLY_DEPTNO} 机构上下级控制）已按需求取消。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmasPriceApprovalQueryServiceImpl implements AmasPriceApprovalQueryService {

    private final AmasPriceApprovalMapper priceApprovalMapper;

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
        // 已取消数据范围控制：不按机构层级 / 申请人工号收敛，授权用户可见全量数据
        // 申请时间倒序
        w.orderByDesc(AmasPriceApproval::getApplyTime);

        Page<AmasPriceApproval> mpPage = new Page<>(page.getPageNo(), page.getPageSize());
        Page<AmasPriceApproval> result = priceApprovalMapper.selectPage(mpPage, w);
        List<AmasPriceApprovalVO> rows = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return PageResult.of(page.getPageNo(), page.getPageSize(), result.getTotal(), rows);
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
