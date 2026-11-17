package com.shafting.weighing.measurement.mapper;

import com.shafting.weighing.measurement.entity.MeasurementPoint;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 六个逻辑测量点的基础数据访问接口。 */
public interface MeasurementPointMapper extends BaseMapper<MeasurementPoint> {
}
