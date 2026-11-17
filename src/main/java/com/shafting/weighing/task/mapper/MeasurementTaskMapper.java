package com.shafting.weighing.task.mapper;

import com.shafting.weighing.task.entity.MeasurementTask;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 测量任务基础增删改查接口。 */
public interface MeasurementTaskMapper extends BaseMapper<MeasurementTask> {
}
