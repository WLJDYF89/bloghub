package guat.lxy.mapper;

import guat.lxy.pojo.dto.ArticlePageQueryDTO;
import guat.lxy.pojo.dto.MyArticlePageDTO;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.vo.ArticleDetailVO;
import guat.lxy.pojo.vo.OverviewArticleVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleMapper {
    @Insert("insert into article (title, content, author, status, category_id) values (#{title}, #{content}, #{author}, #{status}, #{categoryId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertArticle(Article article);

    @Select("select * from article where id = #{id}")
    Article getArticleById(Long id);

    @Select("select * from article where id = #{id}")
    ArticleDetailVO getArticleDetailById(Long id);

    /**
     * 动态更新（SQL 在 ArticleMapper.xml）。只写实体里非 null 的列。
     * 调用方只传「确实变化的字段」，所以正常路径下必然影响 1 行。
     */
    int updateArticle(Article article);

    @Delete("delete from article where id = #{id}")
    void deleteArticle(Long id);

    Long countArticleByCondition(ArticlePageQueryDTO dto);

    List<Article> selectArticlePage(ArticlePageQueryDTO dto);

    Long countMyArticlePage(@Param("author") String author, @Param("dto") MyArticlePageDTO dto);

    List<Article> getArticlePageByUser(@Param("author") String author, @Param("dto") MyArticlePageDTO dto);

    // ==================== 个人文章总览统计 ====================

    Long countDraftByAuthor(@Param("author") String author);

    Long countPublishedByAuthor(@Param("author") String author);

    Long countWeekNewByAuthor(@Param("author") String author);

    Integer getLongestDraftDays(@Param("author") String author);

    List<OverviewArticleVO> getStaleDrafts(@Param("author") String author);

    List<OverviewArticleVO> getRecentArticles(@Param("author") String author);
}
