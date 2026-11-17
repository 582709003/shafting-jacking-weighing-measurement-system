package com.shafting.weighing.curve.mapper;

import com.shafting.weighing.curve.entity.MeasurementResult;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
/** 测量运行当前结果的数据访问接口。 */
public interface MeasurementResultMapper extends BaseMapper<MeasurementResult> {
    /** 按运行读取唯一当前结果。 */
    @Select("SELECT * FROM measurement_result WHERE run_id=#{runId} AND deleted=0")
    MeasurementResult selectByRunId(@Param("runId") Long runId);

    /** 曲线重新拟合后将结果恢复为待保存，清除旧保存人和保存时间。 */
    @Update("""
            UPDATE measurement_result
            SET result_status = 'DRAFT', saved_by = NULL, saved_at = NULL
            WHERE run_id = #{runId} AND deleted = 0
            """)
    int markDraft(@Param("runId") Long runId);

    /** 显示数值页确认保存当前结果。 */
    @Update("""
            UPDATE measurement_result
            SET result_status = 'SAVED', saved_by = #{savedBy}, saved_at = #{savedAt}
            WHERE run_id = #{runId} AND deleted = 0
            """)
    int markSaved(@Param("runId") Long runId,
                  @Param("savedBy") Long savedBy,
                  @Param("savedAt") LocalDateTime savedAt);
}
