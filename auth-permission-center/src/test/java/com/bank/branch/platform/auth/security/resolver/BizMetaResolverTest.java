package com.bank.branch.platform.auth.security.resolver;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BizMetaResolverTest {

    private final BizMetaResolver resolver = new BizMetaResolver();

    @Test
    void resolve_withBizAuthAnnotation_returnsBizMeta() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(),
            TestController.class.getMethod("annotatedMethod"));

        Optional<BizMeta> result = resolver.resolve(handlerMethod);

        assertThat(result).isPresent();
        assertThat(result.get().bizType()).isEqualTo(BizType.CUSTOMER);
        assertThat(result.get().action()).isEqualTo(BizAction.READ);
    }

    @Test
    void resolve_withWriteAction_returnsBizMeta() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(),
            TestController.class.getMethod("writeMethod"));

        Optional<BizMeta> result = resolver.resolve(handlerMethod);

        assertThat(result).isPresent();
        assertThat(result.get().bizType()).isEqualTo(BizType.LEAD);
        assertThat(result.get().action()).isEqualTo(BizAction.WRITE);
    }

    @Test
    void resolve_withoutAnnotation_returnsEmpty() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(),
            TestController.class.getMethod("unannotatedMethod"));

        Optional<BizMeta> result = resolver.resolve(handlerMethod);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_notHandlerMethod_returnsEmpty() {
        Optional<BizMeta> result = resolver.resolve("not a handler");

        assertThat(result).isEmpty();
    }

    // ── 测试桩 ──────────────────────────────────────────────────

    static class TestController {
        @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.READ)
        public void annotatedMethod() {}

        @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
        public void writeMethod() {}

        public void unannotatedMethod() {}
    }
}
