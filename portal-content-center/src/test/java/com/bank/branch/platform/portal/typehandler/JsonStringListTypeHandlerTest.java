package com.bank.branch.platform.portal.typehandler;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class JsonStringListTypeHandlerTest {

    private final JsonStringListTypeHandler handler = new JsonStringListTypeHandler();

    @Test
    void shouldSerializeNullToNull() {
        assertThat(handler.serialize(null)).isNull();
    }

    @Test
    void shouldSerializeEmptyListToEmptyJsonArray() {
        assertThat(handler.serialize(Collections.emptyList())).isEqualTo("[]");
    }

    @Test
    void shouldSerializeListToJsonArray() {
        assertThat(handler.serialize(Arrays.asList("E001", "E002")))
            .isEqualTo("[\"E001\",\"E002\"]");
    }

    @Test
    void shouldDeserializeNullToEmptyList() {
        assertThat(handler.deserialize(null)).isEmpty();
    }

    @Test
    void shouldDeserializeEmptyStringToEmptyList() {
        assertThat(handler.deserialize("")).isEmpty();
    }

    @Test
    void shouldDeserializeEmptyJsonArrayToEmptyList() {
        assertThat(handler.deserialize("[]")).isEmpty();
    }

    @Test
    void shouldDeserializeJsonArrayToList() {
        assertThat(handler.deserialize("[\"E001\",\"E002\"]"))
            .containsExactly("E001", "E002");
    }
}
