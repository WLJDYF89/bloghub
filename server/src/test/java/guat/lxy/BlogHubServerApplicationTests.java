package guat.lxy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 脚手架自带的冒烟测试。
 *
 * 原来这里打印的是 MD5("123456")（e10adc39...），那是旧密码方案的产物；
 * 现在密码统一走 BCrypt，这个测试改成校验 BCrypt 的基本行为。
 * 真正的密码逻辑测试见 service/PasswordServiceTest。
 *
 * 注意：这里刻意不加 @SpringBootTest —— 起完整容器需要 MySQL 与 Redis 都可用，
 * 只为了验证一个哈希算法不值得。
 */
class BlogHubServerApplicationTests {

    @Test
    void bcryptRoundTrip() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String hash = encoder.encode("123456");

        // BCrypt 哈希形如 $2a$10$...，固定 60 字符，能放进 user.password varchar(128)
        assert hash.length() == 60 : "BCrypt 哈希长度应为 60，实际 " + hash.length();
        assert hash.startsWith("$2") : "BCrypt 哈希应以 $2 开头，实际 " + hash;
        assert encoder.matches("123456", hash);
        assert !encoder.matches("123457", hash);
    }
}
