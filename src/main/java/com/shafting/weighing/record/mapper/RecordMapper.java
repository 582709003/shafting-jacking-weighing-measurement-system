package com.shafting.weighing.record.mapper;

import com.shafting.weighing.record.dto.RecordRow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
/** 记录页面任务摘要的关联查询接口。 */
public interface RecordMapper {
    /** 关联任务、运行、测量点和当前结果，最新任务优先。 */
    @Select("""
            SELECT t.id AS task_id, r.id AS run_id, t.task_no, t.task_name,
                   p.point_code, p.point_name, r.state, mr.measured_force_kn,
                   t.started_at, t.completed_at
            FROM test_task t
            JOIN test_run r ON r.task_id=t.id AND r.deleted=0
            JOIN measurement_point p ON p.id=r.point_id
            LEFT JOIN measurement_result mr ON mr.run_id=r.id AND mr.deleted=0
            WHERE t.deleted=0
            ORDER BY t.id DESC
            """)
    List<RecordRow> selectRecords();

    /**
     * 按通道读取已完整执行上升和下降阶段的任务，供两个曲线页的任务下拉框使用。
     * 仅返回状态已完成且两个阶段均有有效采样的运行，最近完成的任务排在前面。
     */
    @Select("""
            SELECT t.id AS task_id, r.id AS run_id, t.task_no, t.task_name,
                   p.point_code, p.point_name, r.state, mr.measured_force_kn,
                   t.started_at, t.completed_at
            FROM test_task t
            JOIN test_run r ON r.task_id = t.id AND r.deleted = 0
            JOIN measurement_point p ON p.id = r.point_id AND p.deleted = 0
            LEFT JOIN measurement_result mr ON mr.run_id = r.id AND mr.deleted = 0
            WHERE t.deleted = 0
              AND p.point_code = #{pointCode}
              AND r.state = 'COMPLETED'
              AND r.process_stage = 'COMPLETED'
              AND 2 <= (
                  SELECT COUNT(*) FROM measurement_sample s
                  WHERE s.run_id = r.id AND s.valid = 1 AND s.stage = 'LIFTING'
              )
              AND 2 <= (
                  SELECT COUNT(*) FROM measurement_sample s
                  WHERE s.run_id = r.id AND s.valid = 1 AND s.stage = 'LOWERING'
              )
            ORDER BY COALESCE(r.ended_at, t.completed_at) DESC, r.id DESC
            """)
    List<RecordRow> selectCompletedByPointCode(@Param("pointCode") String pointCode);
}
