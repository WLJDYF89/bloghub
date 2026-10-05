package guat.lxy.service;

import guat.lxy.pojo.entity.Article;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 文章字段比对：算出「这次提交相对库里现行值，到底哪些字段真的变了」。
 *
 * 存在的理由：前端点保存时会把整份表单原样发回来（哪怕一个字都没改），
 * 所以「字段有值」并不等于「字段变了」。如果直接照单全收地 UPDATE，
 * 就会出现「什么都没改、更新时间却被顶掉」的情况。
 *
 * 因此更新前先比一遍，把没变化的字段清成 null（mapper 的动态 SQL 遇到 null 就不写这一列），
 * 调用方再根据返回值决定要不要执行 UPDATE —— 没有变化就根本不写库。
 *
 * 普通作者更新（ArticleServiceImpl）与管理员更新（AdminServiceImpl）共用这一份逻辑，
 * 避免两处各写一遍、日后只改一处导致行为不一致。
 *
 * 注意：比较用的是 JDK 的 equals（区分大小写、不做 trim），
 * 与 MySQL 默认排序规则（utf8mb4_0900_ai_ci，不区分大小写）不完全等价。
 * 可能出现「库认为没变、这里认为变了」，代价只是多写一次 update_time，不会丢数据。
 * 反过来（这里认为没变、实际库里有差异）不会发生，所以不存在该更新而没更新的问题。
 */
@Component
public class ArticleFieldDiff {

    /**
     * 就地裁剪 article：只保留相对 dbArticle 真正发生变化的字段。
     *
     * @param article   本次提交的内容（会被就地修改）
     * @param dbArticle 库里的现行值（不能为 null）
     * @return 是否有字段真的变了
     */
    public boolean keepOnlyChangedFields(Article article, Article dbArticle) {
        boolean changed = false;

        if (article.getTitle() != null) {
            if (article.getTitle().equals(dbArticle.getTitle())) {
                article.setTitle(null);          // 没变 → 不写这一列
            } else {
                changed = true;
            }
        }

        if (article.getContent() != null) {
            if (article.getContent().equals(dbArticle.getContent())) {
                article.setContent(null);
            } else {
                changed = true;
            }
        }

        if (article.getStatus() != null) {
            if (article.getStatus().equals(dbArticle.getStatus())) {
                article.setStatus(null);
            } else {
                changed = true;
            }
        }

        if (article.getCategoryId() != null) {
            // 接口约定：categoryId = 0 表示「清空分类」，落库为 NULL。
            // 先归一成 null 再比，否则 0 与库里的 NULL 会被判成「有改动」。
            Long incoming = (article.getCategoryId() == 0L) ? null : article.getCategoryId();
            if (Objects.equals(incoming, dbArticle.getCategoryId())) {
                article.setCategoryId(null);
            } else {
                changed = true;
            }
        }

        return changed;
    }
}
