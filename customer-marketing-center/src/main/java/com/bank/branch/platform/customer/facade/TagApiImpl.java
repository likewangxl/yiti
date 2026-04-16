package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.TagApi;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 标签对外接口实现。
 * <p>
 * 实现 {@link TagApi} 接口，简单委托 {@link TagService} 完成查询。
 * 供其他模块通过 Spring 注入使用，不包含业务逻辑。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagApiImpl implements TagApi {

    private final TagService tagService;

    /**
     * 查询所有启用状态的标签列表。
     *
     * @return 启用的标签列表，按 tagPriority 降序
     */
    @Override
    public List<CustTag> listEnabled() {
        log.debug("[TagApiImpl.listEnabled] called");
        return tagService.listEnabled();
    }

    /**
     * 按 ID 查询标签详情。
     *
     * @param id 标签ID
     * @return 标签实体，不存在时由 TagService 抛出 BizException
     */
    @Override
    public CustTag getById(String id) {
        log.debug("[TagApiImpl.getById] id={}", id);
        return tagService.getById(id);
    }
}
