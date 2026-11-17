package com.shafting.weighing.alarm.mapper;

import com.shafting.weighing.alarm.entity.AlarmEvent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
/** 报警事件基础增删改查接口。 */
public interface AlarmEventMapper extends BaseMapper<AlarmEvent> {

    /** 统计尚未关闭的报警，用于模式接通和自动测试启动联锁。 */
    @Select("SELECT COUNT(*) FROM alarm_event WHERE status <> 'CLOSED'")
    int countOpenAlarms();
}
