package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeSearchDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;

/**
 * AddrbookEmployee Entity -> DTO 转换器
 *
 * <p>纯静态方法，无业务逻辑。mobile 字段进行脱敏处理（138****8888）。
 * positionDesc 留 null，由 Service 层通过 DictApi 填充。</p>
 */
public final class EmployeeConverter {

    private EmployeeConverter() {}

    /**
     * Entity -> EmployeeDTO（跨模块 API 返回）
     *
     * <p>mobile 字段自动脱敏：保留前 3 位和后 4 位，中间用 **** 代替。
     * positionDesc 留 null，由 Service 层填充。</p>
     *
     * @param entity 员工实体
     * @return EmployeeDTO，entity 为 null 时返回 null
     */
    public static EmployeeDTO toDTO(AddrbookEmployee entity) {
        if (entity == null) return null;
        return EmployeeDTO.builder()
                .empId(entity.getEmpId())
                .empName(entity.getEmpName())
                .mobile(maskMobile(entity.getMobile()))
                .email(entity.getEmail())
                .orgCode(entity.getOrgCode())
                .orgName(entity.getOrgName())
                .position(entity.getPosition())
                .positionDesc(null) // 需要 DictApi 翻译，Service 层填充
                .selfDesc(entity.getSelfDesc())
                .responsibleProductIds(entity.getResponsibleProductIds())
                .status(entity.getStatus())
                .updatedTime(entity.getUpdatedTime())
                .build();
    }

    /**
     * Entity -> EmployeeSearchDTO（模糊搜索结果）
     *
     * @param entity 员工实体
     * @return 搜索结果 DTO，entity 为 null 时返回 null
     */
    public static EmployeeSearchDTO toSearchDTO(AddrbookEmployee entity) {
        if (entity == null) return null;
        EmployeeSearchDTO dto = new EmployeeSearchDTO();
        dto.setEmpId(entity.getEmpId());
        dto.setEmpName(entity.getEmpName());
        dto.setOrgCode(entity.getOrgCode());
        dto.setOrgName(entity.getOrgName());
        dto.setPosition(entity.getPosition());
        return dto;
    }

    /**
     * 手机号脱敏：保留前 3 位和后 4 位，中间 4 位用 * 替代
     *
     * <p>示例：13812345678 -> 138****5678</p>
     *
     * @param mobile 原始手机号
     * @return 脱敏后的手机号，null 或长度不足时原样返回
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
