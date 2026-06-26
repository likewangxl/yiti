package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeCreateResultDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeSaveReqDTO;
import com.bank.branch.platform.portal.entity.CcmsBusinessContract;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.CcmsBusinessContractMapper;
import com.bank.branch.platform.portal.mapper.ZhGuaranteeInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final CcmsBusinessContractMapper contractMapper;

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
     * <p>业务规则（按客户号 + 客户名称驱动）：</p>
     * <ol>
     *   <li>先校验 {@code zh_guarantee_info} 是否已存在该客户（客户号 + 客户名称命中任一条），
     *       已存在则抛 {@link PortalErrorCode#GUARANTEE_CLIENT_EXISTS}「客户已存在」。</li>
     *   <li>不存在则按客户号 + 客户名称反查 {@code ccms_business_contract}：
     *     <ul>
     *       <li>命中合同记录 → <b>忽略前端表单数据</b>，将合同记录批量映射落库；</li>
     *       <li>未命中 → 落库前端录入的单条数据。</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param req   入参（含客户号、客户名称及手工录入字段）
     * @param empId 操作人工号（记入 create_user）
     * @return 新增结果（来源 + 落库条数；MANUAL 来源附带新记录主键）
     * @throws BizException 客户已存在（PORTAL-40906）
     */
    @Transactional(rollbackFor = Exception.class)
    public GuaranteeCreateResultDTO create(GuaranteeSaveReqDTO req, String empId) {
        // 1) 重复校验：客户号 + 客户名称已存在则拒绝
        if (clientExists(req.getClientNo(), req.getClientName())) {
            throw new BizException(PortalErrorCode.GUARANTEE_CLIENT_EXISTS.getCode(),
                    PortalErrorCode.GUARANTEE_CLIENT_EXISTS.getMessage());
        }

        // 2) 反查合同表：命中则按合同批量导入，前端数据不落库
        List<CcmsBusinessContract> contracts = contractMapper.selectList(
                new LambdaQueryWrapper<CcmsBusinessContract>()
                        .eq(CcmsBusinessContract::getCustomerId, req.getClientNo())
                        .eq(CcmsBusinessContract::getCustomerName, req.getClientName()));
        if (!contracts.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<ZhGuaranteeInfo> rows = new ArrayList<>(contracts.size());
            for (CcmsBusinessContract c : contracts) {
                rows.add(toEntityFromContract(c, empId, now));
            }
            int inserted = guaranteeMapper.batchInsert(rows);
            log.info("[Guarantee] create from contract clientNo={} clientName={} inserted={} by={}",
                    req.getClientNo(), req.getClientName(), inserted, empId);
            return GuaranteeCreateResultDTO.fromContract(inserted);
        }

        // 3) 合同表无记录：落库前端录入的单条数据
        ZhGuaranteeInfo entity = new ZhGuaranteeInfo();
        applySaveFields(entity, req);
        entity.setCreateUser(empId);
        // create_time 列为 NOT NULL DEFAULT CURRENT_TIMESTAMP，显式赋值避免 MyBatis-Plus 传 NULL 触发约束
        entity.setCreateTime(LocalDateTime.now());
        entity.setType("1");
        guaranteeMapper.insert(entity);
        log.info("[Guarantee] create manual id={} clientNo={} clientName={} by={}",
                entity.getId(), entity.getClientNo(), entity.getClientName(), empId);
        return GuaranteeCreateResultDTO.manual(entity.getId());
    }

    /** 校验 {@code zh_guarantee_info} 是否已存在该客户（客户号 + 客户名称同时匹配）。 */
    private boolean clientExists(String clientNo, String clientName) {
        Long count = guaranteeMapper.selectCount(new LambdaQueryWrapper<ZhGuaranteeInfo>()
                .eq(ZhGuaranteeInfo::getClientNo, clientNo)
                .eq(ZhGuaranteeInfo::getClientName, clientName));
        return count != null && count > 0;
    }

    /**
     * 合同记录 → 担保信息实体映射（合同导入）。
     *
     * <p>字段映射口径见 {@code db/ccms_to_guarantee.jpg}（toGuarantee）。金额列合同侧为 decimal「元」，
     * 落库到担保表 varchar 时按「元」原值存储，不做任何换算。basic_id 合同表无对应列恒为 null；
     * 占用名义金额合同表无直接对应列，保持为空。</p>
     */
    private ZhGuaranteeInfo toEntityFromContract(CcmsBusinessContract c, String empId, LocalDateTime now) {
        ZhGuaranteeInfo e = new ZhGuaranteeInfo();
        e.setBasicId(c.getBasicId());                                      // 客户基础id（合同表无此列，恒 null）
        e.setClientNo(c.getCustomerId());                                 // 客户号 → client_num
        e.setClientName(c.getCustomerName());
        e.setAmountType(c.getCreditTypeFlag());
        e.setNotionalAmount(decimalToString(c.getBusinessSum2()));        // 名义金额
        e.setOccupyExposureAmount(decimalToString(c.getExposureBalance())); // 已占用敞口金额
        e.setExpired(c.getMaturity());                                     // 额度到期日 → 到期日
        e.setStart(c.getPutoutDate());                                    // 额度生效日 → start
        e.setLastExpire(c.getTermDate3());                                 // 业务最后到期日
        e.setOrgan(c.getOperateOrgId());                                  // 经办机构
        e.setUserName(c.getOperateUserId());                              // 经办人 → operator 列
        e.setUsableExposureSum(decimalToString(c.getUsableExposureSum())); // 可用敞口金额
        e.setUsableNominalSum(decimalToString(c.getUsableNominalSum()));   // 可用名义金额(剩余额度)
        e.setCreateUser(empId);
        e.setCreateTime(now);
        e.setType("1");
        return e;
    }

    /** decimal → 字符串：去掉多余尾零，空值返回 null（避免落库 "1000.000000" 这类冗余串）。 */
    private static String decimalToString(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
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

    /**
     * 把保存入参的字段拷贝到实体（新增/编辑共用）。
     *
     * <p>金额按「元」原值存储，仅清洗前端可能带入的货币格式特殊字符（{@code ¥}、千分位 {@code ,}），
     * 口径见 {@code db/front_insert_replace.jpg}。</p>
     */
    private void applySaveFields(ZhGuaranteeInfo entity, GuaranteeSaveReqDTO req) {
        entity.setClientNo(req.getClientNo());
        entity.setClientName(req.getClientName());
        entity.setNotionalAmount(stripAmount(req.getNotionalAmount()));
        entity.setOccupyNotionalAmount(stripAmount(req.getOccupyNotionalAmount()));
        entity.setUsableNominalSum(stripAmount(req.getUsableNominalSum()));
        entity.setLastExpire(req.getLastExpire());
        entity.setUserName(req.getUserName());
    }

    /** 清洗金额串：去掉货币符号 {@code ¥} 与千分位 {@code ,}，空值原样返回（front_insert_replace.jpg 口径）。 */
    private static String stripAmount(String v) {
        return v == null ? null : v.replace("¥", "").replace(",", "");
    }
}
