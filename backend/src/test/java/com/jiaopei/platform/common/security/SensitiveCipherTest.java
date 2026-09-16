package com.jiaopei.platform.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SensitiveCipherTest {

    private final SensitiveCipher cipher = new SensitiveCipher("test-key");

    @Test
    void encryptDecryptRoundTrip() {
        cipher.init();
        String plain = "13812341234";
        String encrypted = cipher.encrypt(plain);

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(cipher.isEncrypted(encrypted)).isTrue();
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void encryptIsIdempotent() {
        cipher.init();
        String encrypted = cipher.encrypt("13812341234");
        assertThat(cipher.encrypt(encrypted)).isEqualTo(encrypted);
    }

    @Test
    void decryptPlainReturnsAsIs() {
        cipher.init();
        assertThat(cipher.decrypt("not-encrypted")).isEqualTo("not-encrypted");
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void missingKeyThrows() {
        SensitiveCipher broken = new SensitiveCipher("");
        org.assertj.core.api.Assertions.assertThatThrownBy(broken::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("data-key");
    }
}