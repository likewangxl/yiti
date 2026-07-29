package com.bank.branch.platform.governance.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.PersonTagCreateReqDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagImportResultDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberAddReqDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberRespDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberUpdateReqDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagOrgImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagOrgMemberImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagRespDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagUpdateReqDTO;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.bank.branch.platform.governance.service.PersonTagImportService;
import com.bank.branch.platform.governance.service.PersonTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 人员标签管理控制器（全平台通用，系统设置 > 人员标签）。
 * <p>标签 CRUD（删除级联删关联）+ 成员管理（新增/修改/删除/分页）+ Excel 导入
 * （全局导入缺标签自建、详情导入整标签全量覆盖）。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "人员标签管理", description = "人员标签的增删改查、成员管理与 Excel 导入")
@RequestMapping("/api/admin/sys/person-tags")
public class AdminPersonTagController {

    private final PersonTagService personTagService;
    private final PersonTagImportService personTagImportService;

    /**
     * 分页查询标签列表（含关联人数）。
     *
     * @param keyword  标签名称模糊关键字（可空）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询人员标签（含关联人数）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PageResult<PersonTagRespDTO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseWrapper.success(personTagService.pageTags(keyword, pageNo, pageSize));
    }

    /**
     * 新建标签。
     *
     * @param req 创建请求
     * @return 新建标签行
     */
    @PostMapping
    @Operation(summary = "新建人员标签")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<PersonTag> create(@Valid @RequestBody PersonTagCreateReqDTO req) {
        String operator = DataScopeContext.current().getEmpId();
        log.info("[AdminPersonTagController.create] operator={}, tagName={}", operator, req.getTagName());
        return ResponseWrapper.success(personTagService.createTag(req.getTagName(), req.getRemark(), operator));
    }

    /**
     * 编辑标签（名称/备注）。
     *
     * @param tagId 标签 ID
     * @param req   更新请求
     * @return 空响应
     */
    @PutMapping("/{tagId}")
    @Operation(summary = "编辑人员标签")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> update(@PathVariable("tagId") Long tagId,
                                        @Valid @RequestBody PersonTagUpdateReqDTO req) {
        String operator = DataScopeContext.current().getEmpId();
        log.info("[AdminPersonTagController.update] operator={}, tagId={}", operator, tagId);
        personTagService.updateTag(tagId, req.getTagName(), req.getRemark(), operator);
        return ResponseWrapper.success();
    }

    /**
     * 删除标签（级联删除其下全部人员关联）。
     *
     * @param tagId 标签 ID
     * @return 空响应
     */
    @DeleteMapping("/{tagId}")
    @Operation(summary = "删除人员标签（级联删除关联人员）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.DELETE)
    public ResponseWrapper<Void> delete(@PathVariable("tagId") Long tagId) {
        log.info("[AdminPersonTagController.delete] operator={}, tagId={}",
                DataScopeContext.current().getEmpId(), tagId);
        personTagService.deleteTag(tagId);
        return ResponseWrapper.success();
    }

    /**
     * 分页查询标签某维度下的成员（EMP=工号 / ORG=机构编号+名称实时解析）。
     *
     * @param tagId    标签 ID
     * @param dim      成员维度（EMP/ORG，默认 EMP）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小
     * @return 分页结果
     */
    @GetMapping("/{tagId}/members")
    @Operation(summary = "分页查询标签成员（按维度：员工工号 / 机构编号）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PageResult<PersonTagMemberRespDTO>> members(
            @PathVariable("tagId") Long tagId,
            @RequestParam(value = "dim", defaultValue = "EMP") String dim,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseWrapper.success(personTagService.pageMembers(tagId, dim, pageNo, pageSize));
    }

    /**
     * 新增成员（可同时批量提交员工工号与机构编号，已在标签下的同维度成员跳过）。
     *
     * @param tagId 标签 ID
     * @param req   员工工号列表 + 机构编号列表（至少一个非空）
     * @return 实际新增条数（员工+机构合计）
     */
    @PostMapping("/{tagId}/members")
    @Operation(summary = "新增标签成员（批量员工工号 / 机构编号）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Integer> addMembers(@PathVariable("tagId") Long tagId,
                                               @Valid @RequestBody PersonTagMemberAddReqDTO req) {
        String operator = DataScopeContext.current().getEmpId();
        int empSize = req.getUsernames() == null ? 0 : req.getUsernames().size();
        int orgSize = req.getOrgDeptNos() == null ? 0 : req.getOrgDeptNos().size();
        log.info("[AdminPersonTagController.addMembers] operator={}, tagId={}, empSize={}, orgSize={}",
                operator, tagId, empSize, orgSize);
        return ResponseWrapper.success(
                personTagService.addMembers(tagId, req.getUsernames(), req.getOrgDeptNos(), operator));
    }

    /**
     * 修改成员（按行维度换成另一个工号 / 机构编号）。
     *
     * @param tagId 标签 ID
     * @param id    关联行 ID
     * @param req   新工号（EMP 行）或新机构编号（ORG 行）
     * @return 空响应
     */
    @PutMapping("/{tagId}/members/{id}")
    @Operation(summary = "修改标签成员（更换工号 / 机构编号）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> updateMember(@PathVariable("tagId") Long tagId,
                                              @PathVariable("id") Long id,
                                              @Valid @RequestBody PersonTagMemberUpdateReqDTO req) {
        String operator = DataScopeContext.current().getEmpId();
        log.info("[AdminPersonTagController.updateMember] operator={}, tagId={}, relId={}", operator, tagId, id);
        personTagService.updateMember(tagId, id, req.getUsername(), req.getOrgDeptNo(), operator);
        return ResponseWrapper.success();
    }

    /**
     * 删除单个成员关联。
     *
     * @param tagId 标签 ID
     * @param id    关联行 ID
     * @return 空响应
     */
    @DeleteMapping("/{tagId}/members/{id}")
    @Operation(summary = "删除标签成员")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.DELETE)
    public ResponseWrapper<Void> removeMember(@PathVariable("tagId") Long tagId,
                                              @PathVariable("id") Long id) {
        log.info("[AdminPersonTagController.removeMember] operator={}, tagId={}, relId={}",
                DataScopeContext.current().getEmpId(), tagId, id);
        personTagService.removeMember(tagId, id);
        return ResponseWrapper.success();
    }

    /**
     * 全局导入（按维度：员工=标签名称/工号，机构=标签名称/机构号；缺标签自动新建，同步原子）。
     *
     * @param file .xlsx 文件
     * @param dim  成员维度（EMP/ORG，默认 EMP）
     * @return 导入结果（成功计数或行级错误明细）
     */
    @PostMapping("/import")
    @Operation(summary = "全局导入业务标签-成员关联（Excel，按维度，缺标签自动新建，同步原子）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.IMPORT)
    public ResponseWrapper<PersonTagImportResultDTO> importGlobal(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "dim", defaultValue = "EMP") String dim) {
        String operator = DataScopeContext.current().getEmpId();
        log.info("[AdminPersonTagController.importGlobal] operator={}, dim={}, fileName={}",
                operator, dim, file != null ? file.getOriginalFilename() : null);
        return ResponseWrapper.success(personTagImportService.importGlobal(file, dim, operator));
    }

    /**
     * 下载全局导入模板（按维度：员工=标签名称/工号，机构=标签名称/机构名称）。
     *
     * @param dim      成员维度（EMP/ORG，默认 EMP）
     * @param response HTTP 响应（附件输出）
     */
    @GetMapping("/import-template")
    @Operation(summary = "下载业务标签全局导入模板（按维度）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.IMPORT)
    public void importTemplate(@RequestParam(value = "dim", defaultValue = "EMP") String dim,
                               HttpServletResponse response) throws IOException {
        if (isOrgDim(dim)) {
            PersonTagOrgImportRow sample = new PersonTagOrgImportRow();
            sample.setTagName("重点机构");
            sample.setOrgName("城东支行");
            writeTemplate(response, "业务标签机构导入模板.xlsx", PersonTagOrgImportRow.class, List.of(sample));
        } else {
            PersonTagImportRow sample = new PersonTagImportRow();
            sample.setTagName("重点培养");
            sample.setUsername("100001");
            writeTemplate(response, "业务标签员工导入模板.xlsx", PersonTagImportRow.class, List.of(sample));
        }
    }

    /**
     * 成员导入（按维度对该标签全量覆盖，不影响另一维度，同步原子）。
     *
     * @param tagId 标签 ID
     * @param file  .xlsx 文件
     * @param dim   成员维度（EMP/ORG，默认 EMP）
     * @return 导入结果（成功计数或行级错误明细）
     */
    @PostMapping("/{tagId}/import")
    @Operation(summary = "导入标签成员（Excel，按维度全量覆盖，同步原子）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.IMPORT)
    public ResponseWrapper<PersonTagImportResultDTO> importMembers(
            @PathVariable("tagId") Long tagId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "dim", defaultValue = "EMP") String dim) {
        String operator = DataScopeContext.current().getEmpId();
        log.info("[AdminPersonTagController.importMembers] operator={}, tagId={}, dim={}, fileName={}",
                operator, tagId, dim, file != null ? file.getOriginalFilename() : null);
        return ResponseWrapper.success(personTagImportService.importMembers(tagId, file, dim, operator));
    }

    /**
     * 下载成员导入模板（按维度：员工=工号，机构=机构名称）。
     *
     * @param dim      成员维度（EMP/ORG，默认 EMP）
     * @param response HTTP 响应（附件输出）
     */
    @GetMapping("/member-import-template")
    @Operation(summary = "下载标签成员导入模板（按维度）")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.IMPORT)
    public void memberImportTemplate(@RequestParam(value = "dim", defaultValue = "EMP") String dim,
                                     HttpServletResponse response) throws IOException {
        if (isOrgDim(dim)) {
            PersonTagOrgMemberImportRow sample = new PersonTagOrgMemberImportRow();
            sample.setOrgName("城东支行");
            writeTemplate(response, "标签机构成员导入模板.xlsx", PersonTagOrgMemberImportRow.class, List.of(sample));
        } else {
            PersonTagMemberImportRow sample = new PersonTagMemberImportRow();
            sample.setUsername("100001");
            writeTemplate(response, "标签员工成员导入模板.xlsx", PersonTagMemberImportRow.class, List.of(sample));
        }
    }

    /** 维度是否为机构（ORG，忽略大小写）。 */
    private static boolean isOrgDim(String dim) {
        return "ORG".equalsIgnoreCase(dim);
    }

    /** 输出 Excel 模板附件（UTF-8 文件名）。 */
    private static <T> void writeTemplate(HttpServletResponse response, String fileName,
                                          Class<T> head, List<T> sampleRows) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        EasyExcel.write(response.getOutputStream(), head).sheet("导入模板").doWrite(sampleRows);
    }
}
