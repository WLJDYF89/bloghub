package guat.lxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希器的唯一来源。
 *
 * 这里刻意用 @Bean 暴露 PasswordEncoder 接口，而不是各处 new BCryptPasswordEncoder()：
 * 将来要换算法（Argon2 / PBKDF2）或调强度，只改这一处，
 * 登录时「老哈希自动升级」的逻辑也会跟着换，不会出现两套算法并存。
 *
 * strength 用默认强度 10（2^10 次迭代，单次约 50~100ms）：
 * 对登录接口是安全的量级，又不会明显拖慢注册。
 * 注意：BCrypt 输出固定 60 字符，user.password 列是 varchar(128)，够用。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
