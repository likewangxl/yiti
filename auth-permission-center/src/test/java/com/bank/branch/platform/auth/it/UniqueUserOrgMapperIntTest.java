package com.bank.branch.platform.auth.it;

import com.bank.branch.platform.auth.api.dto.UniqueUserOrgDTO;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** H2 定向测试：验证全表唯一归属判断、授权机构过滤以及参数绑定。 */
@SpringBootTest(classes = {
        DataSourceAutoConfiguration.class,
        SqlInitializationAutoConfiguration.class,
        com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
        AuthTestConfig.class
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UniqueUserOrgMapperIntTest {

    @Autowired
    private UserOrgMapper userOrgMapper;

    @Autowired
    private DataSource dataSource;

    @Test
    void selectUniqueUserOrgs_excludesUsersWithMultipleOrgAssignments() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES (?, ?)",
                "multi-org-user", "BJ_CY");
        jdbcTemplate.update("INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES (?, ?)",
                "multi-org-user", "SH_PD");

        List<UniqueUserOrgDTO> rows = userOrgMapper.selectUniqueUserOrgs(List.of("BJ_CY", "SH_PD"));

        assertThat(rows).extracting(UniqueUserOrgDTO::getEmpId, UniqueUserOrgDTO::getOrgCode)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("user001", "BJ_CY"),
                        org.assertj.core.groups.Tuple.tuple("user002", "SH_PD"));
        assertThat(rows).noneMatch(row -> "multi-org-user".equals(row.getEmpId()));
    }

    @Test
    void selectUniqueUserOrgs_bindsAuthorizedOrgCodesAsParameters() {
        List<UniqueUserOrgDTO> rows = userOrgMapper.selectUniqueUserOrgs(
                List.of("BJ_CY' OR '1'='1"));

        assertThat(rows).isEmpty();
    }
}
