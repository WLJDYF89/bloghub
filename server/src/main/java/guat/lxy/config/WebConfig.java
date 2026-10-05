package guat.lxy.config;

import guat.lxy.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册统一鉴权拦截器
 *
 * 拦截所有请求，只放行真正不需要登录的公开接口：
 * - POST /user/login            登录
 * - POST /user/register         注册
 * - POST /user/reset-password   找回密码
 * - GET  /article/list          公开文章分页列表
 * （GET /article/{纯数字id} 详情在 JwtInterceptor 内部用「方法+纯数字路径」放行，
 *   避免 /article/{id} 模式误伤 /article/my；它是「可选登录」——
 *   认得出身份就写入 BaseContext，认不出按游客处理，草稿的可见性由
 *   ArticleServiceImpl#getArticleById 判定）
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/reset-password",
                        "/article/list",
                        "/category/public"
                );
    }
}
