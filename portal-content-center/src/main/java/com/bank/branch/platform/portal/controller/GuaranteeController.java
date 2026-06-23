package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeBatchDeleteReqDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeSaveReqDTO;
import com.bank.branch.platform.portal.convert.GuaranteeConverter;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.bank.branch.platform.portal.service.GuaranteeExportService;
import com.bank.branch.platform.portal.service.GuaranteeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 担保信息查询 REST Controller。
 *
 * <p>数据源 yiti {@code zh_guarantee_info}。提供列表/详情/新增/编辑/批量删除/导出。</p>
 *
 * <p>鉴权说明：担保信息属门户通用域只读/维护能力，复用 {@link BizType#PRODUCT}（门户既有 BizType）
 * 作为数据范围解析口径——本表为全行级共享参考数据，不做行级数据范围裁剪，故复用对功能无影响；
 * 细粒度授权由 PT_RESOURCE 资源（RES_GUARANTEE_*）+ 角色绑定保证。</p>
 */
@RestController
@RequestMapping("/api/guarantee")
@RequiredArgsConstructor
public class GuaranteeController {

    private final GuaranteeService guaranteeService;
    private final GuaranteeExportService guaranteeExportService;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;

    /** 分页查询担保信息列表。 */
    @GetMapping
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.LIST)
    public ResponseWrapper<GuaranteeDTO> list(@Valid GuaranteeQueryReqDTO req) {
        IPage<ZhGuaranteeInfo> page = guaranteeService.listGuarantees(req);
        List<GuaranteeDTO> dtos = page.getRecords().stream()
                .map(GuaranteeConverter::toDTO)
                .collect(Collectors.toList());
        fillUserDisplayName(dtos);
        return ResponseWrapper.page(PageResult.of(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), dtos));
    }

    /**
     * 批量把经办人工号（operator，= PT_USER.username）解析为姓名，填充 {@code userDisplayName}。
     * 查无 / 离职 / 解析异常时回退为工号本身，保证列表主标题不空白。一次 IN 查询，避免逐行 N+1。
     */
    private void fillUserDisplayName(List<GuaranteeDTO> dtos) {
        List<String> empNos = dtos.stream()
                .map(GuaranteeDTO::getUserName)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (empNos.isEmpty()) {
            return;
        }
        Map<String, String> nameMap;
        try {
            nameMap = userApi.getUsersByUsernames(empNos).stream()
                    .filter(u -> u.getUsername() != null)
                    .collect(Collectors.toMap(UserDTO::getUsername,
                            u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                                    ? u.getDisplayName() : u.getUsername(),
                            (a, b) -> a));
        } catch (Exception e) {
            nameMap = Map.of();
        }
        for (GuaranteeDTO dto : dtos) {
            String no = dto.getUserName();
            dto.setUserDisplayName(no == null ? null : nameMap.getOrDefault(no, no));
        }
    }

    /** 担保信息详情（编辑反显用）。 */
    @GetMapping("/{id:[0-9]{1,19}}")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.READ)
    public ResponseWrapper<GuaranteeDTO> get(@PathVariable Long id) {
        return ResponseWrapper.success(GuaranteeConverter.toDTO(guaranteeService.getById(id)));
    }

    /** 新增担保信息。 */
    @PostMapping
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.WRITE)
    public ResponseWrapper<Long> create(@Valid @RequestBody GuaranteeSaveReqDTO req) {
        return ResponseWrapper.success(guaranteeService.create(req, currentUserApi.getCurrentEmpId()));
    }

    /** 编辑担保信息。 */
    @PutMapping("/{id:[0-9]{1,19}}")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.WRITE)
    public ResponseWrapper<Void> update(@PathVariable Long id, @Valid @RequestBody GuaranteeSaveReqDTO req) {
        guaranteeService.update(id, req, currentUserApi.getCurrentEmpId());
        return ResponseWrapper.success(null);
    }

    /** 批量删除担保信息（支持多选）。 */
    @PostMapping("/batch-delete")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.DELETE)
    public ResponseWrapper<Integer> batchDelete(@Valid @RequestBody GuaranteeBatchDeleteReqDTO req) {
        return ResponseWrapper.success(guaranteeService.batchDelete(req.getIds()));
    }

    /**
     * 导出担保信息（V1 同步导出）。
     *
     * <p>多选导出：传 {@code ids} 时仅导出选中记录；否则按 {@code clientName} 过滤全量导出。</p>
     */
    @GetMapping("/export")
    @BizAuth(bizType = BizType.PRODUCT, action = BizAction.EXPORT)
    public void export(@RequestParam(required = false) String clientName,
                       @RequestParam(required = false) List<Long> ids,
                       HttpServletResponse response) throws IOException {
        String empId = currentUserApi.getCurrentEmpId();
        String filename = "guarantee_export_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        guaranteeExportService.exportToStream(clientName, ids, response.getOutputStream(), empId);
    }
}
