package guat.lxy.pojo.dto;

import lombok.Data;

@Data
public class MyArticlePageDTO {
    private Integer pageNum;

    private Integer pageSize;

    private String keyword;

    private String sortBy;

    private String sortOrder;

    // 可空过滤条件：null 表示不过滤
    private Integer status;

    private Long categoryId;

    // 非前端传入：Service 层计算好后 set 进去，SQL 里 LIMIT #{offset} 要用
    private Integer offset;
}
