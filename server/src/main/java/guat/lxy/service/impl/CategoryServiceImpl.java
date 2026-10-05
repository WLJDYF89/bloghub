package guat.lxy.service.impl;

import guat.lxy.common.constant.MessageConstant;
import guat.lxy.common.constant.RoleIdConstant;
import guat.lxy.common.context.BaseContext;
import guat.lxy.common.exception.AccountNotFoundException;
import guat.lxy.common.exception.AdminPermissionDeniedException;
import guat.lxy.common.exception.CategoryNameDuplicateException;
import guat.lxy.common.exception.ParamInvalidException;
import guat.lxy.common.result.PageResult;
import guat.lxy.mapper.CategoryMapper;
import guat.lxy.mapper.UserMapper;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.entity.Category;
import guat.lxy.pojo.entity.User;
import guat.lxy.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 改名是管理员专属：分类名全站共享，普通作者不能改。
     * JWT 里只有 userId/userName 没有 roleId，必须查库拿最新角色 ——
     * 防止用户登录后被降级，旧 token 仍能改名。
     */
    private void validateAdmin() {
        String userName = BaseContext.getCurrentUserName();
        User user = userMapper.getByUsername(userName);
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (user.getRoleId() != RoleIdConstant.ADMIN_ROLE_ID) {
            throw new AdminPermissionDeniedException(MessageConstant.ADMIN_PERMISSION_DENIED);
        }
    }

    @Override
    public List<Category> list() {
        // articleCount 只统计当前登录用户自己的文章
        String author = BaseContext.getCurrentUserName();

        List<Category> categories = categoryMapper.listWithArticleCount(author);
        return categories == null ? Collections.emptyList() : categories;
    }

    @Override
    public List<Category> listPublic() {
        // 公开接口：不校验登录、不查当前用户，也不做每人文章数统计
        List<Category> categories = categoryMapper.listPublic();
        return categories == null ? Collections.emptyList() : categories;
    }

    @Override
    public void add(Category category) {
        // 0. 管理员专属：新建分类要管理员身份
        validateAdmin();

        // 1. 分类名非空校验（去空白）
        String name = category.getName() == null ? null : category.getName().trim();
        if (name == null || name.isEmpty()) {
            throw new ParamInvalidException(MessageConstant.CATEGORY_NAME_EMPTY);
        }
        category.setName(name);

        // 2. sortOrder 为空默认 0（数据库列 NOT NULL）
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }

        // 3. 落库；name 命中 UNIQUE 约束时转成中文人话异常
        try {
            categoryMapper.insertCategory(category);
        } catch (DuplicateKeyException e) {
            throw new CategoryNameDuplicateException(MessageConstant.CATEGORY_NAME_DUPLICATE);
        }
    }

    @Override
    public void update(Category category) {
        // 0. 管理员专属：改分类名要管理员身份
        validateAdmin();

        // 1. id 必填
        if (category.getId() == null) {
            throw new ParamInvalidException(MessageConstant.CATEGORY_ID_REQUIRED);
        }

        // 2. 分类名非空校验（去空白）
        String name = category.getName() == null ? null : category.getName().trim();
        if (name == null || name.isEmpty()) {
            throw new ParamInvalidException(MessageConstant.CATEGORY_NAME_EMPTY);
        }
        category.setName(name);

        // 3. sortOrder 为空默认 0（数据库列 NOT NULL）
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }

        // 4. 落库；name 命中 UNIQUE 约束时转成中文人话异常
        try {
            categoryMapper.updateCategory(category);
        } catch (DuplicateKeyException e) {
            throw new CategoryNameDuplicateException(MessageConstant.CATEGORY_NAME_DUPLICATE);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 0. 管理员专属：删分类要管理员身份
        validateAdmin();

        // 1. id 必填
        if (id == null) {
            throw new ParamInvalidException(MessageConstant.CATEGORY_ID_REQUIRED);
        }

        // 2. 先解绑文章（category_id = NULL），再删分类，同一事务
        categoryMapper.clearArticleCategory(id);
        categoryMapper.deleteCategory(id);
    }

    @Override
    public PageResult<Article> getArticlePageByCategory(Long categoryId, Integer pageNum, Integer pageSize) {
        // 作者固定为当前登录用户
        String author = BaseContext.getCurrentUserName();

        // ========== 1. 入参兜底（和 ArticleServiceImpl.getArticlePage 规则一致） ==========
        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        pageSize = Math.min(pageSize, 100);

        // ========== 2. 计算 offset = (pageNum - 1) × pageSize ==========
        int offset = (pageNum - 1) * pageSize;

        // ========== 3. 查总数 total ==========
        Long total = categoryMapper.countArticleByCategory(author, categoryId);
        if (total == null) {
            total = 0L;
        }

        // ========== 4. total = 0 直接返回空（短路） ==========
        if (total == 0) {
            return new PageResult<>(0L, 0, pageNum, pageSize, Collections.emptyList());
        }

        // ========== 5. offset ≥ total 直接短路 ==========
        if (offset >= total) {
            int totalPages = (int) Math.ceil(total * 1.0 / pageSize);
            return new PageResult<>(total, totalPages, pageNum, pageSize, Collections.emptyList());
        }

        // ========== 6. 查当前页 records ==========
        List<Article> records = categoryMapper.getArticlePageByCategory(author, categoryId, offset, pageSize);
        if (records == null) {
            records = Collections.emptyList();
        }

        // ========== 7. 算总页数 + 组装返回 ==========
        int totalPages = (int) Math.ceil(total * 1.0 / pageSize);
        return new PageResult<>(total, totalPages, pageNum, pageSize, records);
    }
}