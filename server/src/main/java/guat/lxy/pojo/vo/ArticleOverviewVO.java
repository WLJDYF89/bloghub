package guat.lxy.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleOverviewVO {
    /** status = 0 的篇数 */
    private Long draftCount;

    /** status = 1 的篇数 */
    private Long publishedCount;

    /** 本周新建的篇数（自然周，周一起算） */
    private Long weekNewCount;

    /** 最久没动的那篇草稿躺了多少天；没有草稿时为 null */
    private Integer longestDraftDays;

    /** 最多 3 篇草稿，按 update_time 升序（最久没动的在前） */
    private List<OverviewArticleVO> staleDrafts;

    /** 最多 5 篇，status 不限，按 update_time 降序 */
    private List<OverviewArticleVO> recent;
}