package com.bank.branch.platform.common.db;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Properties;

/**
 * 审计字段自动填充拦截器
 * INSERT 填充 createdBy / createdTime
 * UPDATE 填充 updatedBy / updatedTime
 */
@Slf4j
@Intercepts({
    @Signature(type = Executor.class, method = "update",
        args = {MappedStatement.class, Object.class})
})
public class AuditFieldFiller implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        if (parameter == null) {
            return invocation.proceed();
        }

        String empId = getCurrentEmpId();
        LocalDateTime now = LocalDateTime.now();

        if (ms.getSqlCommandType() == SqlCommandType.INSERT) {
            setFieldIfExists(parameter, "createdBy", empId);
            setFieldIfExists(parameter, "createdTime", now);
            setFieldIfExists(parameter, "updatedBy", empId);
            setFieldIfExists(parameter, "updatedTime", now);
        } else if (ms.getSqlCommandType() == SqlCommandType.UPDATE) {
            setFieldIfExists(parameter, "updatedBy", empId);
            setFieldIfExists(parameter, "updatedTime", now);
        }

        return invocation.proceed();
    }

    /**
     * 获取当前登录用户的员工ID
     *
     * @return 员工ID，未登录时返回 null
     */
    private String getCurrentEmpId() {
        DataScopeContext ctx = DataScopeContext.current();
        return ctx != null ? ctx.getEmpId() : null;
    }

    /**
     * 反射设置对象的指定字段值（仅在字段当前值为 null 时设置）
     *
     * @param obj       目标对象
     * @param fieldName 字段名
     * @param value     要设置的值
     */
    private void setFieldIfExists(Object obj, String fieldName, Object value) {
        try {
            Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                if (field.get(obj) == null) {
                    field.set(obj, value);
                }
            }
        } catch (Exception e) {
            log.trace("无法设置审计字段 {}: {}", fieldName, e.getMessage());
        }
    }

    /**
     * 在类继承链中查找指定名称的字段
     *
     * @param clazz 起始类
     * @param name  字段名
     * @return 找到的字段，未找到返回 null
     */
    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {}
}
