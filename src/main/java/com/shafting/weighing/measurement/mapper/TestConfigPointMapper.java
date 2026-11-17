package com.shafting.weighing.measurement.mapper;

import com.shafting.weighing.measurement.entity.TestConfigPoint;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 参数方案中各测量点参数的基础数据访问接口。 */
public interface TestConfigPointMapper extends BaseMapper<TestConfigPoint> {
}
