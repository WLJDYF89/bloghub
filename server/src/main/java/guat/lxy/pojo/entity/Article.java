package guat.lxy.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Article {
    /** 新增后由 @Options(useGeneratedKeys) 自动回填自增主键 */
    private Long id;
    private String title;
    private String content;
    private String author;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private Integer status;

    /** 所属分类 id；NULL 表示未归类。0 是 add/update 时「清空分类」的哨兵值，落库为 NULL */
    private Long categoryId;
}
