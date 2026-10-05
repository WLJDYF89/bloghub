package guat.lxy.controller;

import guat.lxy.common.result.PageResult;
import guat.lxy.common.result.Result;
import guat.lxy.pojo.dto.ArticlePageQueryDTO;
import guat.lxy.pojo.dto.MyArticlePageDTO;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.vo.ArticleDetailVO;
import guat.lxy.pojo.vo.ArticleOverviewVO;
import guat.lxy.service.ArticleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    @PostMapping("/add")
    public Result<Long> add(@RequestBody Article article) {
        log.info("发布个人博客");
        articleService.addArticle(article);
        // 回传新文章 id，前端存完草稿直接落到编辑页（编辑器要靠 id 自动保存）
        return Result.success(article.getId());
    }

    /**
     * 更新文章。data 回传「本次是否真的有字段被改动」：
     * false 表示提交的内容与库里完全一致（例如打开编辑器没改就点保存），
     * 此时后端什么都没写，update_time 也保持不变。
     */
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody Article article) {
        log.info("更新个人博客");
        boolean changed = articleService.updateArticle(article);
        return Result.success(changed);
    }

    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable("id") Long id){
        log.info("删除个人博客");
        articleService.deleteArticle(id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<PageResult<Article>> getArticlePage(@ModelAttribute ArticlePageQueryDTO dto) {
        log.info("获取文章分页列表: {}", dto);
        PageResult<Article> pageResult = articleService.getArticlePage(dto);
        return Result.success(pageResult);
    }

    @GetMapping("/overview")
    public Result<ArticleOverviewVO> getOverview() {
        log.info("获取个人文章总览统计");
        ArticleOverviewVO overview = articleService.getOverview();
        return Result.success(overview);
    }

    @GetMapping("/{id}")
    public Result<ArticleDetailVO> getArticleById(@PathVariable("id") Long id) {
        log.info("获取文章详情: {}", id);
        ArticleDetailVO articleDetailVO = articleService.getArticleById(id);
        return Result.success(articleDetailVO);
    }

    @GetMapping("/my")
    public Result<PageResult<Article>> getArticlePageByUser(@ModelAttribute MyArticlePageDTO dto) {
        log.info("获取我的文章分页列表: {}", dto);
        PageResult<Article> pageResult = articleService.getArticlePageByUser(dto);
        return Result.success(pageResult);
    }

}
