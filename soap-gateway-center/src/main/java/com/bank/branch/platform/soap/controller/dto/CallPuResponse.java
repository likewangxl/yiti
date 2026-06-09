package com.bank.branch.platform.soap.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 手机端 / callpu 渠道的统一响应信封。
 * <p>字段名对齐手机端 list.vue 的读取约定：{@code response.ReturnCd} 与 {@code response.RspMsg}。
 * 成功时 {@code RspMsg} 为业务对象（如 {@link PerfListData}），失败时 {@code RspMsg} 为错误文案字符串。</p>
 *
 * <p>注：本响应是"业务载荷"，后续 SOAP/callpu 信封若需再包一层，由网关 SOAP 层处理。</p>
 */
@Data
@AllArgsConstructor
public class CallPuResponse {

    /** 返回码，"0"=成功，其余=失败（手机端以 {@code == "0"} 判断）。 */
    @JsonProperty("ReturnCd")
    private String returnCd;

    /** 成功=业务对象；失败=错误文案字符串。 */
    @JsonProperty("RspMsg")
    private Object rspMsg;

    /** 成功响应。 */
    public static CallPuResponse ok(Object rspMsg) {
        return new CallPuResponse("0", rspMsg);
    }

    /** 失败响应（携带错误文案，手机端 toast 展示）。 */
    public static CallPuResponse fail(String message) {
        return new CallPuResponse("99", message);
    }
}
