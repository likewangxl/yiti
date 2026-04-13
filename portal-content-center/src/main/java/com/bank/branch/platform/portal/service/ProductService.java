package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.api.dto.ProductSimpleDTO;
import com.bank.branch.platform.portal.convert.ProductConverter;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品资料库 Service
 * <p>D.1-D.7 的业务逻辑实现入口</p>
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductInfoMapper productInfoMapper;

    /**
     * D.3 查询所有支持中场支持的产品（active + 未删除）
     * <p>V1 不实现 5min Redis 缓存（spec 附录 B2 登记的 V2 待办）</p>
     *
     * @return 支持中场支持的产品简要列表
     */
    public List<ProductSimpleDTO> listSupportAvailable() {
        return productInfoMapper.listSupportAvailable().stream()
                .map(ProductConverter::toSimple)
                .collect(Collectors.toList());
    }
}
