package com.shafting.weighing.alarm.mapper;

import com.shafting.weighing.alarm.entity.AlarmAction;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
/** 报警处理动作的数据访问接口。 */
public interface AlarmActionMapper extends BaseMapper<AlarmAction> {
    /** 按记录顺序读取某报警的完整处理历史。 */
    @Select("SELECT * FROM alarm_action WHERE alarm_id=#{alarmId} ORDER BY id")
    List<AlarmAction> selectByAlarmId(@Param("alarmId") Long alarmId);
}
