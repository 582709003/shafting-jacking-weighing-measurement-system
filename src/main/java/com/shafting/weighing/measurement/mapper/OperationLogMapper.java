package com.shafting.weighing.measurement.mapper;

import org.apache.ibatis.annotations.Insert;

import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

@Mapper
/** 参数修改、控制命令和人工曲线修订共用的操作审计接口。 */
public interface OperationLogMapper {

    /** 保存一次六点参数方案更新及完整请求快照。 */
    @Insert("""
            INSERT INTO sys_operation_log
                (user_id, module, operation, business_id, result, detail_json)
            VALUES
                (#{userId}, 'CONFIG', 'UPDATE_CONFIG', #{businessId}, 'SUCCESS', #{detailJson})
            """)
    int insertConfigUpdate(@Param("userId") Long userId,
                           @Param("businessId") Long businessId,
                           @Param("detailJson") String detailJson);

    /** 保存通用业务操作审计。 */
    @Insert("""
            INSERT INTO sys_operation_log
                (user_id, module, operation, business_id, result, detail_json)
            VALUES
                (#{userId}, #{module}, #{operation}, #{businessId}, 'SUCCESS', #{detailJson})
            """)
    int insertOperation(@Param("userId") Long userId,
                        @Param("module") String module,
                        @Param("operation") String operation,
                        @Param("businessId") Long businessId,
                        @Param("detailJson") String detailJson);
}
