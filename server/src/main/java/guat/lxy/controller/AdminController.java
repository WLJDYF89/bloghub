package guat.lxy.controller;

import guat.lxy.common.result.PageResult;
import guat.lxy.common.result.Result;
import guat.lxy.pojo.dto.ArticlePageQueryDTO;
import guat.lxy.pojo.entity.Article;
import guat.lxy.pojo.entity.User;
import guat.lxy.service.AdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/article/list")
    public Result<PageResult<Article>> getAllArticle(@ModelAttribute ArticlePageQueryDTO dto) {
        log.info("管理员查询全站文章");
        PageResult<Article> pageResult = adminService.getAllArticle(dto);

        return Result.success(pageResult);
    }

    /**
     * 管理员更新文章。data 回传「本次是否真的有字段被改动」：
     * false 表示与库里完全一致，后端什么都没写，update_time 也保持不变。
     */
    @PutMapping("/article/update")
    public Result<Boolean> updateArticle(@RequestBody Article article) {
        log.info("管理员更新文章");
        boolean changed = adminService.updateArticle(article);
        return Result.success(changed);
    }

    @DeleteMapping("/article/delete/{id}")
    public Result<?> deleteArticle(@PathVariable("id") Long id) {
        log.info("管理员删除文章");
        adminService.deleteArticle(id);
        return Result.success();
    }

    @GetMapping("/user/list")
    public Result<List<User>> getAllUser() {
        log.info("管理员查询所有用户");
        List<User> users = adminService.getAllUser();
        return Result.success(users);
    }
}
