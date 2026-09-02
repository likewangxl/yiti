package com.bank.branch.platform.it;

import com.bank.branch.platform.customer.api.ClaimApi;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.LeadApi;
import com.bank.branch.platform.customer.api.TagApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import com.bank.branch.platform.customer.api.dto.LeadDTO;
import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import com.bank.branch.platform.customer.dto.resp.TouchWorklogVO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.service.ClaimService;
import com.bank.branch.platform.customer.service.LeadService;
import com.bank.branch.platform.customer.service.TagCustomerService;
import com.bank.branch.platform.customer.service.TagService;
import com.bank.branch.platform.customer.service.TouchLogService;
import com.bank.branch.platform.customer.service.TouchTaskService;
import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * customer-marketing-center 在 bootstrap 下的最小集成闭环验证。
 * <p>
 * 覆盖 customer 侧线要求的 4 个能力：
 * 1. 标签创建 + 标签客户导入
 * 2. 线索草稿创建 + 对外 LeadApi 查询
 * 3. 客户认领 + 对外 ClaimApi / CustomerQueryApi 查询
 * 4. 认领后手动发起触达任务 + 触达日志 + 完成任务
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
@Sql(
        scripts = {
                "/customer-marketing-schema.sql",
                "/customer-marketing-data.sql"
        }
)
class CustomerMarketingCenterIT {

    private static final String OPERATOR_EMP_ID = "user001";
    private static final String OPERATOR_ORG_ID = "BJ_CY";
    private static final String CUSTOMER_ID = "1001";

    @Autowired
    private TagService tagService;

    @Autowired
    private TagCustomerService tagCustomerService;

    @Autowired
    private LeadService leadService;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private TouchLogService touchLogService;

    @Autowired
    private TouchTaskService touchTaskService;

    @Autowired
    private TagApi tagApi;

    @Autowired
    private LeadApi leadApi;

    @Autowired
    private CustomerQueryApi customerQueryApi;

    @Autowired
    private ClaimApi claimApi;

    @Autowired
    private TouchTaskQueryApi touchTaskQueryApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("customer 集成 - 标签/线索/认领/触达最小闭环可运行")
    void customerMinimalClosure_worksInBootstrap() {
        CustTag tag = tagService.createTag(
                "Phase1 客群标签",
                "roadmap phase1 customer gate",
                "PHASE1",
                10,
                OPERATOR_EMP_ID
        );

        tagCustomerService.importCustomers(tag.getId(), List.of(CUSTOMER_ID), OPERATOR_EMP_ID);

        CustLead lead = leadService.createDraft(
                "Phase1 测试客户",
                "91310000TESTPHASE1",
                "张三",
                "13800000001",
                "IT",
                "GROUP",
                "ENTERPRISE",
                1,
                "PRIVATE",
                "Phase1 集团",
                1,
                "customer bootstrap integration",
                new BigDecimal("1000000.00"),
                new BigDecimal("600000.00"),
                "MANUAL",
                "[\"" + tag.getId() + "\"]",
                "phase1",
                OPERATOR_EMP_ID,
                OPERATOR_ORG_ID
        );

        CustClaim claim = claimService.claim(CUSTOMER_ID, OPERATOR_ORG_ID, OPERATOR_EMP_ID);

        // 认领只建立客户关系，首次触达需由已认领员工手动发起。
        claimService.startTouch(claim.getId(), null, OPERATOR_EMP_ID, OPERATOR_ORG_ID);

        String touchTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM MARKETING_TOUCH_TASK WHERE cust_id = ? ORDER BY created_time DESC LIMIT 1",
                String.class,
                CUSTOMER_ID
        );

        TouchWorklogVO log = touchLogService.addLog(
                touchTaskId,
                "client-phase1-001",
                "首次触达已完成，客户愿意继续沟通",
                "[\"photo-1.png\"]",
                null,
                "ONSITE",
                "[]",
                null,
                "{\"address\":\"客户经营场所\"}",
                OPERATOR_EMP_ID,
                OPERATOR_ORG_ID,
                false
        );

        touchTaskService.markSuccess(touchTaskId, OPERATOR_EMP_ID, false);

        assertThat(tagApi.isTagNameExists("Phase1 客群标签")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_TAG_REL WHERE tag_id = ? AND cust_id = ?",
                Long.class,
                tag.getId(),
                CUSTOMER_ID
        )).isEqualTo(1L);

        // LeadApi 已迁移至 Optional<LeadDTO>，不再直接返回 CustLead 实体
        LeadDTO leadDTO = leadApi.getLead(lead.getId()).orElseThrow();
        assertThat(leadDTO.getId()).isEqualTo(lead.getId());

        CustClaimDTO claimDTO = claimApi.getClaim(CUSTOMER_ID, OPERATOR_ORG_ID).orElseThrow();
        assertThat(claimDTO.getId()).isEqualTo(claim.getId());
        assertThat(customerQueryApi.isValidCustomer(CUSTOMER_ID)).isTrue();
        assertThat(customerQueryApi.isClaimedByOrg(CUSTOMER_ID, OPERATOR_ORG_ID)).isTrue();

        assertThat(log.getTouchTaskId()).isEqualTo(touchTaskId);
        assertThat(log.getWorkLogId()).isNotBlank();
        TouchTaskDTO taskDto = touchTaskQueryApi.getTouchTask(touchTaskId).orElseThrow();
        assertThat(taskDto.getTaskStatus()).isEqualTo("SUCCESS");
        assertThat(taskDto.getWorklogId()).isEqualTo(log.getWorkLogId());
        // getSlaStatus 已从契约删除；slaWarning=false 表示 SLA 正常（GREEN）
        assertThat(taskDto.getSlaWarning()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MARKETING_TOUCH_WORKLOG WHERE id = ?",
                Long.class,
                log.getWorkLogId()
        )).isEqualTo(1L);
    }
}
