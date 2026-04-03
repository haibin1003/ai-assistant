package com.ai.assistant.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EncryptionService 单元测试
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        ReflectionTestUtils.setField(encryptionService, "encryptionKey", "test-encryption-key-32-characters");
    }

    @Test
    @DisplayName("加密和解密 - 成功")
    void encryptAndDecrypt_success() {
        String plainText = "my-secret-api-key";

        String encrypted = encryptionService.encrypt(plainText);
        String decrypted = encryptionService.decrypt(encrypted);

        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);
        assertEquals(plainText, decrypted);
    }

    @Test
    @DisplayName("加密 - 空字符串")
    void encrypt_emptyString() {
        String result = encryptionService.encrypt("");

        assertEquals("", result);
    }

    @Test
    @DisplayName("加密 - null")
    void encrypt_null() {
        String result = encryptionService.encrypt(null);

        assertNull(result);
    }

    @Test
    @DisplayName("解密 - 空字符串")
    void decrypt_emptyString() {
        String result = encryptionService.decrypt("");

        assertEquals("", result);
    }

    @Test
    @DisplayName("解密 - null")
    void decrypt_null() {
        String result = encryptionService.decrypt(null);

        assertNull(result);
    }

    @Test
    @DisplayName("加密结果不可预测（每次加密结果不同）")
    void encrypt_differentResultsForSameInput() {
        String plainText = "same-input";

        String encrypted1 = encryptionService.encrypt(plainText);
        String encrypted2 = encryptionService.encrypt(plainText);

        // 由于使用随机IV，相同输入的加密结果应该不同
        assertNotEquals(encrypted1, encrypted2);

        // 但都能正确解密
        assertEquals(plainText, encryptionService.decrypt(encrypted1));
        assertEquals(plainText, encryptionService.decrypt(encrypted2));
    }

    @Test
    @DisplayName("加密中文内容")
    void encrypt_chineseContent() {
        String plainText = "这是一个中文测试";

        String encrypted = encryptionService.encrypt(plainText);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plainText, decrypted);
    }

    @Test
    @DisplayName("加密长文本")
    void encrypt_longText() {
        String plainText = "a".repeat(10000);

        String encrypted = encryptionService.encrypt(plainText);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plainText, decrypted);
    }
}
