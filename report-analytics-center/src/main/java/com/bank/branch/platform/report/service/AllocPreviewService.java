package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.report.controller.dto.AllocPreviewRespDTO;
import com.bank.branch.platform.report.controller.dto.AllocPreviewRespDTO.AllocItem;
import com.bank.branch.platform.report.controller.dto.AllocPreviewRespDTO.BalanceSummary;
import com.bank.branch.platform.report.entity.*;
import com.bank.branch.platform.report.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 业绩调整分配预览服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocPreviewService {

    private final DlCorpAssetLiabAllotMapper corpAllotMapper;
    private final DlCorpDepositAcctMapper corpDepositMapper;
    private final DlCorpLoanAcctMapper corpLoanMapper;
    private final DlIndivDepositAcctMapper indivDepositMapper;
    private final UserApi userApi;

    /**
     * 查询分配预览数据
     */
    public AllocPreviewRespDTO preview(String custType, String custNo, String allocDim, String accountNo, LocalDate statisDt) {
        LocalDate dt = statisDt != null ? statisDt : LocalDate.now().minusDays(1);
        AllocPreviewRespDTO resp = new AllocPreviewRespDTO();

        // 收集关联的账号和借据号，用于查原业绩分配
        Set<String> acctNos = new LinkedHashSet<>();
        Set<String> iouNos = new LinkedHashSet<>();

        if ("RULE".equals(allocDim)) {
            if ("CORP".equals(custType)) {
                List<DlCorpDepositAcct> deposits = queryCorpDepositByCust(custNo, dt);
                resp.setDepositSummary(sumRmb(deposits));
                deposits.forEach(r -> { if (r.getAcctNo() != null) acctNos.add(r.getAcctNo()); });

                List<DlCorpLoanAcct> loans = queryCorpLoanByCust(custNo, dt);
                resp.setLoanSummary(sumLoanRmb(loans));
                loans.forEach(r -> { if (r.getIouNo() != null) iouNos.add(r.getIouNo()); });
            } else {
                List<DlIndivDepositAcct> deposits = queryIndivDepositByCust(custNo, dt);
                resp.setDepositSummary(sumIndivRmb(deposits));
                deposits.forEach(r -> { if (r.getAcctNo() != null) acctNos.add(r.getAcctNo()); });
            }
        } else {
            if ("CORP".equals(custType)) {
                List<DlCorpDepositAcct> deposits = queryCorpDepositByAcct(accountNo, dt);
                resp.setDepositSummary(sumRmb(deposits));
                deposits.forEach(r -> { if (r.getAcctNo() != null) acctNos.add(r.getAcctNo()); });

                List<DlCorpLoanAcct> loans = queryCorpLoanByAcct(accountNo, dt);
                resp.setLoanSummary(sumLoanRmb(loans));
                loans.forEach(r -> { if (r.getIouNo() != null) iouNos.add(r.getIouNo()); });
            } else {
                List<DlIndivDepositAcct> deposits = queryIndivDepositByAcct(accountNo, dt);
                resp.setDepositSummary(sumIndivRmb(deposits));
                deposits.forEach(r -> { if (r.getAcctNo() != null) acctNos.add(r.getAcctNo()); });
            }
        }

        // 原业绩分配：用 acctNos + iouNos 关联 CORP_ASSET_LIAB_ALLOT
        resp.setAllocList(queryAllocItems(acctNos, iouNos));

        // 判断是否有数据
        boolean depositHas = resp.getDepositSummary() != null && resp.getDepositSummary().getCurrBalRmb().compareTo(BigDecimal.ZERO) != 0;
        boolean loanHas = resp.getLoanSummary() != null && resp.getLoanSummary().getCurrBalRmb().compareTo(BigDecimal.ZERO) != 0;
        resp.setHasData(depositHas || loanHas);

        return resp;
    }

    // ==================== 查询原始数据 ====================

    private List<DlCorpDepositAcct> queryCorpDepositByCust(String custId, LocalDate dt) {
        return corpDepositMapper.selectList(new LambdaQueryWrapper<DlCorpDepositAcct>()
                .eq(DlCorpDepositAcct::getCustId, custId).eq(DlCorpDepositAcct::getStatisDt, dt));
    }

    private List<DlCorpLoanAcct> queryCorpLoanByCust(String custId, LocalDate dt) {
        return corpLoanMapper.selectList(new LambdaQueryWrapper<DlCorpLoanAcct>()
                .eq(DlCorpLoanAcct::getCustId, custId).eq(DlCorpLoanAcct::getStatisDt, dt));
    }

    private List<DlIndivDepositAcct> queryIndivDepositByCust(String custId, LocalDate dt) {
        return indivDepositMapper.selectList(new LambdaQueryWrapper<DlIndivDepositAcct>()
                .eq(DlIndivDepositAcct::getCustId, custId).eq(DlIndivDepositAcct::getStatisDt, dt));
    }

    private List<DlCorpDepositAcct> queryCorpDepositByAcct(String acctNo, LocalDate dt) {
        return corpDepositMapper.selectList(new LambdaQueryWrapper<DlCorpDepositAcct>()
                .eq(DlCorpDepositAcct::getAcctNo, acctNo).eq(DlCorpDepositAcct::getStatisDt, dt));
    }

    private List<DlCorpLoanAcct> queryCorpLoanByAcct(String acctNo, LocalDate dt) {
        return corpLoanMapper.selectList(new LambdaQueryWrapper<DlCorpLoanAcct>()
                .eq(DlCorpLoanAcct::getAcctNo, acctNo).eq(DlCorpLoanAcct::getStatisDt, dt));
    }

    private List<DlIndivDepositAcct> queryIndivDepositByAcct(String acctNo, LocalDate dt) {
        return indivDepositMapper.selectList(new LambdaQueryWrapper<DlIndivDepositAcct>()
                .eq(DlIndivDepositAcct::getAcctNo, acctNo).eq(DlIndivDepositAcct::getStatisDt, dt));
    }

    // ==================== 余额汇总（只用人民币字段） ====================

    private BalanceSummary sumRmb(List<DlCorpDepositAcct> rows) {
        BalanceSummary s = new BalanceSummary();
        for (DlCorpDepositAcct r : rows) {
            s.setCurrBalRmb(s.getCurrBalRmb().add(safe(r.getCurrBalRmb())));
            s.setCurrMAvgBalRmb(s.getCurrMAvgBalRmb().add(safe(r.getCurrMAvgBalRmb())));
            s.setCurrYAvgBalRmb(s.getCurrYAvgBalRmb().add(safe(r.getCurrYAvgBalRmb())));
        }
        return s;
    }

    private BalanceSummary sumLoanRmb(List<DlCorpLoanAcct> rows) {
        BalanceSummary s = new BalanceSummary();
        for (DlCorpLoanAcct r : rows) {
            s.setCurrBalRmb(s.getCurrBalRmb().add(safe(r.getCurrBalRmb())));
            s.setCurrMAvgBalRmb(s.getCurrMAvgBalRmb().add(safe(r.getCurrMAvgBalRmb())));
            s.setCurrYAvgBalRmb(s.getCurrYAvgBalRmb().add(safe(r.getCurrYAvgBalRmb())));
        }
        return s;
    }

    private BalanceSummary sumIndivRmb(List<DlIndivDepositAcct> rows) {
        BalanceSummary s = new BalanceSummary();
        for (DlIndivDepositAcct r : rows) {
            s.setCurrBalRmb(s.getCurrBalRmb().add(safe(r.getCurrBalRmb())));
            s.setCurrMAvgBalRmb(s.getCurrMAvgBalRmb().add(safe(r.getCurrMAvgBalRmb())));
            s.setCurrYAvgBalRmb(s.getCurrYAvgBalRmb().add(safe(r.getCurrYAvgBalRmb())));
        }
        return s;
    }

    // ==================== 原业绩分配 ====================

    /**
     * 用账号(Acct_No)和借据号(IOU_No)关联CORP_ASSET_LIAB_ALLOT，
     * 取出分配比例和员工号，再关联系统用户表取姓名和机构。
     */
    private List<AllocItem> queryAllocItems(Set<String> acctNos, Set<String> iouNos) {
        if (acctNos.isEmpty() && iouNos.isEmpty()) return new ArrayList<>();

        // 用 Acct_No 关联
        List<DlCorpAssetLiabAllot> allots = new ArrayList<>();
        if (!acctNos.isEmpty()) {
            allots.addAll(corpAllotMapper.selectList(new LambdaQueryWrapper<DlCorpAssetLiabAllot>()
                    .in(DlCorpAssetLiabAllot::getAcctNo, acctNos)));
        }
        // 用 IOU_No 关联
        if (!iouNos.isEmpty()) {
            allots.addAll(corpAllotMapper.selectList(new LambdaQueryWrapper<DlCorpAssetLiabAllot>()
                    .in(DlCorpAssetLiabAllot::getIouNo, iouNos)));
        }

        // 去重（同一条分配记录可能同时被 Acct_No 和 IOU_No 命中）
        Map<String, DlCorpAssetLiabAllot> unique = new LinkedHashMap<>();
        for (DlCorpAssetLiabAllot a : allots) {
            String key = a.getAcctNo() + "|" + a.getAllocaterId() + "|" + a.getIouNo();
            unique.putIfAbsent(key, a);
        }

        // 批量查员工信息（Allocater_Id 对应 PT_USER.USERNAME）
        Set<String> usernames = unique.values().stream()
                .map(DlCorpAssetLiabAllot::getAllocaterId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        Map<String, String> empNameMap = new HashMap<>();
        Map<String, String> empOrgMap = new HashMap<>();
        if (!usernames.isEmpty()) {
            List<UserDTO> users = userApi.getUsersByUsernames(new ArrayList<>(usernames));
            if (users != null) {
                for (UserDTO u : users) {
                    empNameMap.put(u.getUsername(), u.getDisplayName());
                    empOrgMap.put(u.getUsername(), u.getMainOrgName());
                }
            }
        }

        return unique.values().stream().map(a -> {
            AllocItem item = new AllocItem();
            item.setAcctNo(a.getAcctNo());
            item.setDynScale(a.getDynScale() != null ? a.getDynScale().toPlainString() : null);
            item.setAllocaterId(a.getAllocaterId());
            item.setEmpName(empNameMap.getOrDefault(a.getAllocaterId(), a.getAllocaterId()));
            item.setOrgName(empOrgMap.getOrDefault(a.getAllocaterId(), ""));
            return item;
        }).collect(Collectors.toList());
    }

    private static BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
