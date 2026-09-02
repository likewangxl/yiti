package com.bank.branch.platform.customer.dto;

import com.bank.branch.platform.customer.dto.req.LeadCreateReqDTO;
import com.bank.branch.platform.customer.dto.req.LeadUpdateReqDTO;
import com.bank.branch.platform.customer.dto.req.LeadEditVersionReqDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/** 线索 touchRestricted 请求契约红测。 */
class LeadTouchRestrictedContractTest {

    @Test
    void createRequest_shouldDefaultToRestrictedAndRequireValue() throws Exception {
        LeadCreateReqDTO request = new LeadCreateReqDTO();

        assertThat(request.getTouchRestricted()).isEqualTo(1);
        Field field = LeadCreateReqDTO.class.getDeclaredField("touchRestricted");
        NotNull notNull = field.getAnnotation(NotNull.class);
        assertThat(notNull).isNotNull();
        assertThat(notNull.message()).isEqualTo("是否触达限制不能为空");
        assertThat(field.getAnnotation(jakarta.validation.constraints.Min.class)).isNotNull();
        assertThat(field.getAnnotation(jakarta.validation.constraints.Max.class)).isNotNull();
    }

    @Test
    void updateRequest_shouldKeepTouchRestrictionOptional() throws Exception {
        Field field = LeadUpdateReqDTO.class.getDeclaredField("touchRestricted");

        assertThat(field.getType()).isEqualTo(Integer.class);
        assertThat(field.getAnnotation(NotNull.class)).isNull();
    }

    @Test
    void createUpdateAndEditVersion_shouldRejectValuesOutsideZeroOrOne() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        LeadCreateReqDTO create = new LeadCreateReqDTO();
        create.setCustName("测试企业");
        create.setUnifiedCreditCode("91110000123456789X");
        create.setTouchRestricted(2);
        assertThat(validator.validate(create)).extracting(v -> v.getPropertyPath().toString())
                .contains("touchRestricted");

        LeadUpdateReqDTO update = new LeadUpdateReqDTO();
        update.setTouchRestricted(-1);
        assertThat(validator.validate(update)).extracting(v -> v.getPropertyPath().toString())
                .contains("touchRestricted");

        LeadEditVersionReqDTO edit = new LeadEditVersionReqDTO();
        edit.setSourceCustId("cust-1");
        edit.setTouchRestricted(2);
        assertThat(validator.validate(edit)).extracting(v -> v.getPropertyPath().toString())
                .contains("touchRestricted");
    }
}
