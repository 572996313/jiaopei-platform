package com.cornerstone.core.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataMaskingTest {

    @Test
    void masksPhone() {
        assertThat(DataMasking.phone("13812341234")).isEqualTo("138****1234");
    }

    @Test
    void masksIdCard() {
        assertThat(DataMasking.idCard("110101199003074512"))
            .isEqualTo("110***********4512");
    }

    @Test
    void masksBankCard() {
        assertThat(DataMasking.bankCard("6222021234567890123"))
            .isEqualTo("6222 *********** 0123");
    }

    @Test
    void masksPasswordFully() {
        assertThat(DataMasking.maskAll("SuperSecret123")).isEqualTo("******");
    }

    @Test
    void masksEmail() {
        assertThat(DataMasking.email("zhangsan@example.com"))
            .isEqualTo("z***@example.com");
    }

    @Test
    void masksNameKeepsSurName() {
        assertThat(DataMasking.name("张伟")).isEqualTo("张*");
    }

    @Test
    void masksAddressKeepsHeadSix() {
        assertThat(DataMasking.mask("北京市朝阳区建国路88号", SensitiveType.ADDRESS))
            .startsWith("北京市朝阳区")
            .contains("*");
    }

    @Test
    void masksOtherKeepsQuarter() {
        String masked = DataMasking.mask("ab12cd34", SensitiveType.OTHER);
        assertThat(masked).contains("*");
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(DataMasking.mask(null, SensitiveType.PHONE)).isNull();
        assertThat(DataMasking.mask("", SensitiveType.PHONE)).isEmpty();
    }
}