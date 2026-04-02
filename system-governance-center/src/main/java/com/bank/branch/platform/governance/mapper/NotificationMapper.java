package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知 Mapper 接口，操作 user_notification 表。
 * <p>
 * 支持通知的插入、查询、已读状态更新等操作。
 * 分页查询支持按接收人工号和已读状态筛选。
 * </p>
 */
@Mapper
public interface NotificationMapper {

    /**
     * 插入通知记录。
     *
     * @param notification 通知实体
     * @return 受影响行数
     */
    int insert(UserNotification notification);

    /**
     * 根据主键查询通知。
     *
     * @param id 通知ID
     * @return 通知实体，不存在时返回 null
     */
    UserNotification selectById(String id);

    /**
     * 分页查询指定用户的通知列表。
     *
     * @param empId  接收人工号
     * @param isRead 是否已读（null=全部, 0=未读, 1=已读）
     * @param offset 偏移量
     * @param limit  每页大小
     * @return 通知列表，按 created_time DESC 排序
     */
    List<UserNotification> selectByEmpId(@Param("empId") String empId,
                                         @Param("isRead") Integer isRead,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /**
     * 统计指定用户的通知总数。
     *
     * @param empId  接收人工号
     * @param isRead 是否已读（null=全部, 0=未读, 1=已读）
     * @return 通知总数
     */
    long countByEmpId(@Param("empId") String empId,
                      @Param("isRead") Integer isRead);

    /**
     * 查询指定用户的未读通知数量。
     *
     * @param empId 接收人工号
     * @return 未读通知数量
     */
    int countUnread(String empId);

    /**
     * 更新通知的已读状态。
     *
     * @param id       通知ID
     * @param isRead   是否已读（1=已读, 0=未读）
     * @param readTime 阅读时间
     * @return 受影响行数
     */
    int updateReadStatus(@Param("id") String id,
                         @Param("isRead") int isRead,
                         @Param("readTime") LocalDateTime readTime);

    /**
     * 将指定用户的所有未读通知标记为已读。
     *
     * @param empId    接收人工号
     * @param readTime 阅读时间
     * @return 受影响行数（本次标记已读的通知条数）
     */
    int markAllAsRead(@Param("empId") String empId,
                      @Param("readTime") LocalDateTime readTime);
}
