package guat.lxy.service;

import guat.lxy.common.result.PageResult;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.entity.Category;

import java.util.List;

public interface CategoryService {

    /**
     * 分类列表（带 articleCount，只统计当前登录用户自己的文章）
     * @return 按 sort_order asc, id asc 排序的分类列表
     */
    List<Category> list();

    /**
     * 公开分类列表（无需登录，只返回 id/name/sortOrder，不带 articleCount）
     * @return 按 sort_order asc, id asc 排序的分类列表
     */
    List<Category> listPublic();

    /**
     * 新增分类（name 去空白后不能为空，sortOrder 为空默认 0）
     * @param category 分类内容
     */
    void add(Category category);

    /**
     * 更新分类（id 必填）
     * @param category 分类内容
     */
    void update(Category category);

    /**
     * 删除分类，同时把该分类下文章的 category_id 置为 NULL（同一事务）
     * @param id 分类 ID
     */
    void delete(Long id);

    /**
     * 某分类下当前登录用户的文章分页（status 不限，按 update_time DESC）
     * @param categoryId 分类 ID
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 文章分页结果
     */
    PageResult<Article> getArticlePageByCategory(Long categoryId, Integer pageNum, Integer pageSize);
}