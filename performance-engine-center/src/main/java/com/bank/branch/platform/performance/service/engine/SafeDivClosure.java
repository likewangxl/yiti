package com.bank.branch.platform.performance.service.engine;

import groovy.lang.Closure;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Groovy 安全除法闭包：{@code div(a, b)}.
 *
 * <p>除数为 0 或 null → 返回 {@link BigDecimal#ZERO}；否则以固定 scale=10、HALF_UP 相除
 * （固定 scale 同时规避无限小数如 1/3 不指定 scale 抛 ArithmeticException）。
 */
public final class SafeDivClosure extends Closure<BigDecimal> {

    /** 内部除法精度. */
    private static final int SCALE = 10;

    public SafeDivClosure(Object owner) {
        super(owner);
    }

    /** div(a, b)：b 为 0/null → 0；否则 a/b（scale=10, HALF_UP）. */
    public BigDecimal doCall(Object a, Object b) {
        BigDecimal divisor = toBigDecimal(b);
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal dividend = toBigDecimal(a);
        if (dividend == null) {
            return BigDecimal.ZERO;
        }
        return dividend.divide(divisor, SCALE, RoundingMode.HALF_UP);
    }

    /** 任意数值对象转 BigDecimal；null 原样返回 null. */
    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(v.toString());
    }
}
