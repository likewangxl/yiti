package com.bank.branch.platform.report.exception;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.enums.RptErrorCode;

/**
 * report-analytics-center 模块统一业务异常（Task M1.1.1 新增）.
 *
 * <p>继承 common-web 的 {@link BizException}，由 {@code GlobalExceptionHandler}
 * 捕获转换为 {@code ResponseWrapper} 响应。
 *
 * <p>用法：
 * <pre>{@code
 * throw new RptException(RptErrorCode.METRIC_DIM_MISMATCH);
 * throw new RptException(RptErrorCode.SAVED_QUERY_NOT_FOUND, id);
 * }</pre>
 *
 * <p>对照 performance-engine-center 的 {@code PerfException}，保持跨模块一致风格.
 */
public class RptException extends BizException {

    private final RptErrorCode errorCode;

    public RptException(RptErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public RptException(RptErrorCode errorCode, Throwable cause) {
        super(errorCode.getCode(), errorCode.getMsg(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 保持错误码稳定，同时返回经服务端排序、可供前端直接展示的安全补充信息。
     * 仅用于不含敏感数据的冲突上下文（例如已发布引用屏编码）。
     */
    public RptException(RptErrorCode errorCode, String message) {
        super(errorCode.getCode(), message);
        this.errorCode = errorCode;
    }

    public RptErrorCode getErrorCode() {
        return errorCode;
    }
}
