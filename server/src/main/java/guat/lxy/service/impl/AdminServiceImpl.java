package guat.lxy.service.impl;

import guat.lxy.common.constant.MessageConstant;
import guat.lxy.common.constant.RoleIdConstant;
import guat.lxy.common.context.BaseContext;
import guat.lxy.common.exception.AccountNotFoundException;
import guat.lxy.common.exception.AdminPermissionDeniedException;
import guat.lxy.common.exception.ArticleIdRequiredException;
import guat.lxy.common.exception.ArticleNotFoundException;
import guat.lxy.common.exception.NothingToUpdateException;
import guat.lxy.common.result.PageResult;
import guat.lxy.mapper.AdminMapper;
import guat.lxy.mapper.ArticleMapper;
import guat.lxy.mapper.UserMapper;
import guat.lxy.pojo.dto.ArticlePageQueryDTO;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.entity.User;
import guat.lxy.service.AdminService;
import guat.lxy.service.ArticleFieldDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserMapper userMapper;

    /** 更新前比对新旧值，只把真正变化的字段写下去（与普通作者更新共用） */
    @Autowired
    private ArticleFieldDiff articleFieldDiff;

    /**
     * 管理员接口统一鉴权：登录态已由 JwtInterceptor 完成三合一校验，
     * 这里只做管理员角色校验。
     * JWT 里只存了 userId/userName 没存 roleId，必须查库拿最新角色：
     * 防止用户登录后角色被降级（管理员被收回权限），旧 token 还能继续访问管理员接口
     *
     * @return 当前登录的管理员用户
     */
    private User validateAdmin() {
        String userName = BaseContext.getCurrentUserName();
        User user = userMapper.getByUsername(userName);
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (user.getRoleId() != RoleIdConstant.ADMIN_ROLE_ID) {
            throw new AdminPermissionDeniedException(MessageConstant.ADMIN_PERMISSION_DENIED);
        }
        return user;
    }

    @Override
    public PageResult<Article> getAllArticle(ArticlePageQueryDTO dto) {

        // ========== 1. 管理员身份校验 ==========
        validateAdmin();

        // ========== 2. 入参兜底（和公开列表 / 我的文章规则保持一致） ==========
        Integer pageNum = dto.getPageNum();
        Integer pageSize = dto.getPageSize();
        String keyword = dto.getKeyword();
        String author = dto.getAuthor();
        String sortBy = dto.getSortBy();
        String sortOrder = dto.getSortOrder();

        // pageNum：null / ≤0 → 1
        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;
        }
        // pageSize：null / ≤0 → 10；最大 100 防止拖库
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        pageSize = Math.min(pageSize, 100);
        // keyword：空串/全空格 → null
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        // author：空串/全空格 → null
        if (author != null && author.trim().isEmpty()) {
            author = null;
        }
        // sortBy：只允许 createTime / updateTime，其它一律 fallback 到 createTime
        if (sortBy == null || (!sortBy.equals("updateTime") && !sortBy.equals("createTime"))) {
            sortBy = "createTime";
        }
        // sortOrder：只允许 asc / desc，其它一律 fallback 到 desc
        if (sortOrder == null || (!sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc"))) {
            sortOrder = "desc";
        }

        // 兜底后的值写回 dto（给 XML 里的 test 和 LIMIT 取值用）
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        dto.setKeyword(keyword);
        dto.setAuthor(author);
        dto.setSortBy(sortBy);
        dto.setSortOrder(sortOrder.toLowerCase());

        // ========== 3. 计算 offset = (pageNum - 1) × pageSize ==========
        int offset = (pageNum - 1) * pageSize;
        dto.setOffset(offset);

        // ========== 4. 查全站文章总数（管理员不限制 status，草稿/已发布全部计入） ==========
        Long total = adminMapper.countAllArticle(dto);
        if (total == null) {
            total = 0L;
        }

        // ========== 5. total = 0 直接返回空（短路，省一次列表 SQL） ==========
        if (total == 0) {
            return new PageResult<>(0L, 0, pageNum, pageSize, Collections.emptyList());
        }

        // ========== 6. offset 已经 ≥ total（手动翻页翻过头）直接短路 ==========
        if (offset >= total) {
            int totalPages = (int) Math.ceil(total * 1.0 / pageSize);
            return new PageResult<>(total, totalPages, pageNum, pageSize, Collections.emptyList());
        }

        // ========== 7. 查当前页 records（管理员视角：所有状态文章都返回） ==========
        List<Article> records = adminMapper.selectAllArticlePage(dto);
        if (records == null) {
            records = Collections.emptyList();
        }

        // ========== 8. 算总页数 + 组装返回 ==========
        int totalPages = (int) Math.ceil(total * 1.0 / pageSize);
        return new PageResult<>(total, totalPages, pageNum, pageSize, records);
    }

    @Override
    public boolean updateArticle(Article article) {
        // 1. 管理员身份校验
        validateAdmin();

        // 2. 参数校验：id 不能为空
        if (article.getId() == null) {
            throw new ArticleIdRequiredException(MessageConstant.ARTICLE_ID_REQUIRED);
        }

        // 3. 参数校验：至少要修改一个字段（四个都没传说明调用方没给出任何意图）
        if (article.getTitle() == null && article.getContent() == null
                && article.getStatus() == null && article.getCategoryId() == null) {
            throw new NothingToUpdateException(MessageConstant.NOTHING_TO_UPDATE);
        }

        // 4. 检查文章是否存在
        Article dbArticle = articleMapper.getArticleById(article.getId());
        if (dbArticle == null) {
            throw new ArticleNotFoundException(MessageConstant.ARTICLE_NOT_FOUND);
        }

        // 注意：管理员可以修改任意用户的文章，这里【不做】作者归属校验
        // （普通用户更新 ArticleServiceImpl.updateArticle 里有 author.equals 校验，管理员接口刻意去掉）

        // 5. 只保留真正变化的字段。没改任何东西时不写库，update_time 保持不变
        //    （与普通作者更新共用同一份比对逻辑，保证两边行为一致）
        boolean changed = articleFieldDiff.keepOnlyChangedFields(article, dbArticle);

        // 6. 保险：强制清空绝对不能被修改的字段，防止动态 SQL 写漏
        article.setAuthor(null);
        article.setCreateTime(null);
        article.setUpdateTime(null);

        // 7. 更新数据库（复用 ArticleMapper 的动态 SQL：<set> + <if>，只写非 null 的列）
        if (changed) {
            articleMapper.updateArticle(article);
        }
        return changed;
    }

    @Override
    public void deleteArticle(Long id) {
        // 1. 管理员身份校验
        validateAdmin();

        // 2. 参数校验：id 不能为空
        if (id == null) {
            throw new ArticleIdRequiredException(MessageConstant.ARTICLE_ID_REQUIRED);
        }

        // 3. 检查文章是否存在
        Article dbArticle = articleMapper.getArticleById(id);
        if (dbArticle == null) {
            throw new ArticleNotFoundException(MessageConstant.ARTICLE_NOT_FOUND);
        }

        // 注意：管理员可以删除任意用户的文章，这里【不做】作者归属校验
        // （普通用户删除 ArticleServiceImpl.deleteArticle 里有 author.equals 校验，管理员接口刻意去掉）

        // 4. 执行物理删除（复用 ArticleMapper 的 deleteArticle）
        articleMapper.deleteArticle(id);
    }

    @Override
    public List<User> getAllUser() {
        // 1. 管理员身份校验
        validateAdmin();

        // 2. 查询全部账号，含管理员自己（前端按 role_id 区分「管理员 / 作者」）
        List<User> users = userMapper.getAllUsers();
        if (users == null) {
            return Collections.emptyList();
        }

        // 3. 按 userId 升序（order by 放 Java 侧，SQL 里不再引用未经证实的列名）
        users.sort((a, b) -> {
            Long ia = a.getUserId() == null ? Long.MAX_VALUE : a.getUserId();
            Long ib = b.getUserId() == null ? Long.MAX_VALUE : b.getUserId();
            return Long.compare(ia, ib);
        });
        return users;
    }
}
