package com.shafting.weighing.record.dto;

import java.util.List;

/** 记录页面分页查询响应，包含当前页任务和分页统计。 */
public record RecordPageResponse(
        /** 当前页测量任务，按任务主键倒序排列。 */
        List<RecordRow> records,
        /** 当前页码，从 1 开始。 */
        int page,
        /** 每页记录数量。 */
        int pageSize,
        /** 符合搜索条件的任务总数。 */
        long total,
        /** 总页数；没有记录时仍返回 1，便于页面保持稳定页码。 */
        int totalPages) {
}
