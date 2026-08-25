package com.bank.branch.platform.customer.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 新增触达日志请求 DTO
 */
@Data
@Schema(description = "新增触达日志请求")
public class TouchLogReqDTO {

    /**
     * 客户端幂等键（由移动端生成，防重复提交），必填
     */
    @NotBlank(message = "客户端幂等键不能为空")
    @Schema(description = "客户端幂等键（UUID），用于防重复提交", required = true)
    private String clientUuid;

    /**
     * 触达内容描述，必填
     */
    @NotBlank(message = "触达内容不能为空")
    @Schema(description = "触达内容文字描述", required = true)
    private String logContent;

    /** 实际触达时间。 */
    @NotNull(message = "触达时间不能为空")
    @Schema(description = "实际触达时间", required = true)
    private LocalDateTime touchTime;

    /** 触达方式：VISIT/PHONE/WECHAT/OTHER。 */
    @NotBlank(message = "触达方式不能为空")
    @Schema(description = "触达方式", required = true)
    private String touchMethod;

    @Schema(description = "协同人员工工号列表")
    private List<String> participantEmpIds;

    /**
     * 照片 URL 列表（MinIO 上传后的 URL），最多 9 张，可为空
     */
    @Schema(description = "照片 URL 列表（MinIO URL），最多 9 张")
    private List<String> photoUrls;

    @Valid
    @NotNull(message = "分类照片不能为空")
    @Schema(description = "分类照片，每类最多3张，至少上传1张", required = true)
    private PhotoGroups photoGroups;

    @Schema(description = "办理定位，建议为经纬度和地址的JSON字符串")
    private String operatorLocation;

    /** 分类照片请求。 */
    @Data
    public static class PhotoGroups {
        @Size(max = 3, message = "关键人合影最多3张")
        private List<String> keyPerson = new ArrayList<>();

        @Size(max = 3, message = "企业门牌最多3张")
        private List<String> doorplate = new ArrayList<>();

        @Size(max = 3, message = "经营场所最多3张")
        private List<String> workplace = new ArrayList<>();

        @AssertTrue(message = "至少上传一张触达照片")
        @JsonIgnore
        public boolean isAnyPhotoPresent() {
            return size(keyPerson) + size(doorplate) + size(workplace) > 0;
        }

        @JsonIgnore
        public List<String> allPhotos() {
            List<String> all = new ArrayList<>();
            if (keyPerson != null) all.addAll(keyPerson);
            if (doorplate != null) all.addAll(doorplate);
            if (workplace != null) all.addAll(workplace);
            return all;
        }

        private int size(List<String> values) {
            return values == null ? 0 : values.size();
        }
    }
}
