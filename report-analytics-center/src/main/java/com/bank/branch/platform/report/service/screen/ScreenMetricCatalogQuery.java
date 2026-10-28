package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.resp.ScreenMetricCatalogItemDTO;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 为大屏绑定页提供 PERF_METRIC_DEF 只读目录。
 *
 * <p>目录走既有只读数据源，不依赖绩效配置权限；真正取数仍由宽表引擎按槽位翻译。
 */
@Service
@RequiredArgsConstructor
public class ScreenMetricCatalogQuery {

    private static final int KEYWORD_LIMIT = 64;

    private final ScreenMetricSlotDao slotDao;

    /**
     * 按基础维度和关键字返回可展示的指标。关键字只在内存过滤，超长时截断而不是拼进 SQL。
     */
    public List<ScreenMetricCatalogItemDTO> list(String baseDim, String keyword) {
        String safeKeyword = keyword == null ? null : keyword.trim();
        if (safeKeyword != null && safeKeyword.length() > KEYWORD_LIMIT) {
            safeKeyword = safeKeyword.substring(0, KEYWORD_LIMIT);
        }
        return ScreenMetricCatalogMapper.toItems(slotDao.listActiveDefinitions(), baseDim, safeKeyword);
    }
}
