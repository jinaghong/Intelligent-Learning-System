package com.hjh.tlias.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 智能出题的查询条件。
 *
 * count / difficulty 不设默认值，null 由 Service 兜底（5 道、中等）——
 * 和 LogQueryDTO 在字段上写默认值不同：这里默认值要拼进 Prompt、测试要断言，
 * 放 Service 里逻辑更集中（「谁负责语义谁兜底」）。
 *
 * 上限 20 道不是技术限制，是产品约束：一回出太多，老师也看不完，
 * token 还烧得快。
 */
@Data
public class QuestionQueryDTO {

    @NotNull(message = "课程 id 不能为空")
    private Long courseId;

    @Min(value = 1, message = "出题数量至少 1 道")
    @Max(value = 20, message = "出题数量最多 20 道")
    private Integer count;

    @Min(value = 1, message = "难度必须在 1~3 之间")
    @Max(value = 3, message = "难度必须在 1~3 之间")
    private Integer difficulty;

}
