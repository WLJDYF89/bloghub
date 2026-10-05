package guat.lxy.service.impl;

import guat.lxy.common.context.BaseContext;
import guat.lxy.common.constant.MessageConstant;
import guat.lxy.common.exception.ArticleIdRequiredException;
import guat.lxy.common.exception.ArticleNotFoundException;
import guat.lxy.common.exception.ArticlePermissionDeniedException;
import guat.lxy.common.exception.NothingToUpdateException;
import guat.lxy.common.result.PageResult;
import guat.lxy.mapper.ArticleMapper;
import guat.lxy.pojo.dto.ArticlePageQueryDTO;
import guat.lxy.pojo.dto.MyArticlePageDTO;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.vo.ArticleDetailVO;
import guat.lxy.pojo.vo.ArticleOverviewVO;
import guat.lxy.pojo.vo.OverviewArticleVO;
import guat.lxy.service.ArticleFieldDiff;
import guat.lxy.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    /** 更新前比对新旧值，只把真正变化的字段写下去（普通作者与管理员共用） */
    @Autowired
    private ArticleFieldDiff articleFieldDiff;

    @Override
    @Transactional
    public void addArticle(Article article) {
        // 作者从统一鉴权上下文取（author 不由前端传，避免伪造）
        String author = BaseContext.getCurrentUserName();
        article.setAuthor(author);

        // status 现已入库；未传时默认 1（已发布），防止 NOT NULL 列插入 NULL
        if (article.getStatus() == null) {
            article.setStatus(1);
        }

        // 插入后自增主键由 @Options(useGeneratedKeys) 回填到 article.id
        articleMapper.insertArticle(article);
    }

    @Override
    @Transactional
    public boolean updateArticle(Article article) {
        // 当前登录作者（拦截器已统一完成三合一校验）
        String author = BaseContext.getCurrentUserName();

        // 1. 参数校验：id 不能为空
        if (article.getId() == null) {
            throw new ArticleIdRequiredException(MessageConstant.ARTICLE_ID_REQUIRED);
        }

        // 2. 参数校验：至少要修改一个字段（四个字段都没传时说明调用方没给出任何意图）
        if (article.getTitle() == null && article.getContent() == null
                && article.getStatus() == null && article.getCategoryId() == null) {
            throw new NothingToUpdateException(MessageConstant.NOTHING_TO_UPDATE);
        }

        // 3. 取出库里的现行值：既用于校验，也用于判断到底有没有真的改动
        Article dbArticle = articleMapper.getArticleById(article.getId());
        if (dbArticle == null) {
            throw new ArticleNotFoundException(MessageConstant.ARTICLE_NOT_FOUND);
        }

        // 4. 检查作者是否匹配
        if (!author.equals(dbArticle.getAuthor())) {
            throw new ArticlePermissionDeniedException(MessageConstant.ARTICLE_UPDATE_PERMISSION_DENIED);
        }

        // 5. 只保留真正变化的字段。
        //    前端保存时会把整份表单原样发回来（哪怕一个字都没改），
        //    所以「字段有值」不等于「字段变了」—— 必须和库里的值比一遍。
        //    没有任何字段变化时直接返回：不写库，update_time 自然不动。
        boolean changed = articleFieldDiff.keepOnlyChangedFields(article, dbArticle);

        // 6. 保险：强制清空绝对不能被修改的字段，防止动态 SQL 写漏
        article.setAuthor(null);
        article.setCreateTime(null);
        article.setUpdateTime(null);

        // 7. 更新数据库（只在我们确实比对出改动时才执行）
        if (changed) {
            articleMapper.updateArticle(article);
        }
        return changed;
    }

    @Override
    public void deleteArticle(Long id) {
        // 当前登录作者
        String author = BaseContext.getCurrentUserName();

        // 2. 参数校验：id 不能为空
        if (id == null) {
            throw new ArticleIdRequiredException(MessageConstant.ARTICLE_ID_REQUIRED);
        }

        // 3. 检查文章是否存在
        Article dbArticle = articleMapper.getArticleById(id);
        if (dbArticle == null) {
            throw new ArticleNotFoundException(MessageConstant.ARTICLE_NOT_FOUND);
        }

        // 4. 检查作者是否匹配（只能删自己的文章）
        if (!author.equals(dbArticle.getAuthor())) {
            throw new ArticlePermissionDeniedException(MessageConstant.ARTICLE_DELETE_PERMISSION_DENIED);
        }

        // 5. 执行删除
        articleMapper.deleteArticle(id);
    }

    @Override
    public PageResult<Article> getArticlePage(ArticlePageQueryDTO dto) {

        // ========== 1. 入参兜底（防止前端传 null / 0 / 非法值） ==========
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

        // 把兜底后的值写回 dto（给 XML 里的 test 和 SQL 取值用）
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        dto.setKeyword(keyword);
        dto.setAuthor(author);
        dto.setSortBy(sortBy);
        dto.setSortOrder(sortOrder.toLowerCase());

        // ========== 2. 计算 offset = (pageNum - 1) × pageSize ==========
        int offset = (pageNum - 1) * pageSize;
        dto.setOffset(offset);

        // ========== 3. 查总数 total ==========
        Long total = articleMapper.countArticleByCondition(dto);
        if (total == null) {
            total = 0L;
        }

        // ========== 4. total = 0 直接返回空（短路，不再查列表 SQL） ==========
        if (total == 0) {
            return new PageResult<>(0L, 0, pageNum, pageSize, java.util.Collections.emptyList());
        }

        // ========== 5. total > 0 但 offset 已经 ≥ total（比如用户手动翻到最后一页之后） ==========
        // 这种情况 MySQL LIMIT 会返回空，但我们直接短路，省一次 SQL
        if (offset >= total) {
            int totalPages = (int) Math.ceil(total * 1.0 / pageSize);
            return new PageResult<>(total, totalPages, pageNum, pageSize, java.util.Collections.emptyList());
        }

        // ========== 6. 查当前页的 records ==========
        java.util.List<Article> records = articleMapper.selectArticlePage(dto);
        if (records == null) {
            records = java.util.Collections.emptyList();
        }

        // ========== 7. 计算总页数 + 组装返回 ==========
        int totalPages = (int) Math.ceil(total * 1.0 / pageSize);
        return new PageResult<>(total, totalPages, pageNum, pageSize, records);
    }

    @Override
    public ArticleDetailVO getArticleById(Long id) {
        ArticleDetailVO articleDetailVO = articleMapper.getArticleDetailById(id);
        if (articleDetailVO == null) {
            return null;
        }

        // 草稿只有作者本人能看。游客和非作者一律按「文章不存在」处理 ——
        // 公开详情接口对所有人开放，返回「你没权限」等于承认这里躺着一篇草稿，
        // 用同一个文案既不泄露存在性，也省得前端处理两种失败。
        // 作者身份来自 BaseContext：JwtInterceptor 对这条路径做的是可选登录，
        // 没带 token 时这里就是 null，也就是游客。
        if (!Integer.valueOf(1).equals(articleDetailVO.getStatus())) {
            String currentUser = BaseContext.getCurrentUserName();
            if (currentUser == null || !currentUser.equals(articleDetailVO.getAuthor())) {
                throw new ArticleNotFoundException(MessageConstant.ARTICLE_NOT_FOUND);
            }
        }

        return articleDetailVO;
    }

    @Override
    public PageResult<Article> getArticlePageByUser(MyArticlePageDTO dto) {
        // 当前登录作者（author 绝对不能从前端传，只能从上下文拿，防止看别人的草稿）
        String author = BaseContext.getCurrentUserName();

        // ========== 3. 入参兜底（和公开列表 getArticlePage 规则一致） ==========
        Integer pageNum = dto.getPageNum();
        Integer pageSize = dto.getPageSize();
        String keyword = dto.getKeyword();
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
        // sortBy：只允许 createTime / updateTime，其它一律 fallback 到 createTime
        if (sortBy == null || (!sortBy.equals("updateTime") && !sortBy.equals("createTime"))) {
            sortBy = "createTime";
        }
        // sortOrder：只允许 asc / desc，其它一律 fallback 到 desc
        if (sortOrder == null || (!sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc"))) {
            sortOrder = "desc";
        }

        // 兜底后的值全部写回 dto（给 XML 的 <if test="dto.xxx"> 和 LIMIT #{dto.xxx} 用）
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        dto.setKeyword(keyword);
        dto.setSortBy(sortBy);
        dto.setSortOrder(sortOrder.toLowerCase());

        // ========== 4. 计算 offset ==========
        int offset = (pageNum - 1) * pageSize;
        dto.setOffset(offset);

        // ========== 5. 查总数 total（作者=我，按 keyword 过滤） ==========
        Long total = articleMapper.countMyArticlePage(author, dto);
        if (total == null) {
            total = 0L;
        }

        // ========== 6. total = 0 直接返回空 ==========
        if (total == 0) {
            return new PageResult<>(0L, 0, pageNum, pageSize, java.util.Collections.emptyList());
        }

        // ========== 7. offset >= total 直接短路 ==========
        if (offset >= total) {
            int totalPages = (int) Math.ceil(total * 1.0 / pageSize);
            return new PageResult<>(total, totalPages, pageNum, pageSize, java.util.Collections.emptyList());
        }

        // ========== 8. 查当前页 records（作者=我，不强制 status=1，草稿/已发布全返回） ==========
        java.util.List<Article> records = articleMapper.getArticlePageByUser(author, dto);
        if (records == null) {
            records = java.util.Collections.emptyList();
        }

        // ========== 9. 算总页数 + 组装返回 ==========
        int totalPages = (int) Math.ceil(total * 1.0 / pageSize);
        return new PageResult<>(total, totalPages, pageNum, pageSize, records);
    }

    @Override
    public ArticleOverviewVO getOverview() {
        // 作者固定为当前登录用户
        String author = BaseContext.getCurrentUserName();

        ArticleOverviewVO vo = new ArticleOverviewVO();

        // 1. 篇数统计（null 兜底为 0）
        Long draftCount = articleMapper.countDraftByAuthor(author);
        Long publishedCount = articleMapper.countPublishedByAuthor(author);
        Long weekNewCount = articleMapper.countWeekNewByAuthor(author);
        vo.setDraftCount(draftCount == null ? 0L : draftCount);
        vo.setPublishedCount(publishedCount == null ? 0L : publishedCount);
        vo.setWeekNewCount(weekNewCount == null ? 0L : weekNewCount);

        // 2. 最久没动的草稿躺了多少天；没有草稿时保持 null（不返回 0）
        vo.setLongestDraftDays(articleMapper.getLongestDraftDays(author));

        // 3. 最久没动的草稿（最多 3 篇）+ 最近编辑（最多 5 篇），无条件返回空 list
        java.util.List<OverviewArticleVO> staleDrafts = articleMapper.getStaleDrafts(author);
        vo.setStaleDrafts(staleDrafts == null ? java.util.Collections.emptyList() : staleDrafts);

        java.util.List<OverviewArticleVO> recent = articleMapper.getRecentArticles(author);
        vo.setRecent(recent == null ? java.util.Collections.emptyList() : recent);

        return vo;
    }
}
