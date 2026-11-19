package com.shafting.weighing.alarm.mapper;

import com.shafting.weighing.alarm.entity.AlarmEvent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 报警事件基础增删改查接口。 */
@Mapper
public interface AlarmEventMapper extends BaseMapper<AlarmEvent> {

    /** 统计尚未关闭的报警，用于模式接通和自动测试启动联锁。 */
    @Select("SELECT COUNT(*) FROM alarm_event WHERE status <> 'CLOSED'")
    int countOpenAlarms();

    /** 按稳定编码读取尚未关闭的同一报警，避免 PLC 轮询重复入库。 */
    @Select("SELECT * FROM alarm_event WHERE alarm_code = #{alarmCode} AND status <> 'CLOSED' ORDER BY id DESC LIMIT 1")
    AlarmEvent selectOpenByCode(@Param("alarmCode") String alarmCode);

    /**
     * 分页查询报警页签；关键词同时匹配报警编码、来源和报警内容。
     *
     * @param statusGroup ACTIVE、PROCESSING 或 CLOSED
     * @param keyword 关键词，空字符串表示不筛选
     * @param alarmLevel 报警级别，空字符串表示全部级别
     * @param startTime 发生时间下限，为空表示不限制
     * @param endTime 发生时间上限，为空表示不限制
     * @param offset 数据库分页偏移量
     * @param pageSize 本页最多返回数量
     * @return 按发生时间倒序排列的当前页报警
     */
    @Select("""
            <script>
            SELECT * FROM alarm_event
            WHERE ((#{statusGroup} = 'ACTIVE' AND status = 'ACTIVE')
                OR (#{statusGroup} = 'PROCESSING' AND status IN ('ACKNOWLEDGED', 'RECOVERED'))
                OR (#{statusGroup} = 'CLOSED' AND status = 'CLOSED'))
              AND (#{keyword} = ''
                   OR LOWER(alarm_code) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                   OR LOWER(source_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                   OR LOWER(message) LIKE CONCAT('%', LOWER(#{keyword}), '%'))
              AND (#{alarmLevel} = '' OR alarm_level = #{alarmLevel})
            <if test="startTime != null">AND occurred_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND occurred_at &lt;= #{endTime}</if>
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{pageSize} OFFSET #{offset}
            </script>
            """)
    List<AlarmEvent> selectPage(@Param("statusGroup") String statusGroup,
                                @Param("keyword") String keyword,
                                @Param("alarmLevel") String alarmLevel,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                @Param("offset") long offset,
                                @Param("pageSize") int pageSize);

    /** 按与分页查询相同的条件统计记录数。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM alarm_event
            WHERE ((#{statusGroup} = 'ACTIVE' AND status = 'ACTIVE')
                OR (#{statusGroup} = 'PROCESSING' AND status IN ('ACKNOWLEDGED', 'RECOVERED'))
                OR (#{statusGroup} = 'CLOSED' AND status = 'CLOSED'))
              AND (#{keyword} = ''
                   OR LOWER(alarm_code) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                   OR LOWER(source_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                   OR LOWER(message) LIKE CONCAT('%', LOWER(#{keyword}), '%'))
              AND (#{alarmLevel} = '' OR alarm_level = #{alarmLevel})
            <if test="startTime != null">AND occurred_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND occurred_at &lt;= #{endTime}</if>
            </script>
            """)
    long countPage(@Param("statusGroup") String statusGroup,
                   @Param("keyword") String keyword,
                   @Param("alarmLevel") String alarmLevel,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    /** 按状态统计页签数量，不受当前筛选条件影响。 */
    @Select("SELECT COUNT(*) FROM alarm_event WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /** 报警关闭时释放PLC防重键，使相同编码下一次能够重新创建报警。 */
    @Update("UPDATE alarm_event SET deduplication_key = NULL WHERE id = #{id}")
    int clearDeduplicationKey(@Param("id") Long id);
}
