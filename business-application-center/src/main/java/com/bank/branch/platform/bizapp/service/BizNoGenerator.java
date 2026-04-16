package com.bank.branch.platform.bizapp.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 业务编号生成器。
 * <p>
 * 生成唯一的业务编号，格式为前缀 + yyyyMMdd + 6位随机数字。
 * </p>
 */
@Service
public class BizNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    /**
     * 生成贷款申请编号。
     * 格式：LA + yyyyMMdd + 6位零填充随机数，例如 LA20260414123456
     *
     * @return 贷款申请编号
     */
    public String generateLoanNo() {
        return "LA" + LocalDate.now().format(DATE_FMT) + generateSixDigits();
    }

    /**
     * 生成支持请求编号。
     * 格式：SR + yyyyMMdd + 6位零填充随机数，例如 SR20260414123456
     *
     * @return 支持请求编号
     */
    public String generateSupportNo() {
        return "SR" + LocalDate.now().format(DATE_FMT) + generateSixDigits();
    }

    /**
     * 生成6位零填充随机数字字符串（000000~999999）。
     *
     * @return 6位数字字符串
     */
    private String generateSixDigits() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
