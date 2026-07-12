package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RPT_SCREEN 实体 —— 大屏定义.
 */
@Data
@TableName("RPT_SCREEN")
public class RptScreen {

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 大屏编码（deleted=0 内应用层唯一） */
    private String screenCode;

    /** 大屏名称 */
    private String screenName;

    /** 视角：PROVINCE / BRANCH / PERSON */
    private String viewLevel;

    /** 主题变量覆盖 JSON（一期留空） */
    private String themeJson;

    /** ACTIVE / DISABLED */
    private String status;

    /** 画布全局样式JSON(设计基准/背景/适配策略/主题覆盖,携带 schemaVersion) */
    private String canvasStyleJson;

    /** 编辑态组件树JSON(草稿,编辑器唯一读写对象) */
    private String canvasDraftJson;

    /** 发布态渲染包JSON=组件树+图表绑定快照,线上/预览只读它 */
    private String canvasPublishedJson;

    /** 真乐观锁版本号:保存 WHERE canvas_version=? 并自增,冲突返回 RPT-43012 */
    private Integer canvasVersion;

    /** 发布状态:0未发布/1已发布/2已发布但有未发布修改 */
    private Integer publishStatus;

    /** 最近一次发布时间 */
    private LocalDateTime publishedAt;

    /** 最近一次发布人工号 */
    private String publishedBy;

    /** 创建人工号 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;

    /** 逻辑删除 0否1是 */
    @TableLogic
    private Integer deleted;
}
