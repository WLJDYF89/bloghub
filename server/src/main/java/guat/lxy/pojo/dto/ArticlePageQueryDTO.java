package guat.lxy.pojo.dto;

import lombok.Data;

@Data
public class ArticlePageQueryDTO {
    private Integer pageNum;

    private Integer pageSize;

    private String keyword;

    private String author;

    private Long categoryId;

    private String sortBy;

    private String sortOrder;

    // 非前端传入：Service 层计算好后 set 进去，SQL 里 LIMIT #{offset} 要用
    private Integer offset;
}

