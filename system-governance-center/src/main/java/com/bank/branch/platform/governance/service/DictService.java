package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.DictTypeRespDTO;
import com.bank.branch.platform.governance.entity.SysDict;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.mapper.DictMapper;
import com.bank.branch.platform.governance.mapper.DictTypeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 字典管理服务
 * <p>
 * 负责 sys_dict 表的 CRUD 操作，集成 Redis 缓存实现 cache-aside 模式。
 * 缓存 key 格式：gov:dict:{dictType}，TTL 为 10 分钟。
 * 所有写操作完成后自动清除对应字典类型的缓存，保证数据一致性。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DictMapper dictMapper;

    /** 缓存 key 前缀 */
    private static final String CACHE_PREFIX = "gov:dict:";

    /** 缓存过期时间 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /**
     * 查询指定字典类型下的所有启用状态字典项（cache-aside 模式）。
     * <p>
     * 优先从 Redis 缓存读取，缓存未命中时查询数据库并回填缓存。
     * </p>
     *
     * @param dictType 字典类型
     * @return 字典项列表，按 sort_order 升序排列
     */
    @SuppressWarnings("unchecked")
    public List<SysDict> getDictItems(String dictType) {
        String cacheKey = CACHE_PREFIX + dictType;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("[DictService.getDictItems] 缓存命中 dictType={}", dictType);
            return (List<SysDict>) cached;
        }
        // 缓存未命中，从数据库加载
        log.debug("[DictService.getDictItems] 缓存未命中，查询数据库 dictType={}", dictType);
        List<SysDict> items = dictMapper.selectByDictType(dictType);
        redisTemplate.opsForValue().set(cacheKey, items, CACHE_TTL);
        return items;
    }

    /**
     * 获取指定字典类型和编码对应的标签名称。
     *
     * @param dictType 字典类型
     * @param dictCode 字典编码
     * @return 字典标签，未找到时返回 null
     */
    public String getDictLabel(String dictType, String dictCode) {
        List<SysDict> items = getDictItems(dictType);
        return items.stream()
                .filter(item -> item.getDictCode().equals(dictCode))
                .map(SysDict::getDictLabel)
                .findFirst()
                .orElse(null);
    }

    /**
     * 校验指定字典类型和编码是否为合法值。
     *
     * @param dictType 字典类型
     * @param dictCode 字典编码
     * @return 存在返回 true，否则返回 false
     */
    public boolean isValidDictValue(String dictType, String dictCode) {
        List<SysDict> items = getDictItems(dictType);
        return items.stream().anyMatch(item -> item.getDictCode().equals(dictCode));
    }

    /**
     * 批量查询多个字典类型的字典项。
     *
     * @param dictTypes 字典类型集合
     * @return key=dictType, value=该类型下的字典项列表
     */
    public Map<String, List<SysDict>> batchGetDictItems(Set<String> dictTypes) {
        Map<String, List<SysDict>> result = new HashMap<>();
        for (String dictType : dictTypes) {
            result.put(dictType, getDictItems(dictType));
        }
        return result;
    }

    /**
     * 新增字典项。
     * <p>
     * 校验 dictType + dictCode 唯一性，重复时抛出 GOV-40901。
     * 插入成功后清除该字典类型的缓存。
     * </p>
     *
     * @param dictType  字典类型
     * @param dictCode  字典编码
     * @param dictLabel 字典标签
     * @param dictValue 字典值
     * @param sortOrder 排序号
     * @param remark    备注
     * @return 新建的字典实体
     * @throws BizException GOV-40901 当 dictType+dictCode 已存在时
     */
    @Transactional
    public SysDict createDict(String dictType, String dictCode, String dictLabel,
                              String dictValue, Integer sortOrder, String remark) {
        log.info("[DictService.createDict] dictType={}, dictCode={}", dictType, dictCode);
        // 校验 dictType + dictCode 唯一性，防止重复编码导致前端展示混乱
        if (dictMapper.existsByDictTypeAndDictCode(dictType, dictCode)) {
            throw new BizException(GovErrorCode.DICT_CODE_DUPLICATE.getCode(),
                    GovErrorCode.DICT_CODE_DUPLICATE.getMessage());
        }
        String id = "D_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        SysDict dict = new SysDict();
        dict.setId(id);
        dict.setDictType(dictType);
        dict.setDictCode(dictCode);
        dict.setDictLabel(dictLabel);
        dict.setDictValue(dictValue);
        dict.setSortOrder(sortOrder);
        dict.setStatus("ACTIVE");
        dict.setRemark(remark);
        dict.setCreatedTime(LocalDateTime.now());
        dict.setUpdatedTime(LocalDateTime.now());
        dictMapper.insert(dict);
        // 新增后清除缓存，保证下次读取到最新数据
        redisTemplate.delete(CACHE_PREFIX + dictType);
        log.info("[DictService.createDict] 字典创建成功 id={}", id);
        return dict;
    }

    /**
     * 更新字典项。
     *
     * @param id        字典ID
     * @param dictLabel 字典标签
     * @param dictValue 字典值
     * @param sortOrder 排序号
     * @param remark    备注
     * @return 更新后的字典实体
     * @throws BizException GOV-40001 当字典不存在时
     */
    @Transactional
    public SysDict updateDict(String id, String dictLabel, String dictValue,
                              Integer sortOrder, String remark) {
        log.info("[DictService.updateDict] id={}", id);
        SysDict existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BizException(GovErrorCode.DICT_TYPE_NOT_FOUND.getCode(),
                    GovErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
        }
        if (dictLabel != null) existing.setDictLabel(dictLabel);
        if (dictValue != null) existing.setDictValue(dictValue);
        if (sortOrder != null) existing.setSortOrder(sortOrder);
        if (remark != null) existing.setRemark(remark);
        existing.setUpdatedTime(LocalDateTime.now());
        dictMapper.updateById(existing);
        // 更新后清除缓存
        redisTemplate.delete(CACHE_PREFIX + existing.getDictType());
        return existing;
    }

    /**
     * 逻辑删除字典项（将状态设为 DISABLED）。
     *
     * @param id 字典ID
     * @throws BizException GOV-40001 当字典不存在时
     */
    @Transactional
    public void deleteDict(String id) {
        log.info("[DictService.deleteDict] id={}", id);
        SysDict existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BizException(GovErrorCode.DICT_TYPE_NOT_FOUND.getCode(),
                    GovErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
        }
        existing.setStatus("DISABLED");
        existing.setUpdatedTime(LocalDateTime.now());
        dictMapper.updateById(existing);
        // 逻辑删除后清除缓存
        redisTemplate.delete(CACHE_PREFIX + existing.getDictType());
        log.info("[DictService.deleteDict] 字典已逻辑删除 id={}", id);
    }

    /**
     * 切换字典项状态（ACTIVE ↔ DISABLED）。
     *
     * @param id 字典ID
     * @return 切换后的字典实体
     * @throws BizException GOV-40001 当字典不存在时
     */
    @Transactional
    public SysDict toggleStatus(String id) {
        log.info("[DictService.toggleStatus] id={}", id);
        SysDict existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BizException(GovErrorCode.DICT_TYPE_NOT_FOUND.getCode(),
                    GovErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
        }
        // 翻转状态：ACTIVE → DISABLED，DISABLED → ACTIVE
        String newStatus = "ACTIVE".equals(existing.getStatus()) ? "DISABLED" : "ACTIVE";
        existing.setStatus(newStatus);
        existing.setUpdatedTime(LocalDateTime.now());
        dictMapper.updateById(existing);
        redisTemplate.delete(CACHE_PREFIX + existing.getDictType());
        log.info("[DictService.toggleStatus] 状态已切换 id={}, newStatus={}", id, newStatus);
        return existing;
    }

    /**
     * 设置字典项状态（启用/禁用）。
     *
     * @param id     字典ID
     * @param status 目标状态（ACTIVE/DISABLED）
     * @return 更新后的字典实体
     * @throws BizException GOV-40001 当字典不存在时
     */
    @Transactional
    public SysDict updateStatus(String id, String status) {
        log.info("[DictService.updateStatus] id={}, status={}", id, status);
        SysDict existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BizException(GovErrorCode.DICT_TYPE_NOT_FOUND.getCode(),
                    GovErrorCode.DICT_TYPE_NOT_FOUND.getMessage());
        }
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException("GOV-40002", "状态值无效，仅支持 ACTIVE 或 DISABLED");
        }
        existing.setStatus(status);
        existing.setUpdatedTime(LocalDateTime.now());
        dictMapper.updateById(existing);
        redisTemplate.delete(CACHE_PREFIX + existing.getDictType());
        log.info("[DictService.updateStatus] 状态已更新 id={}, newStatus={}", id, status);
        return existing;
    }

    /**
     * 分页查询字典列表。
     *
     * @param dictType 字典类型，为 null 时不过滤
     * @param keyword  关键词，为 null 时不过滤
     * @param pageNo   当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<SysDict> listByPage(String dictType, String keyword, int pageNo, int pageSize) {
        log.debug("[DictService.listByPage] dictType={}, keyword={}, pageNo={}, pageSize={}",
                dictType, keyword, pageNo, pageSize);
        int offset = (pageNo - 1) * pageSize;
        long total = dictMapper.countByPage(dictType, keyword);
        List<SysDict> records = dictMapper.selectByPage(dictType, keyword, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 查询字典类型列表（按类型聚合）。
     * <p>
     * 用于 A.1 字典类型列表查询（GET /api/sys/dicts）。
     * 按字典类型分组聚合，返回每种类型的汇总信息。
     * </p>
     *
     * @param dictType 字典类型精确匹配，为 null 时不过滤
     * @param keyword  关键词模糊搜索字典类型，为 null 时不过滤
     * @param status   状态筛选（ACTIVE/DISABLED/null 不过滤）
     * @return 字典类型汇总列表
     */
    public List<DictTypeRespDTO> listDictTypes(String dictType, String keyword, String status) {
        log.debug("[DictService.listDictTypes] dictType={}, keyword={}, status={}", dictType, keyword, status);
        List<DictTypeVO> vos = dictMapper.selectGroupByType(dictType, keyword, status);
        return vos.stream().map(vo -> {
            DictTypeRespDTO dto = new DictTypeRespDTO();
            dto.setDictType(vo.getDictType());
            dto.setDictTypeLabel(vo.getRemark() != null && !vo.getRemark().isEmpty()
                    ? vo.getRemark() : vo.getDictType());
            dto.setItemCount(vo.getActiveCount() != null ? vo.getActiveCount().intValue() : 0);
            dto.setStatus(vo.getActiveCount() != null && vo.getActiveCount() > 0 ? "ACTIVE" : "DISABLED");
            return dto;
        }).collect(Collectors.toList());
    }
}
