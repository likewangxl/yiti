package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TagStatus;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 标签管理业务服务。
 * <p>
 * 负责标签的 CRUD、启禁用及列表查询，是标签管理域的核心逻辑入口。
 * 所有数据库操作委托给 {@link CustTagMapper}，不直接调用其他模块的 Mapper。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final CustTagMapper tagMapper;

    /**
     * 创建新标签。
     * <p>
     * 先做唯一性预检（名称、编码均不可重复），通过后插入，主键由本方法生成（UUID 32位）。
     * 新标签默认状态为 ACTIVE。
     * </p>
     *
     * @param tagName       标签名称（唯一）
     * @param tagCode       标签编码（唯一，创建后不可修改）
     * @param description   标签描述（可为 null）
     * @param tagCategory   标签分类（可为 null）
     * @param tagPriority   排序优先级（可为 null，越大越靠前）
     * @param operatorEmpId 操作人员工工号
     * @return 插入成功后完整的标签实体
     */
    public CustTag createTag(String tagName, String tagCode, String description,
                              String tagCategory, Integer tagPriority, String operatorEmpId) {
        log.info("[TagService.createTag] tagName={}, tagCode={}, operator={}", tagName, tagCode, operatorEmpId);

        // 名称唯一性校验
        if (tagMapper.selectByTagName(tagName) != null) {
            throw new BizException(CustomerErrorCode.TAG_NAME_DUPLICATE.getCode(),
                    CustomerErrorCode.TAG_NAME_DUPLICATE.getMessage());
        }

        // 编码唯一性校验
        if (tagMapper.selectByTagCode(tagCode) != null) {
            throw new BizException(CustomerErrorCode.TAG_CODE_DUPLICATE.getCode(),
                    CustomerErrorCode.TAG_CODE_DUPLICATE.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        CustTag entity = new CustTag();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTagName(tagName);
        entity.setTagCode(tagCode);
        entity.setDescription(description);
        entity.setTagCategory(tagCategory);
        entity.setTagPriority(tagPriority);
        entity.setStatus(TagStatus.ACTIVE.getCode());
        entity.setCreatedBy(operatorEmpId);
        entity.setCreatedTime(now);
        entity.setUpdatedBy(operatorEmpId);
        entity.setUpdatedTime(now);
        entity.setDeleted(0);

        tagMapper.insert(entity);
        log.info("[TagService.createTag] created tagId={}", entity.getId());
        return entity;
    }

    /**
     * 更新标签信息。
     * <p>
     * tagCode（标签编码）在创建后不可修改，若传入的 tagCode 与现有编码不一致则抛出业务异常。
     * tagName 仍需唯一性检查（排除自身）。
     * </p>
     *
     * @param id            标签 ID
     * @param tagName       新标签名称
     * @param tagCode       标签编码（必须与原值一致）
     * @param description   新描述
     * @param tagCategory   新分类
     * @param tagPriority   新优先级
     * @param operatorEmpId 操作人
     * @return 更新后的标签实体
     */
    public CustTag updateTag(String id, String tagName, String tagCode, String description,
                              String tagCategory, Integer tagPriority, String operatorEmpId) {
        log.info("[TagService.updateTag] id={}, tagName={}, operator={}", id, tagName, operatorEmpId);

        CustTag existing = requireExists(id);

        // tagCode 不可修改
        if (tagCode != null && !tagCode.equals(existing.getTagCode())) {
            throw new BizException(CustomerErrorCode.TAG_CODE_IMMUTABLE.getCode(),
                    CustomerErrorCode.TAG_CODE_IMMUTABLE.getMessage());
        }

        // 名称唯一性校验（排除自身）
        if (tagName != null && !tagName.equals(existing.getTagName())) {
            CustTag nameConflict = tagMapper.selectByTagName(tagName);
            if (nameConflict != null && !nameConflict.getId().equals(id)) {
                throw new BizException(CustomerErrorCode.TAG_NAME_DUPLICATE.getCode(),
                        CustomerErrorCode.TAG_NAME_DUPLICATE.getMessage());
            }
        }

        CustTag updateEntity = new CustTag();
        updateEntity.setId(id);
        updateEntity.setTagName(tagName);
        updateEntity.setDescription(description);
        updateEntity.setTagCategory(tagCategory);
        updateEntity.setTagPriority(tagPriority);
        updateEntity.setUpdatedBy(operatorEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        tagMapper.updateById(updateEntity);

        // 返回更新后的完整对象（合并变更到原实体）
        existing.setTagName(tagName != null ? tagName : existing.getTagName());
        existing.setDescription(description != null ? description : existing.getDescription());
        existing.setTagCategory(tagCategory != null ? tagCategory : existing.getTagCategory());
        existing.setTagPriority(tagPriority != null ? tagPriority : existing.getTagPriority());
        existing.setUpdatedBy(operatorEmpId);
        existing.setUpdatedTime(updateEntity.getUpdatedTime());
        log.info("[TagService.updateTag] updated tagId={}", id);
        return existing;
    }

    /**
     * 切换标签的启用/停用状态。
     *
     * @param id            标签 ID
     * @param status        目标状态（ACTIVE / DISABLED）
     * @param operatorEmpId 操作人
     */
    public void toggleStatus(String id, String status, String operatorEmpId) {
        log.info("[TagService.toggleStatus] id={}, status={}, operator={}", id, status, operatorEmpId);

        requireExists(id);

        CustTag updateEntity = new CustTag();
        updateEntity.setId(id);
        updateEntity.setStatus(status);
        updateEntity.setUpdatedBy(operatorEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        tagMapper.updateById(updateEntity);
    }

    /**
     * 按 ID 查询标签，不存在时抛出 BizException。
     *
     * @param id 标签 ID
     * @return 标签实体
     */
    public CustTag getById(String id) {
        log.debug("[TagService.getById] id={}", id);
        return requireExists(id);
    }

    /**
     * 分页查询标签列表。
     * <p>
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword  关键词（模糊匹配 tagName / tagCode），可为 null
     * @param status   状态过滤，可为 null
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<CustTag> listPage(String keyword, String status, int pageNo, int pageSize) {
        log.debug("[TagService.listPage] keyword={}, status={}, pageNo={}, pageSize={}", keyword, status, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<CustTag> records = tagMapper.selectPage(keyword, status, offset, pageSize);
        long total = tagMapper.countPage(keyword, status);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 查询所有启用状态的标签（用于打标下拉选择）。
     *
     * @return 启用的标签列表，按 tagPriority 降序
     */
    public List<CustTag> listEnabled() {
        log.debug("[TagService.listEnabled] called");
        return tagMapper.selectEnabled();
    }

    // ============================= 私有辅助方法 =============================

    /**
     * 断言标签存在，不存在时抛出 BizException。
     * <p>
     * 避免在多个方法中重复查询+判空逻辑。
     * </p>
     *
     * @param id 标签 ID
     * @return 已存在的标签实体
     */
    private CustTag requireExists(String id) {
        CustTag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(),
                    CustomerErrorCode.TAG_NOT_FOUND.getMessage());
        }
        return tag;
    }
}
