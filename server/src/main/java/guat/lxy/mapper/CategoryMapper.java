package guat.lxy.mapper;

import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 分类列表（带 articleCount）
     * articleCount 只统计「当前登录作者」名下的文章，author 由 Service 层传入
     */
    List<Category> listWithArticleCount(@Param("author") String author);

    /** 公开分类列表：只返回 id/name/sort_order，无 articleCount 子查询 */
    List<Category> listPublic();

    @Insert("insert into category (name, sort_order) values (#{name}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertCategory(Category category);

    @Update("update category set name = #{name}, sort_order = #{sortOrder} where id = #{id}")
    void updateCategory(Category category);

    @Delete("delete from category where id = #{id}")
    void deleteCategory(Long id);

    /** 删除分类时把归属该分类的文章置为未归类（category_id = NULL） */
    @Update("update article set category_id = null where category_id = #{categoryId}")
    void clearArticleCategory(Long categoryId);

    @Select("select count(*) from article where author = #{author} and category_id = #{categoryId}")
    Long countArticleByCategory(@Param("author") String author, @Param("categoryId") Long categoryId);

    /** 某分类下当前登录作者的文章分页（status 不限），按 update_time DESC */
    List<Article> getArticlePageByCategory(@Param("author") String author,
                                           @Param("categoryId") Long categoryId,
                                           @Param("offset") Integer offset,
                                           @Param("pageSize") Integer pageSize);
}