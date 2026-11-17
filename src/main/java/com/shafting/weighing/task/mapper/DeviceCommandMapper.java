package com.shafting.weighing.task.mapper;

import org.apache.ibatis.annotations.Insert;

import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

import org.apache.ibatis.annotations.Select;

@Mapper
/** 运行命令幂等记录的数据访问接口。 */
public interface DeviceCommandMapper {

    /** 按唯一命令号查询其所属运行，用于识别重复请求。 */
    @Select("SELECT run_id FROM device_command WHERE command_no = #{commandNo}")
    Long selectRunIdByCommandNo(@Param("commandNo") String commandNo);

    /** 保存已由本地模拟器确认执行的命令。 */
    @Insert("""
            INSERT INTO device_command
                (command_no, run_id, command_type, status, sent_at, acked_at)
            VALUES
                (#{commandNo}, #{runId}, #{commandType}, 'ACKED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            """)
    int insertCommand(@Param("commandNo") String commandNo,
                      @Param("runId") Long runId,
                      @Param("commandType") String commandType);
}
