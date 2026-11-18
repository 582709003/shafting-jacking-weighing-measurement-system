package com.shafting.weighing.alarm.mapper;

import com.shafting.weighing.alarm.entity.AlarmEvent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/** 报警事件基础增删改查接口。 */
@Mapper
public interface AlarmEventMapper extends BaseMapper<AlarmEvent> {

    /** 统计尚未关闭的报警，用于模式接通和自动测试启动联锁。 */
    @Select("SELECT COUNT(*) FROM alarm_event WHERE status <> 'CLOSED'")
    int countOpenAlarms();

    /** 按稳定编码读取尚未关闭的同一报警，避免 PLC 轮询重复入库。 */
    @Select("SELECT * FROM alarm_event WHERE alarm_code = #{alarmCode} AND status <> 'CLOSED' ORDER BY id DESC LIMIT 1")
    AlarmEvent selectOpenByCode(@Param("alarmCode") String alarmCode);
}
