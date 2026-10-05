package guat.lxy.service;

import guat.lxy.mapper.UserMapper;
import guat.lxy.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 密码哈希的唯一出入口：注册、改密、登录校验都走这里。
 *
 * 存储格式当前是 BCrypt（形如 $2a$10$...，60 字符，自带随机盐）。
 *
 * ── 关于历史遗留的无盐 MD5 ──────────────────────────────────────────────
 * 库里早期账号存的是 32 位十六进制无盐 MD5，无法反推成 BCrypt
 * （同一密码每次 BCrypt 结果都不同，且 MD5 不可逆），
 * 所以采用「登录时一次性升级」：
 *   1. 先用 BCrypt 验；不匹配再按 MD5 验一次（兼容老的库记录）
 *   2. MD5 验通过说明密码是对的，顺手把这条记录改写成 BCrypt
 *   3. 之后再登录就只走 BCrypt 分支，老格式逐步自然消亡
 *
 * 这个策略不需要用户改密码，也不需要停机，是本地口令迁移的标准做法。
 * 代价：迁移完成前，老记录的哈希强度仍是不安全的 MD5 —— 但只在
 * 「该用户下次登录之前」这个窗口内存在，且升级成功后立即消失。
 */
@Service
@Slf4j
public class PasswordService {

    /** 无盐 MD5 哈希：32 位小写十六进制 */
    private static final Pattern LEGACY_MD5 = Pattern.compile("^[0-9a-fA-F]{32}$");

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public PasswordService(PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /** 加密明文密码，用于注册 / 重置密码 / 新建账号。 */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            // 参数校验在调用方做，这里只是兜底，避免存进一个空密码的哈希
            throw new IllegalArgumentException("密码不能为空");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 校验登录密码；如果库里的哈希还是老的 MD5，就顺手升级成 BCrypt。
     *
     * @param user          已按用户名查出的用户（不能为 null）
     * @param rawPassword   用户提交的明文密码
     * @return 密码是否正确
     */
    public boolean verifyAndUpgradeIfLegacy(User user, String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        String stored = user.getPassword();
        if (stored == null || stored.isEmpty()) {
            log.error("账号 {} 的 password 字段为空，拒绝登录", user.getUserName());
            return false;
        }

        // 1. 先按当前算法（BCrypt）验
        if (isBcrypt(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }

        // 2. 不是 BCrypt，再按历史 MD5 验一次
        if (isLegacyMd5(stored)) {
            String md5 = md5Hex(rawPassword);
            // 用 equalsIgnoreCase：历史记录大小写不一定统一
            if (stored.equalsIgnoreCase(md5)) {
                upgradeToBcrypt(user, rawPassword, stored);
                return true;
            }
            return false;
        }

        // 3. 两种格式都不是：数据脏了或列被手工改过，明确报出来，不要静默当作密码错误
        log.error("账号 {} 的 password 既不是 BCrypt 也不是 MD5，格式无法识别（长度={}）",
                user.getUserName(), stored.length());
        return false;
    }

    /** 判断某个哈希是否已经是 BCrypt 格式（$2a$ / $2b$ / $2y$ 开头）。 */
    public boolean isBcrypt(String hash) {
        return hash != null && hash.startsWith("$2");
    }

    /** 判断某个哈希是否是历史遗留的无盐 MD5。 */
    public boolean isLegacyMd5(String hash) {
        return hash != null && LEGACY_MD5.matcher(hash).matches();
    }

    /**
     * 把一条老的 MD5 记录改写成 BCrypt。
     *
     * 这里【故意吞掉异常、不让登录失败】：密码已经验过了，用户是有权登录的；
     * 升级只是顺带的优化，写库失败（数据库抖动 / 权限问题）不该把人挡在门外，
     * 记一条 warn 下次登录再试即可。
     */
    private void upgradeToBcrypt(User user, String rawPassword, String oldHash) {
        try {
            userMapper.resetPassword(user.getUserName(), passwordEncoder.encode(rawPassword));
            log.info("账号 {} 的密码已从 MD5 升级为 BCrypt", user.getUserName());
        } catch (RuntimeException e) {
            log.warn("账号 {} 的密码升级 BCrypt 失败，本次仍按 MD5 校验通过，下次登录会重试。err={}",
                    user.getUserName(), e.getMessage());
        }
    }

    /** 历史 MD5 算法：与旧版本 UserServiceImpl 的实现保持一致，仅用于校验老记录。 */
    @SuppressWarnings("deprecation")
    private String md5Hex(String rawPassword) {
        return DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
    }
}
