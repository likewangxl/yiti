package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.entity.UserProductRelation;
import com.bank.branch.platform.portal.mapper.UserProductRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户—产品关系领域服务。
 *
 * <p>该服务是关系表的唯一写入口。通讯录本人维护和产品维护端的负责人变更都通过
 * 整组替换完成，避免再维护两个 JSON 反向字段。</p>
 */
@Service
@RequiredArgsConstructor
public class UserProductRelationService {

    private static final int BATCH_SIZE = 500;

    private final UserProductRelationMapper relationMapper;

    /** 按用户获取产品 ID。 */
    public List<String> listProductIdsByUserId(String userId) {
        if (isBlank(userId)) {
            return Collections.emptyList();
        }
        return relationMapper.listByUserId(userId).stream()
                .map(UserProductRelation::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 批量按用户获取产品 ID，返回用户到产品列表的映射。 */
    public Map<String, List<String>> mapProductIdsByUserIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> normalized = normalizeIds(userIds);
        if (normalized.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (List<String> batch : batches(normalized)) {
            List<UserProductRelation> relations = relationMapper.listByUserIds(batch);
            if (relations == null) {
                continue;
            }
            for (UserProductRelation relation : relations) {
                if (relation.getUserId() == null || relation.getProductId() == null) {
                    continue;
                }
                result.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>())
                        .add(relation.getProductId());
            }
        }
        return result;
    }

    /** 按产品获取负责人 ID。 */
    public List<String> listUserIdsByProductId(String productId) {
        if (isBlank(productId)) {
            return Collections.emptyList();
        }
        return relationMapper.listByProductId(productId).stream()
                .map(UserProductRelation::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 批量按产品获取负责人 ID，返回产品到用户列表的映射。 */
    public Map<String, List<String>> mapUserIdsByProductIds(Collection<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> normalized = normalizeIds(productIds);
        if (normalized.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (List<String> batch : batches(normalized)) {
            List<UserProductRelation> relations = relationMapper.listByProductIds(batch);
            if (relations == null) {
                continue;
            }
            for (UserProductRelation relation : relations) {
                if (relation.getProductId() == null || relation.getUserId() == null) {
                    continue;
                }
                result.computeIfAbsent(relation.getProductId(), key -> new ArrayList<>())
                        .add(relation.getUserId());
            }
        }
        return result;
    }

    /** 产品删除前的关系引用数量。 */
    public int countUsersByProductId(String productId) {
        if (isBlank(productId)) {
            return 0;
        }
        return relationMapper.countByProductId(productId);
    }

    /**
     * 原子替换某用户的全部负责产品关系。
     *
     * @param userId   PT_USER.USER_ID
     * @param productIds 新的产品 ID 集合，可为空表示清空
     * @param operator 修改人
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceProductsForUser(String userId, Collection<String> productIds, String operator) {
        requireId(userId, "userId");
        List<String> normalized = normalizeIds(productIds);
        List<String> current = listProductIdsByUserId(userId);
        if (new LinkedHashSet<>(current).equals(new LinkedHashSet<>(normalized))) {
            return;
        }
        relationMapper.deleteByUserId(userId);
        insertRelations(userId, normalized, operator);
    }

    /**
     * 原子替换某产品的全部负责人关系。产品维护端使用，负责人来源仍只有关系表。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceUsersForProduct(String productId, Collection<String> userIds, String operator) {
        requireId(productId, "productId");
        List<String> normalized = normalizeIds(userIds);
        List<String> current = listUserIdsByProductId(productId);
        if (new LinkedHashSet<>(current).equals(new LinkedHashSet<>(normalized))) {
            return;
        }
        List<String> removed = current.stream()
                .filter(userId -> !normalized.contains(userId))
                .collect(Collectors.toList());
        for (List<String> batch : batches(removed)) {
            relationMapper.deleteByProductAndUserIds(productId, batch);
        }
        List<UserProductRelation> added = normalized.stream()
                .filter(userId -> !current.contains(userId))
                .map(userId -> newRelation(userId, productId, operator))
                .collect(Collectors.toList());
        for (List<UserProductRelation> batch : batches(added)) {
            relationMapper.insertBatch(batch);
        }
    }

    /** 批量插入关系实体，供测试和同事务聚合场景使用。 */
    private void insertRelations(String userId, List<String> productIds, String operator) {
        if (productIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<UserProductRelation> relations = productIds.stream()
                .map(productId -> {
                    UserProductRelation relation = new UserProductRelation();
                    relation.setUserId(userId);
                    relation.setProductId(productId);
                    relation.setAssignedTime(now);
                    relation.setUpdatedTime(now);
                    relation.setUpdatedBy(operator);
                    return relation;
                })
                .collect(Collectors.toList());
        for (List<UserProductRelation> batch : batches(relations)) {
            relationMapper.insertBatch(batch);
        }
    }

    private UserProductRelation newRelation(String userId, String productId, String operator) {
        LocalDateTime now = LocalDateTime.now();
        UserProductRelation relation = new UserProductRelation();
        relation.setUserId(userId);
        relation.setProductId(productId);
        relation.setAssignedTime(now);
        relation.setUpdatedTime(now);
        relation.setUpdatedBy(operator);
        return relation;
    }

    private static List<String> normalizeIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (!isBlank(id)) {
                normalized.add(id.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private static void requireId(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 将批量查询和写入限制在单条 SQL 的安全大小内。 */
    private static <T> List<List<T>> batches(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<T>> result = new ArrayList<>((values.size() + BATCH_SIZE - 1) / BATCH_SIZE);
        for (int from = 0; from < values.size(); from += BATCH_SIZE) {
            result.add(values.subList(from, Math.min(from + BATCH_SIZE, values.size())));
        }
        return result;
    }
}
