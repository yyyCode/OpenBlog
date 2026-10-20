package com.yqz.openblog.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** YAML 的 openblog.sensitive-word.import.* 必须真的绑定到 importConfig。 */
class SensitiveWordPropertiesTest {

    @Test
    void importSectionBinds() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("openblog.sensitive-word.import.max-file-size", "1MB")
                .withProperty("openblog.sensitive-word.import.max-rows", "1000");

        SensitiveWordProperties properties = Binder.get(env)
                .bind("openblog.sensitive-word", Bindable.of(SensitiveWordProperties.class))
                .get();

        assertEquals(DataSize.ofMegabytes(1), properties.getImport().getMaxFileSize());
        assertEquals(1000, properties.getImport().getMaxRows());
    }
}
