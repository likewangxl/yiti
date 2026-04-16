package com.bank.branch.platform.performance.exception;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.performance.enums.PerfErrorCode;

/**
 * performance 模块统一业务异常.
 * <p>继承 common-web 的 {@link BizException}, 由 GlobalExceptionHandler 捕获转 ResponseWrapper.
 * <p>用法: throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
 */
public class PerfException extends BizException {

    private final PerfErrorCode errorCode;

    public PerfException(PerfErrorCode errorCode, Object... args) {
        super(errorCode.getCode(), errorCode.format(args));
        this.errorCode = errorCode;
    }

    public PerfException(PerfErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getCode(), errorCode.format(args), cause);
        this.errorCode = errorCode;
    }

    public PerfErrorCode getErrorCode() {
        return errorCode;
    }
}
