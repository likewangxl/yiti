package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeSaveReqDTO;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.ZhGuaranteeInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 担保信息查询与维护服务。
 *
 * <p>数据源：yiti {@code zh_guarantee_info} 表。提供分页查询、详情、新增、编辑、批量删除能力，
 * 全部基于 MyBatis-Plus {@code BaseMapper} + {@code LambdaQueryWrapper}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuaranteeService {

    private static final DateTimeFormatter UPDATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZhGuaranteeInfoMapper guaranteeMapper;

    /**
     * 分页查询担保信息（按客户名称模糊匹配，按 id 倒序）。
     *
     * @param req 查询入参
     * @return MyBatis-Plus 分页结果
     */
    public IPage<ZhGuaranteeInfo> listGuarantees(GuaranteeQueryReqDTO req) {
        LambdaQueryWrapper<ZhGuaranteeInfo> qw = new LambdaQueryWrapper<ZhGuaranteeInfo>()
                .like(StringUtils.hasText(req.getClientName()), ZhGuaranteeInfo::getClientName, req.getClientName())
                .orderByDesc(ZhGuaranteeInfo::getId);
        return guaranteeMapper.selectPage(new Page<>(req.normalizedPageNo(), req.normalizedPageSize()), qw);
    }

    /**
     * 查询担保信息（导出用，不分页）。
     *
     * <p>多选导出：传入 {@code ids} 非空时仅导出选中记录；否则按客户名称过滤全量导出。</p>
     *
     * @param clientName 客户名称（可空，ids 为空时生效）
     * @param ids        选中主键列表（可空）
     * @return 实体列表（按 id 倒序）
     */
    public List<ZhGuaranteeInfo> listForExport(String clientName, List<Long> ids) {
        LambdaQueryWrapper<ZhGuaranteeInfo> qw = new LambdaQueryWrapper<ZhGuaranteeInfo>()
                .orderByDesc(ZhGuaranteeInfo::getId);
        if (ids != null && !ids.isEmpty()) {
            qw.in(ZhGuaranteeInfo::getId, ids);
        } else {
            qw.like(StringUtils.hasText(clientName), ZhGuaranteeInfo::getClientName, clientName);
        }
        return guaranteeMapper.selectList(qw);
    }

    /**
     * 查询担保信息详情（编辑反显用）。
     *
     * @param id 主键
     * @return 实体
     * @throws BizException 记录不存在（PORTAL-40006）
     */
    public ZhGuaranteeInfo getById(Long id) {
        ZhGuaranteeInfo entity = guaranteeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(PortalErrorCode.GUARANTEE_NOT_FOUND.getCode(),
                    PortalErrorCode.GUARANTEE_NOT_FOUND.getMessage());
        }
        return entity;
    }

    /**
     * 新增担保信息。
     *
     * @param req   入参
     * @param empId 操作人工号（记入 create_user）
     * @return 新记录主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(GuaranteeSaveReqDTO req, String empId) {
        ZhGuaranteeInfo entity = new ZhGuaranteeInfo();
        applySaveFields(entity, req);
        entity.setCreateUser(empId);
        // create_time 列为 NOT NULL DEFAULT CURRENT_TIMESTAMP，显式赋值避免 MyBatis-Plus 传 NULL 触发约束
        entity.setCreateTime(LocalDateTime.now());
        entity.setType("1");
        guaranteeMapper.insert(entity);
        log.info("[Guarantee] create id={} clientName={} by={}", entity.getId(), entity.getClientName(), empId);
        return entity.getId();
    }

    /**
     * 编辑担保信息。
     *
     * @param id    主键
     * @param req   入参
     * @param empId 操作人工号
     * @throws BizException 记录不存在（PORTAL-40006）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, GuaranteeSaveReqDTO req, String empId) {
        ZhGuaranteeInfo entity = getById(id);
        applySaveFields(entity, req);
        // 变更日期记录最近一次修改时间
        entity.setUpdateTime(LocalDateTime.now().format(UPDATE_TIME_FMT));
        guaranteeMapper.updateById(entity);
        log.info("[Guarantee] update id={} by={}", id, empId);
    }

    /**
     * 批量删除担保信息（支持多选）。
     *
     * @param ids 主键列表
     * @return 实际删除行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = guaranteeMapper.deleteBatchIds(ids);
        log.info("[Guarantee] batchDelete ids={} deleted={}", ids, deleted);
        return deleted;
    }

    /** 把保存入参的字段拷贝到实体（新增/编辑共用）。 */
    private void applySaveFields(ZhGuaranteeInfo entity, GuaranteeSaveReqDTO req) {
        entity.setClientName(req.getClientName());
        entity.setNotionalAmount(req.getNotionalAmount());
        entity.setOccupyNotionalAmount(req.getOccupyNotionalAmount());
        entity.setUsableNominalSum(req.getUsableNominalSum());
        entity.setLastExpire(req.getLastExpire());
        entity.setUserName(req.getUserName());
    }
}
