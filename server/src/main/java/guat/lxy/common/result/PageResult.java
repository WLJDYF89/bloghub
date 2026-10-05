package guat.lxy.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {
    private Long total;

    private Integer totalPages;

    private Integer pageNum;

    private Integer pageSize;

    private List<T> records;
}

