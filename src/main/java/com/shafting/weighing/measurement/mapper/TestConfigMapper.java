package com.shafting.weighing.measurement.mapper;

import com.shafting.weighing.measurement.entity.TestConfig;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/** 参数方案主表的基础数据访问接口。 */
public interface TestConfigMapper extends BaseMapper<TestConfig> {
}
