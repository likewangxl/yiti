package com.bank.branch.platform.yundun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/** 违规台账 Excel 导入表单。 */
@Data
public class ViolationImportReq {
    @NotNull(message = "请选择要导入的 Excel 文件")
    private MultipartFile file;
    @NotBlank(message = "导入原因不能为空")
    @Size(max = 500, message = "导入原因不能超过 500 个字符")
    private String reason;
}
