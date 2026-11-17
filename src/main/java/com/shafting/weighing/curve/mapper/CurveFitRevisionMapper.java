package com.shafting.weighing.curve.mapper;

import com.shafting.weighing.curve.entity.CurveFitRevision;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
/** 曲线拟合修订的数据访问接口。 */
public interface CurveFitRevisionMapper extends BaseMapper<CurveFitRevision> {

    /** 查询运行当前最大修订号，新版本在此基础上加一。 */
    @Select("SELECT COALESCE(MAX(revision_no), 0) FROM curve_fit_revision WHERE run_id=#{runId}")
    int selectMaximumRevision(@Param("runId") Long runId);

    /** 查询运行最新一版自动或人工修订。 */
    @Select("SELECT * FROM curve_fit_revision WHERE run_id=#{runId} ORDER BY revision_no DESC LIMIT 1")
    CurveFitRevision selectLatest(@Param("runId") Long runId);
}
