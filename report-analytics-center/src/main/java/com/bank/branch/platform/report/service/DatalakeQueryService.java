package com.bank.branch.platform.report.service;

import com.bank.branch.platform.report.entity.*;
import com.bank.branch.platform.report.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据湖表查询服务（只读）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatalakeQueryService {

    private final DlIndivDepositAcctMapper indivDepositMapper;
    private final DlCorpAssetLiabAllotMapper corpAssetLiabAllotMapper;
    private final DlIndivAllotRelaMapper indivAllotRelaMapper;
    private final DlCorpDepositAcctMapper corpDepositMapper;
    private final DlCorpLoanAcctMapper corpLoanMapper;

    /** 个人存款账户 — 按统计日期+机构分页查询 */
    public IPage<DlIndivDepositAcct> pageIndivDeposit(int pageNo, int pageSize, String statisDt, String acctOrgId) {
        LambdaQueryWrapper<DlIndivDepositAcct> qw = new LambdaQueryWrapper<DlIndivDepositAcct>()
                .eq(statisDt != null, DlIndivDepositAcct::getStatisDt, statisDt)
                .eq(acctOrgId != null, DlIndivDepositAcct::getAcctOrgId, acctOrgId);
        return indivDepositMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    /** 对公资产负债分配 — 按统计日期+客户分页查询 */
    public IPage<DlCorpAssetLiabAllot> pageCorpAssetLiabAllot(int pageNo, int pageSize, String statisDt, String custId) {
        LambdaQueryWrapper<DlCorpAssetLiabAllot> qw = new LambdaQueryWrapper<DlCorpAssetLiabAllot>()
                .eq(statisDt != null, DlCorpAssetLiabAllot::getStatisDt, statisDt)
                .eq(custId != null, DlCorpAssetLiabAllot::getCustId, custId);
        return corpAssetLiabAllotMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    /** 个人分配关系 — 按交易日期+分配人分页查询 */
    public IPage<DlIndivAllotRela> pageIndivAllotRela(int pageNo, int pageSize, String txDate, String crmAllocaterId) {
        LambdaQueryWrapper<DlIndivAllotRela> qw = new LambdaQueryWrapper<DlIndivAllotRela>()
                .eq(txDate != null, DlIndivAllotRela::getTxDate, txDate)
                .eq(crmAllocaterId != null, DlIndivAllotRela::getCrmAllocaterId, crmAllocaterId);
        return indivAllotRelaMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    /** 对公存款账户 — 按统计日期+机构分页查询 */
    public IPage<DlCorpDepositAcct> pageCorpDeposit(int pageNo, int pageSize, String statisDt, String acctOrgId) {
        LambdaQueryWrapper<DlCorpDepositAcct> qw = new LambdaQueryWrapper<DlCorpDepositAcct>()
                .eq(statisDt != null, DlCorpDepositAcct::getStatisDt, statisDt)
                .eq(acctOrgId != null, DlCorpDepositAcct::getAcctOrgId, acctOrgId);
        return corpDepositMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    /** 对公贷款账户 — 按统计日期+机构分页查询 */
    public IPage<DlCorpLoanAcct> pageCorpLoan(int pageNo, int pageSize, String statisDt, String acctOrgId) {
        LambdaQueryWrapper<DlCorpLoanAcct> qw = new LambdaQueryWrapper<DlCorpLoanAcct>()
                .eq(statisDt != null, DlCorpLoanAcct::getStatisDt, statisDt)
                .eq(acctOrgId != null, DlCorpLoanAcct::getAcctOrgId, acctOrgId);
        return corpLoanMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    /** 按账号查个人存款账户 */
    public List<DlIndivDepositAcct> listIndivDepositByAcctNo(String acctNo) {
        return indivDepositMapper.selectList(new LambdaQueryWrapper<DlIndivDepositAcct>()
                .eq(DlIndivDepositAcct::getAcctNo, acctNo));
    }

    /** 按账号查对公存款账户 */
    public List<DlCorpDepositAcct> listCorpDepositByAcctNo(String acctNo) {
        return corpDepositMapper.selectList(new LambdaQueryWrapper<DlCorpDepositAcct>()
                .eq(DlCorpDepositAcct::getAcctNo, acctNo));
    }

    /** 按账号查对公贷款账户 */
    public List<DlCorpLoanAcct> listCorpLoanByAcctNo(String acctNo) {
        return corpLoanMapper.selectList(new LambdaQueryWrapper<DlCorpLoanAcct>()
                .eq(DlCorpLoanAcct::getAcctNo, acctNo));
    }

    /** 按客户ID查对公资产负债分配 */
    public List<DlCorpAssetLiabAllot> listCorpAssetLiabAllotByCustId(String custId) {
        return corpAssetLiabAllotMapper.selectList(new LambdaQueryWrapper<DlCorpAssetLiabAllot>()
                .eq(DlCorpAssetLiabAllot::getCustId, custId));
    }

    /** 按分配人查个人分配关系 */
    public List<DlIndivAllotRela> listIndivAllotRelaByAllocater(String crmAllocaterId) {
        return indivAllotRelaMapper.selectList(new LambdaQueryWrapper<DlIndivAllotRela>()
                .eq(DlIndivAllotRela::getCrmAllocaterId, crmAllocaterId));
    }
}
