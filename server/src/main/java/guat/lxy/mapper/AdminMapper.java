package guat.lxy.mapper;

import guat.lxy.pojo.dto.ArticlePageQueryDTO;
import guat.lxy.pojo.entity.Article;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AdminMapper {

    /**
     * 管理员视角：统计全站文章数量
     * 不限制 status，草稿(0)/已发布(1) 全部计入
     */
    Long countAllArticle(ArticlePageQueryDTO dto);

    /**
     * 管理员视角：分页查询全站文章
     * 不限制 status，支持 keyword（标题/内容模糊）+ author 过滤
     */
    List<Article> selectAllArticlePage(ArticlePageQueryDTO dto);
}
