package com.shafting.weighing.task.mapper;

import com.shafting.weighing.task.entity.TestRun;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
/** 测量运行数据访问接口。 */
public interface TestRunMapper extends BaseMapper<TestRun> {

    /** 当前单运行模型下，读取任务的第一条有效运行。 */
    @Select("SELECT * FROM test_run WHERE task_id = #{taskId} AND deleted = 0 ORDER BY id LIMIT 1")
    TestRun selectFirstByTaskId(@Param("taskId") Long taskId);

    /** 统计运行中或暂停中的任务，用于全局单通道互斥。 */
    @Select("SELECT COUNT(*) FROM test_run WHERE deleted = 0 AND state IN ('RUNNING', 'PAUSED')")
    int countActiveRuns();

    /** 按测量点读取最新一条尚未删除计算结果的运行，供显示数值页切换通道。 */
    @Select("""
            SELECT r.*
            FROM test_run r
            JOIN measurement_result mr ON mr.run_id = r.id AND mr.deleted = 0
            JOIN measurement_point p ON p.id = r.point_id AND p.deleted = 0
            WHERE r.deleted = 0 AND p.point_code = #{pointCode}
            ORDER BY r.id DESC
            LIMIT 1
            """)
    TestRun selectLatestResultRunByPointCode(@Param("pointCode") String pointCode);
}
