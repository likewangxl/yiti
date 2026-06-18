package com.bank.branch.platform.portal.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.addrbook.AddrbookImportRow;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通讯录导入服务。
 *
 * <p>同步、<strong>原子（全或无）</strong>：全部行校验通过才入库；任一行有误则整批不写，
 * 抛出带行号 + 原因的错误（如「第 20 行：工号不存在」）。</p>
 *
 * <p>校验口径：
 * <ul>
 *   <li>工号必填，且必须存在于 PT_USER 并处于<strong>启用</strong>状态（ISENABLED=0）；</li>
 *   <li>机构名称必填，按名称反查 EXT_ORG_INFO：0 命中=不存在，&gt;1 命中=重名歧义；</li>
 *   <li>姓名以 PT_USER 为准（模板姓名列仅供核对）。</li>
 * </ul>
 * 入库语义：按工号 upsert（存在更新、不存在新建）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddrbookImportService {

    /** 单次导入最大行数保护。 */
    private static final int MAX_IMPORT_ROWS = 5000;

    private final AddrbookEmployeeMapper addrbookEmployeeMapper;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final CurrentUserApi currentUserApi;

    /**
     * 解析 + 校验 + 入库。
     *
     * @param file 上传的 Excel
     * @return 成功导入（upsert）的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(PortalErrorCode.PARAM_INVALID.getCode(), "导入文件为空");
        }
        List<AddrbookImportRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(AddrbookImportRow.class)
                    .sheet()
                    .doReadSync();
        } catch (IOException e) {
            throw new BizException(PortalErrorCode.PARAM_INVALID.getCode(), "导入文件解析失败：" + e.getMessage());
        }
        if (rows == null || rows.isEmpty()) {
            throw new BizException(PortalErrorCode.PARAM_INVALID.getCode(), "导入文件无数据行");
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new BizException(PortalErrorCode.PARAM_INVALID.getCode(),
                    "导入行数 " + rows.size() + " 超过上限 " + MAX_IMPORT_ROWS);
        }

        // 批量取数：工号 → PT_USER（姓名 + 启用状态）；机构名称 → EXT_ORG_INFO（编码 + 重名判定）
        List<String> empIds = rows.stream().map(r -> trim(r.getEmpId()))
                .filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());
        List<String> orgNames = rows.stream().map(r -> trim(r.getOrgName()))
                .filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());

        Map<String, UserDTO> userByEmpId = empIds.isEmpty() ? Map.of()
                : userApi.getUsersByUsernames(empIds).stream()
                    .filter(u -> u.getUsername() != null)
                    .collect(Collectors.toMap(UserDTO::getUsername, u -> u, (a, b) -> a));
        // 机构名称 → 命中列表（用于重名判定）
        Map<String, List<OrgDTO>> orgsByName = orgNames.isEmpty() ? Map.of()
                : orgApi.getOrgsByNames(orgNames).stream()
                    .collect(Collectors.groupingBy(OrgDTO::getOrgName));

        // 逐行校验，收集所有错误（全或无）
        List<String> errors = new ArrayList<>();
        List<AddrbookEmployee> toUpsert = new ArrayList<>(rows.size());
        String operator = currentUserApi.getCurrentEmpId();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < rows.size(); i++) {
            AddrbookImportRow r = rows.get(i);
            int lineNo = i + 2; // 第 1 行为表头，数据从第 2 行起
            String empId = trim(r.getEmpId());
            String orgName = trim(r.getOrgName());

            if (empId.isEmpty()) { errors.add("第 " + lineNo + " 行：工号必填"); continue; }
            if (orgName.isEmpty()) { errors.add("第 " + lineNo + " 行：机构名称必填"); continue; }

            UserDTO user = userByEmpId.get(empId);
            if (user == null) { errors.add("第 " + lineNo + " 行：工号「" + empId + "」在系统用户中不存在"); continue; }
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                errors.add("第 " + lineNo + " 行：工号「" + empId + "」已禁用，不可导入"); continue;
            }

            List<OrgDTO> orgs = orgsByName.get(orgName);
            if (orgs == null || orgs.isEmpty()) { errors.add("第 " + lineNo + " 行：机构「" + orgName + "」不存在"); continue; }
            if (orgs.size() > 1) { errors.add("第 " + lineNo + " 行：机构名称「" + orgName + "」重复，请使用唯一机构"); continue; }
            OrgDTO org = orgs.get(0);

            AddrbookEmployee e = new AddrbookEmployee();
            e.setEmpId(empId);
            e.setEmpName(user.getDisplayName()); // 姓名以 PT_USER 为准
            e.setOrgCode(org.getOrgCode());
            e.setOrgName(org.getOrgName());
            e.setPosition(trimOrNull(r.getPosition()));
            e.setMobile(trimOrNull(r.getMobile()));
            e.setEmail(trimOrNull(r.getEmail()));
            e.setSelfDesc(trimOrNull(r.getSelfDesc()));
            e.setStatus("ACTIVE");
            e.setMaintainerEmpId(operator);
            e.setUpdatedTime(now);
            toUpsert.add(e);
        }

        if (!errors.isEmpty()) {
            // 全或无：任一行有误，整批不入库，返回明细（最多前 20 条，避免过长）
            String detail = errors.stream().limit(20).collect(Collectors.joining("；"));
            if (errors.size() > 20) {
                detail += "；…共 " + errors.size() + " 处错误";
            }
            throw new BizException(PortalErrorCode.PARAM_INVALID.getCode(), "导入失败：" + detail);
        }

        // 全部通过 → upsert
        for (AddrbookEmployee e : toUpsert) {
            AddrbookEmployee exist = addrbookEmployeeMapper.selectByEmpId(e.getEmpId());
            if (exist == null) {
                e.setCreatedTime(now);
                e.setDeleted(0);
                addrbookEmployeeMapper.insert(e);
            } else {
                e.setCreatedTime(exist.getCreatedTime());
                e.setDeleted(exist.getDeleted() == null ? 0 : exist.getDeleted());
                addrbookEmployeeMapper.updateById(e);
            }
        }
        log.info("[AddrbookImport] 导入成功 {} 行, operator={}", toUpsert.size(), operator);
        return toUpsert.size();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimOrNull(String s) {
        String t = trim(s);
        return t.isEmpty() ? null : t;
    }
}
