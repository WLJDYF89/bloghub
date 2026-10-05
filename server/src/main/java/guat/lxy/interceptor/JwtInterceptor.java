package guat.lxy.interceptor;

import guat.lxy.common.context.BaseContext;
import guat.lxy.service.TokenService;
import guat.lxy.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 统一鉴权拦截器：所有需要登录的接口在这里一次性完成
 * 「清洗 + 签名/过期校验 + Redis 黑名单校验」，Service 层不再重复收 token 校验。
 *
 * 校验通过后把 userId / userName / 清洗后的 token 写入 BaseContext（ThreadLocal），
 * Controller / Service 直接从 BaseContext 取当前用户身份。
 *
 * 校验失败（未登录 / 过期 / 已登出）抛出的都是 BaseException 子类，
 * 会被 GlobalExceptionHandler 统一转成 Result{code:0, msg} 返回给前端。
 *
 * 例外：公开的文章详情是「可选登录」，token 认不出来时降级成游客而不是拒绝。
 */
@Component
public class JwtInterceptor implements AsyncHandlerInterceptor {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 公开的文章详情（GET /article/{纯数字id}）放行。
        // 不能用 excludePathPatterns("/article/{id}")，因为它会连 GET /article/my 一起放行，
        // 所以这里用「方法 + 纯数字路径」精确匹配。
        //
        // 这一条是「可选登录」：带了合法 token 就把身份写进 BaseContext
        // （作者要能打开自己的草稿），没带或 token 已经失效就当游客放行。
        // 这里不能像别的接口那样一拒了之 —— 它读的通常是已发布的文章，
        // 不该因为浏览器里躺着一个过期 token 就把游客挡在门外。
        if (isPublicArticleDetail(request)) {
            if (hasToken(request)) {
                try {
                    bindIdentity(request);
                } catch (RuntimeException ignored) {
                    // 认不出来就是游客，身份清干净，交给 Service 按游客处理
                    BaseContext.clearAll();
                }
            }
            return true;
        }

        try {
            bindIdentity(request);
            return true;
        } catch (RuntimeException e) {
            // preHandle 抛异常时 afterCompletion 不会执行（本拦截器 preHandle 未成功返回），
            // Tomcat 线程池会复用当前线程，必须手动清掉 ThreadLocal 防止身份串号
            BaseContext.clearAll();
            throw e;
        }
    }

    private boolean isPublicArticleDetail(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("^/article/\\d+$");
    }

    private boolean hasToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && !authorization.isBlank();
    }

    /**
     * 三合一校验（清洗 + 签名/过期 + 黑名单）并把身份写进 BaseContext，
     * 校验不过直接抛 BaseException 子类，由调用方决定是拒绝还是降级成游客。
     */
    private void bindIdentity(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        String userName = tokenService.validateAndThrow(authorization);

        // 校验已通过，再解析一次拿 userId（generateToken 写入的 claims：userId / userName）
        Claims claims = jwtUtils.parseToken(authorization);
        Object uid = claims.get("userId");
        if (uid instanceof Number) {
            BaseContext.setCurrentUserId(((Number) uid).longValue());
        }
        BaseContext.setCurrentUserName(userName);
        BaseContext.setCurrentToken(jwtUtils.cleanToken(authorization));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 正常请求结束时清理 ThreadLocal
        BaseContext.clearAll();
    }

    /**
     * 异步请求（AI 的 SSE 转发走这条）在 preHandle 之后就把 Tomcat 线程交还线程池了，
     * afterCompletion 却要等异步处理彻底结束才回调 —— 中间这段时间线程会被下一个请求复用，
     * 不清就会把当前用户身份带过去（串号）。这里补上交接时的清理。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        BaseContext.clearAll();
    }
}
