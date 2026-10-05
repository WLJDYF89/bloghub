package guat.lxy.service;

import guat.lxy.common.result.PageResult;
import guat.lxy.pojo.dto.ArticlePageQueryDTO;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface AdminService {
    /**
     *  获取所有文章
     *  @return
     */
    PageResult<Article> getAllArticle(ArticlePageQueryDTO dto);

    /**
     * 管理员更新文章（可修改任意用户的文章，不做作者归属校验）
     * 只允许修改 title / content / category_id / status
     *
     * 与普通作者更新同一套语义：只写真正变化的字段，且只有确实存在变化时才更新
     * update_time。管理员「没改任何东西就点保存」同样不会顶掉更新时间。
     *
     * @return true 表示确实有字段被改动并落库；false 表示与库里完全一致、什么都没写
     */
    boolean updateArticle(Article article);

    /**
     * 管理员删除文章（可删除任意用户的文章，不做作者归属校验）
     */
    void deleteArticle(Long id);

    /**
     * 管理员查询所有用户（不含密码）
     */
    List<User> getAllUser();
}
