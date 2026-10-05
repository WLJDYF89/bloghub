package guat.lxy.service;

import guat.lxy.mapper.UserMapper;
import guat.lxy.pojo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PasswordService 单元测试：BCrypt 加密 + 历史 MD5 记录的一次性升级。
 *
 * 纯 Mockito 单测，不起 Spring 容器，也就不需要 MySQL / Redis。
 */
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final String RAW_PASSWORD = "123456";

    /** 与旧版本实现一致的 MD5 值，用来构造「历史遗留记录」 */
    private static final String LEGACY_MD5 = DigestUtils.md5DigestAsHex(
            RAW_PASSWORD.getBytes(StandardCharsets.UTF_8));

    @Mock
    private UserMapper userMapper;

    private PasswordEncoder passwordEncoder;
    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        passwordService = new PasswordService(passwordEncoder, userMapper);
    }

    // ==================== 加密 ====================

    @Test
    @DisplayName("encode：产出 BCrypt 格式，且同一密码两次加密结果不同（随机盐生效）")
    void encode_producesSaltedBcryptHash() {
        String first = passwordService.encode(RAW_PASSWORD);
        String second = passwordService.encode(RAW_PASSWORD);

        assertTrue(first.startsWith("$2"), "应该是 BCrypt 格式，实际=" + first);
        assertNotEquals(LEGACY_MD5, first, "不能退化成 MD5");
        assertNotEquals(first, second, "BCrypt 带随机盐，两次结果必须不同");
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, first));
    }

    @Test
    @DisplayName("encode：空密码直接拒绝，不产生任何哈希")
    void encode_rejectsBlankPassword() {
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode(null));
        assertThrows(IllegalArgumentException.class, () -> passwordService.encode(""));
    }

    // ==================== 格式识别 ====================

    @Test
    @DisplayName("格式识别：正确区分 BCrypt 与历史 MD5")
    void formatDetection() {
        String bcrypt = passwordService.encode(RAW_PASSWORD);

        assertTrue(passwordService.isBcrypt(bcrypt));
        assertFalse(passwordService.isLegacyMd5(bcrypt));

        assertTrue(passwordService.isLegacyMd5(LEGACY_MD5));
        assertTrue(passwordService.isLegacyMd5(LEGACY_MD5.toUpperCase()), "大写 MD5 也算历史格式");
        assertFalse(passwordService.isBcrypt(LEGACY_MD5));

        assertFalse(passwordService.isBcrypt(null));
        assertFalse(passwordService.isLegacyMd5(null));
        assertFalse(passwordService.isLegacyMd5("我不是哈希"), "脏数据不该被认成 MD5");
    }

    // ==================== 校验：已经是 BCrypt ====================

    @Test
    @DisplayName("校验：BCrypt 记录密码正确 → true，且绝不回写数据库")
    void verify_bcryptRecord_correctPassword() {
        User user = userWithPassword(passwordService.encode(RAW_PASSWORD));

        assertTrue(passwordService.verifyAndUpgradeIfLegacy(user, RAW_PASSWORD));
        verify(userMapper, never()).resetPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("校验：BCrypt 记录密码错误 → false，不写库")
    void verify_bcryptRecord_wrongPassword() {
        User user = userWithPassword(passwordService.encode(RAW_PASSWORD));

        assertFalse(passwordService.verifyAndUpgradeIfLegacy(user, "wrong-password"));
        verify(userMapper, never()).resetPassword(anyString(), anyString());
    }

    // ==================== 校验：历史 MD5（一次性升级） ====================

    @Test
    @DisplayName("升级：MD5 记录密码正确 → 返回 true 并把该账号改写为 BCrypt")
    void verify_legacyMd5_correctPassword_upgradesToBcrypt() {
        User user = userWithPassword(LEGACY_MD5);
        when(userMapper.resetPassword(eq("alice"), anyString())).thenReturn(1);

        assertTrue(passwordService.verifyAndUpgradeIfLegacy(user, RAW_PASSWORD));

        // 关键断言：确实回写了，且写进去的是 BCrypt 而不是 MD5
        var hashCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(userMapper).resetPassword(eq("alice"), hashCaptor.capture());
        String written = hashCaptor.getValue();
        assertTrue(written.startsWith("$2"), "回写的必须是 BCrypt，实际=" + written);
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, written), "回写的哈希要能验证原密码");
    }

    @Test
    @DisplayName("升级：MD5 记录密码错误 → false，绝不改写密码")
    void verify_legacyMd5_wrongPassword_doesNotUpgrade() {
        User user = userWithPassword(LEGACY_MD5);

        assertFalse(passwordService.verifyAndUpgradeIfLegacy(user, "wrong-password"));
        verify(userMapper, never()).resetPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("升级：写库失败不能让已验密的用户登录失败（记 warn 后仍返回 true）")
    void verify_upgradeFailure_doesNotBlockLogin() {
        User user = userWithPassword(LEGACY_MD5);
        when(userMapper.resetPassword(eq("alice"), anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("数据库抖动"));

        assertTrue(passwordService.verifyAndUpgradeIfLegacy(user, RAW_PASSWORD),
                "升级失败只应记录日志，不应把用户挡在门外");
    }

    // ==================== 校验：脏数据 ====================

    @Test
    @DisplayName("校验：password 为空或格式无法识别 → false，且不写库")
    void verify_invalidStoredHash() {
        assertFalse(passwordService.verifyAndUpgradeIfLegacy(userWithPassword(null), RAW_PASSWORD));
        assertFalse(passwordService.verifyAndUpgradeIfLegacy(userWithPassword(""), RAW_PASSWORD));
        assertFalse(passwordService.verifyAndUpgradeIfLegacy(userWithPassword("乱码"), RAW_PASSWORD));
        verify(userMapper, never()).resetPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("校验：明文密码为 null → false")
    void verify_nullRawPassword() {
        assertFalse(passwordService.verifyAndUpgradeIfLegacy(userWithPassword(LEGACY_MD5), null));
        verify(userMapper, never()).resetPassword(anyString(), anyString());
    }

    private User userWithPassword(String storedPassword) {
        User user = new User();
        user.setUserId(1L);
        user.setUserName("alice");
        user.setPassword(storedPassword);
        return user;
    }
}
