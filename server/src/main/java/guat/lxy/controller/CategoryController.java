package guat.lxy.controller;

import guat.lxy.common.result.PageResult;
import guat.lxy.common.result.Result;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.entity.Category;
import guat.lxy.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
@Slf4j
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/list")
    public Result<List<Category>> list() {
        log.info("获取分类列表");
        List<Category> categories = categoryService.list();
        return Result.success(categories);
    }

    @GetMapping("/public")
    public Result<List<Category>> listPublic() {
        log.info("获取公开分类列表");
        List<Category> categories = categoryService.listPublic();
        return Result.success(categories);
    }

    @PostMapping("/add")
    public Result add(@RequestBody Category category) {
        log.info("新增分类: {}", category);
        categoryService.add(category);
        return Result.success();
    }

    @PutMapping("/update")
    public Result update(@RequestBody Category category) {
        log.info("更新分类: {}", category);
        categoryService.update(category);
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable("id") Long id) {
        log.info("删除分类: {}", id);
        categoryService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/articles")
    public Result<PageResult<Article>> getArticlePageByCategory(
            @PathVariable("id") Long id,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        log.info("获取分类下文章分页列表: categoryId={}, pageNum={}, pageSize={}", id, pageNum, pageSize);
        PageResult<Article> pageResult = categoryService.getArticlePageByCategory(id, pageNum, pageSize);
        return Result.success(pageResult);
    }
}