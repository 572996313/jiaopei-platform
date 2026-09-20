package com.jiaopei.platform.common.security;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 可逆对称加密工具(AES-GCM)。密文自带 {@code enc:v1:} 前缀与随机 IV,
 * 再次加密时识别前缀直接返回原值(幂等),解密时自动识别前缀,明文直接原样返回。
 *
 * <p>密钥来源:配置 {@code jiaopei.security.data-key}(环境变量
 * {@code DATA_ENCRYPT_KEY} 覆盖,生产必须配置),经 SHA-256 派生为 32 字节
 * AES-256 密钥,任意长度的配置串均可使用。</p>
 */
@Component
public class SensitiveCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final String dataKey;
    private SecretKeySpec secretKey;

    public SensitiveCipher(@Value("${jiaopei.security.data-key:}") String dataKey) {
        this.dataKey = dataKey;
    }

    @PostConstruct
    void init() {
        if (dataKey == null || dataKey.isBlank()) {
            throw new IllegalStateException("jiaopei.security.data-key 未配置,敏感数据加密不可用");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(dataKey.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化敏感加密密钥失败", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 加密,幂等:已加密的密文直接返回。 */
    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        if (isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("敏感数据加密失败", e);
        }
    }

    /** 解密:明文或未知格式直接原样返回。 */
    public String decrypt(String value) {
        if (value == null || !isEncrypted(value)) {
            return value;
        }
        try {
            byte[] all = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            System.arraycopy(all, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感数据解密失败", e);
        }
    }
}