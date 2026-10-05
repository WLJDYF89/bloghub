package guat.lxy.service;

import guat.lxy.common.result.PageResult;
import guat.lxy.pojo.dto.ArticlePageQueryDTO;
import guat.lxy.pojo.dto.MyArticlePageDTO;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.vo.ArticleDetailVO;
import guat.lxy.pojo.vo.ArticleOverviewVO;

public interface ArticleService {

    /**
     * 添加文章（作者 = 当前登录用户，从 BaseContext 获取）
     * @param article 文章内容（title、content 等）
     */
    void addArticle(Article article);

    /**
     * 更新文章（校验作者归属：只能改自己的）
     *
     * 只把「与库里现行值不同」的字段写下去，并且只有真的存在这样的字段时才更新
     * update_time。因此「打开编辑器一个字都没改，直接点保存」不会顶掉更新时间。
     *
     * @param article 文章内容（id 必填，其余字段为空表示本次不改）
     * @return true 表示确实有字段被改动并落库；false 表示与库里完全一致、什么都没写
     */
    boolean updateArticle(Article article);

    /**
     * 删除文章（校验作者归属：只能删自己的）
     * @param id 文章 ID
     */
    void deleteArticle(Long id);

    /**
     * 分页查询文章
     * @param dto 分页查询参数
     * @return 文章列表
     */
    PageResult<Article> getArticlePage(ArticlePageQueryDTO dto);

    /**
     * 根据 ID 获取文章
     * @param id 文章 ID
     * @return 文章详情
     */
    ArticleDetailVO getArticleById(Long id);

    /**
     * 分页查询「我的文章」（作者 = 当前登录用户，从 BaseContext 获取）
     * @param dto   分页查询参数
     * @return 文章列表
     */
    PageResult<Article> getArticlePageByUser(MyArticlePageDTO dto);

    /**
     * 个人文章总览统计（作者 = 当前登录用户，从 BaseContext 获取）
     * @return 草稿/已发布/本周新增篇数、最久未动草稿、最近编辑列表
     */
    ArticleOverviewVO getOverview();
}
