package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.AmasApprovalQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.AmasAllocationVO;
import com.bank.branch.platform.report.dto.resp.AmasApprRecordVO;
import com.bank.branch.platform.report.dto.resp.AmasApprovalDetailVO;
import com.bank.branch.platform.report.dto.resp.AmasApprovalRowVO;
import com.bank.branch.platform.report.entity.AmasApprRecord;
import com.bank.branch.platform.report.entity.AmasPerfAdjustApproval;
import com.bank.branch.platform.report.entity.AmasPerformanceAllocation;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.AmasApprRecordMapper;
import com.bank.branch.platform.report.mapper.AmasPerfAdjustApprovalMapper;
import com.bank.branch.platform.report.mapper.AmasPerformanceAllocationMapper;
import com.bank.branch.platform.report.service.AmasApprovalQueryService;
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
 * {@link AmasApprovalQueryService} 实现：只读，全部走 MyBatis-Plus 条件查询，对外只返回 VO.
 *
 * <p>分页依赖 common-db 全局注册的 {@code PaginationInnerInterceptor}（MySQL 方言）。
 * 时间倒序对 varchar 的 {@code APPLY_TIME} 做字符串降序（来源为定长 yyyy-MM-dd HH:mm:ss）。
 * entity → VO 用 {@link BeanUtils#copyProperties}（同名字段）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmasApprovalQueryServiceImpl implements AmasApprovalQueryService {

    private final AmasPerfAdjustApprovalMapper approvalMapper;
    private final AmasPerformanceAllocationMapper allocationMapper;
    private final AmasApprRecordMapper apprRecordMapper;

    @Override
    public PageResult<AmasApprovalRowVO> pageList(AmasApprovalQueryReqDTO req, PageRequest page) {
        AmasApprovalQueryReqDTO q = req == null ? new AmasApprovalQueryReqDTO() : req;
        LambdaQueryWrapper<AmasPerfAdjustApproval> w = new LambdaQueryWrapper<>();
        // 业绩调整编号：模糊
        if (StringUtils.hasText(q.getPerfAdjustNo())) {
            w.like(AmasPerfAdjustApproval::getPerfAdjustNo, q.getPerfAdjustNo().trim());
        }
        // 申请人工号：精确
        if (StringUtils.hasText(q.getApplyUsername())) {
            w.eq(AmasPerfAdjustApproval::getApplyUsername, q.getApplyUsername().trim());
        }
        // 客户关键词：客户号 或 客户名称 模糊
        if (StringUtils.hasText(q.getCustKeyword())) {
            String kw = q.getCustKeyword().trim();
            w.and(c -> c.like(AmasPerfAdjustApproval::getCustId, kw)
                    .or().like(AmasPerfAdjustApproval::getCustName, kw));
        }
        // 审批状态：精确
        if (StringUtils.hasText(q.getApprStatus())) {
            w.eq(AmasPerfAdjustApproval::getApprStatus, q.getApprStatus().trim());
        }
        // 申请时间区间（字符串比较）
        if (StringUtils.hasText(q.getApplyTimeStart())) {
            w.ge(AmasPerfAdjustApproval::getApplyTime, q.getApplyTimeStart().trim());
        }
        if (StringUtils.hasText(q.getApplyTimeEnd())) {
            w.le(AmasPerfAdjustApproval::getApplyTime, q.getApplyTimeEnd().trim());
        }
        // 申请时间倒序
        w.orderByDesc(AmasPerfAdjustApproval::getApplyTime);

        Page<AmasPerfAdjustApproval> mpPage = new Page<>(page.getPageNo(), page.getPageSize());
        Page<AmasPerfAdjustApproval> result = approvalMapper.selectPage(mpPage, w);
        List<AmasApprovalRowVO> rows = result.getRecords().stream()
                .map(this::toRowVO).collect(Collectors.toList());
        return PageResult.of(page.getPageNo(), page.getPageSize(), result.getTotal(), rows);
    }

    @Override
    public AmasApprovalDetailVO detail(String perfAdjustNo) {
        AmasPerfAdjustApproval approval = approvalMapper.selectById(perfAdjustNo);
        if (approval == null) {
            throw new RptException(RptErrorCode.AMAS_APPROVAL_NOT_FOUND);
        }
        // 业绩分配明细：按 PERF_ADJUST_NO 关联
        List<AmasAllocationVO> allocations = allocationMapper.selectList(
                        new LambdaQueryWrapper<AmasPerformanceAllocation>()
                                .eq(AmasPerformanceAllocation::getPerfAdjustNo, perfAdjustNo))
                .stream().map(this::toAllocationVO).collect(Collectors.toList());
        // 审批流程：REGION_DT_ID 关联，按序号倒序
        List<AmasApprRecordVO> apprRecords = apprRecordMapper.selectList(
                        new LambdaQueryWrapper<AmasApprRecord>()
                                .eq(AmasApprRecord::getRegionDtId, perfAdjustNo)
                                .orderByDesc(AmasApprRecord::getApprSeq))
                .stream().map(this::toApprRecordVO).collect(Collectors.toList());

        AmasApprovalDetailVO vo = new AmasApprovalDetailVO();
        vo.setApproval(toRowVO(approval));
        vo.setAllocations(allocations);
        vo.setApprRecords(apprRecords);
        return vo;
    }

    private AmasApprovalRowVO toRowVO(AmasPerfAdjustApproval e) {
        AmasApprovalRowVO vo = new AmasApprovalRowVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }

    private AmasAllocationVO toAllocationVO(AmasPerformanceAllocation e) {
        AmasAllocationVO vo = new AmasAllocationVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }

    private AmasApprRecordVO toApprRecordVO(AmasApprRecord e) {
        AmasApprRecordVO vo = new AmasApprRecordVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}
