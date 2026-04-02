package com.bank.branch.platform.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.slf4j.MDC;
import java.time.Instant;

/**
 * 统一响应包装器
 * 所有 API 接口统一返回此格式
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseWrapper<T> {
    private String code;
    private String message;
    private String traceId;
    private T data;
    private PageResult<?> page;
    private String timestamp;

    private ResponseWrapper() {
        this.timestamp = Instant.now().toString();
    }

    /**
     * 成功响应（携带数据）
     *
     * @param data 响应数据
     * @return 统一响应
     */
    public static <T> ResponseWrapper<T> success(T data) {
        ResponseWrapper<T> w = new ResponseWrapper<>();
        w.setCode("0");
        w.setMessage("success");
        w.setTraceId(MDC.get("traceId"));
        w.setData(data);
        return w;
    }

    /**
     * 成功响应（无数据）
     *
     * @return 统一响应
     */
    public static ResponseWrapper<Void> success() {
        return success(null);
    }

    /**
     * 分页响应
     *
     * @param pageResult 分页结果
     * @return 统一响应
     */
    public static <T> ResponseWrapper<T> page(PageResult<T> pageResult) {
        ResponseWrapper<T> w = new ResponseWrapper<>();
        w.setCode("0");
        w.setMessage("success");
        w.setTraceId(MDC.get("traceId"));
        w.setPage(pageResult);
        return w;
    }

    /**
     * 错误响应
     *
     * @param code    错误码
     * @param message 错误信息
     * @return 统一响应
     */
    public static ResponseWrapper<?> error(String code, String message) {
        ResponseWrapper<?> w = new ResponseWrapper<>();
        w.setCode(code);
        w.setMessage(message);
        w.setTraceId(MDC.get("traceId"));
        return w;
    }
}
