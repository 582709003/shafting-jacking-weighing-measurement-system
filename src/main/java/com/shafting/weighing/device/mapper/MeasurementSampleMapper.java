package com.shafting.weighing.device.mapper;

import com.shafting.weighing.device.entity.MeasurementSample;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
/** 原始测量采样的数据访问接口。 */
public interface MeasurementSampleMapper extends BaseMapper<MeasurementSample> {

    /** 按运行读取有效采样，并保持原始采集顺序。 */
    @Select("SELECT * FROM measurement_sample WHERE run_id = #{runId} AND valid = 1 ORDER BY sequence_no")
    List<MeasurementSample> selectByRunId(@Param("runId") Long runId);

    /** 逻辑删除一次运行的原始采样，保留数据库记录用于审计和人工恢复。 */
    @Update("UPDATE measurement_sample SET valid = 0 WHERE run_id = #{runId} AND valid = 1")
    int invalidateByRunId(@Param("runId") Long runId);
}
