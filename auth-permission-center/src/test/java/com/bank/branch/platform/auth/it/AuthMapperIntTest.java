package com.bank.branch.platform.auth.it;

import com.bank.branch.platform.auth.entity.*;
import com.bank.branch.platform.auth.mapper.*;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L3 集成测试 - Auth 模块
 * 显式拼装 ApplicationContext：DataSource + SqlInit + MybatisPlus + AuthTestConfig
 * （MP 替换 mybatis-spring-boot-starter 后，@MybatisTest 不再可用，改为最小切片）
 * schema.sql / data.sql 由 application.yml 中 spring.sql.init.schema-locations 加载
 */
@SpringBootTest(classes = {
    DataSourceAutoConfiguration.class,
    SqlInitializationAutoConfiguration.class,
    MybatisPlusAutoConfiguration.class,
    AuthTestConfig.class
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class AuthMapperIntTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleResourceMapper roleResourceMapper;

    @Autowired
    private RoleBizScopeMapper bizScopeMapper;

    @Autowired
    private OrgMapper orgMapper;

    @Autowired
    private UserOrgMapper userOrgMapper;

    @Test
    @DisplayName("用户查询: 根据 userId 查询用户信息")
    void selectByUserId_exists() {
        PtUser user = userMapper.selectByUserId("admin");
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("用户查询: 根据 username 查询用于登录")
    void selectByUsername_exists() {
        PtUser user = userMapper.selectByUsername("user001");
        assertThat(user).isNotNull();
        assertThat(user.getUserchnname()).isEqualTo("张三");
    }

    @Test
    @DisplayName("用户查询: 不存在用户返回 null")
    void selectByUserId_notFound() {
        PtUser user = userMapper.selectByUserId("nonexistent");
        assertThat(user).isNull();
    }

    @Test
    @DisplayName("角色查询: 分页查询返回所有角色")
    void selectAllRoles() {
        List<PtRole> roles = roleMapper.selectByPage(null, null, 0, 100);
        assertThat(roles).hasSize(4);
    }

    @Test
    @DisplayName("角色查询: 根据角色ID查询角色信息")
    void selectByRoleId() {
        PtRole role = roleMapper.selectByRoleId("R001");
        assertThat(role).isNotNull();
        assertThat(role.getRoleCode()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("角色查询: 根据角色编码查询")
    void selectByRoleCode() {
        PtRole role = roleMapper.selectByRoleCode("CUST_MGR");
        assertThat(role).isNotNull();
        assertThat(role.getRoleId()).isEqualTo("R002");
    }

    @Test
    @DisplayName("资源查询: 根据角色ID查询该角色的所有资源")
    void selectResourcesByRoleId() {
        List<PtResource> resources = resourceMapper.selectByRoleId("R001");
        assertThat(resources).hasSize(7);
    }

    @Test
    @DisplayName("角色资源关联: 查询角色的资源ID列表")
    void selectResourceIdsByRoleId() {
        List<String> resourceIds = roleResourceMapper.selectResourceIdsByRoleId("R001");
        assertThat(resourceIds).hasSize(7);
    }

    @Test
    @DisplayName("角色资源关联: 判断授权关系是否存在")
    void existsByRoleIdAndResourceId() {
        assertThat(roleResourceMapper.existsByRoleIdAndResourceId("R001", "1")).isTrue();
        assertThat(roleResourceMapper.existsByRoleIdAndResourceId("R001", "999")).isFalse();
    }

    @Test
    @DisplayName("用户角色关联: 查询用户的角色列表")
    void selectRolesByUserId() {
        List<PtRole> roles = userRoleMapper.selectRolesByUserId("admin");
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).getRoleId()).isEqualTo("R001");
    }

    @Test
    @DisplayName("用户角色关联: 查询用户的角色ID列表")
    void selectRoleIdsByUserId() {
        List<String> roleIds = userRoleMapper.selectRoleIdsByUserId("admin");
        assertThat(roleIds).containsExactly("R001");
    }

    @Test
    @DisplayName("用户角色关联: 统计指定角色的用户数")
    void countByRoleId() {
        long count = userRoleMapper.countByRoleId("R001");
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("业务范围: 查询角色的业务数据范围")
    void selectBizScopeByRoleId() {
        List<PtRoleBizScope> scopes = bizScopeMapper.selectByRoleId("R001");
        assertThat(scopes).hasSize(2);
    }

    @Test
    @DisplayName("业务范围: 全量查询用于缓存加载")
    void selectAllBizScopes() {
        List<PtRoleBizScope> scopes = bizScopeMapper.selectAll();
        assertThat(scopes).hasSize(6);
    }

    @Test
    @DisplayName("机构查询: 查询所有机构")
    void selectAllOrgs() {
        List<ExtOrgInfo> orgs = orgMapper.selectAll();
        assertThat(orgs).hasSize(5);
    }

    @Test
    @DisplayName("机构查询: 根据 orgCode 查询")
    void selectByOrgCode() {
        ExtOrgInfo org = orgMapper.selectByOrgCode("BJ_CY");
        assertThat(org).isNotNull();
        assertThat(org.getOrgName()).isEqualTo("北京分行朝阳支行");
    }

    @Test
    @DisplayName("机构查询: 懒加载子机构")
    void selectChildren() {
        List<ExtOrgInfo> children = orgMapper.selectChildren("HQ");
        assertThat(children).hasSize(2);
    }

    @Test
    @DisplayName("用户机构关联: 查询用户所属机构")
    void selectUserOrgByUserId() {
        ExtUserOrg userOrg = userOrgMapper.selectByUserId("user001");
        assertThat(userOrg).isNotNull();
        assertThat(userOrg.getOrgCode()).isEqualTo("BJ_CY");
    }
}

/**
 * 测试专用配置 - 提供 Mapper 扫描入口，满足 @MybatisTest 的配置要求。
 */
@org.mybatis.spring.annotation.MapperScan("com.bank.branch.platform.auth.mapper")
class AuthTestConfig {
}
