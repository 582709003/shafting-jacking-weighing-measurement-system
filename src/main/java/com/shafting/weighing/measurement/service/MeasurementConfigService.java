package com.shafting.weighing.measurement.service;

import com.shafting.weighing.measurement.dto.ConfigDto;
import com.shafting.weighing.measurement.entity.MeasurementPoint;
import com.shafting.weighing.measurement.entity.TestConfig;
import com.shafting.weighing.measurement.entity.TestConfigPoint;
import com.shafting.weighing.measurement.mapper.MeasurementPointMapper;
import com.shafting.weighing.measurement.mapper.OperationLogMapper;
import com.shafting.weighing.measurement.mapper.TestConfigMapper;
import com.shafting.weighing.measurement.mapper.TestConfigPointMapper;
import com.shafting.weighing.security.mapper.SysUserMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 六点参数方案的读取、完整性校验、版本更新和审计业务。 */
@Service
public class MeasurementConfigService {

    private static final int ENABLED = 1;

    private final MeasurementPointMapper measurementPointMapper;
    private final TestConfigMapper testConfigMapper;
    private final TestConfigPointMapper testConfigPointMapper;
    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public MeasurementConfigService(MeasurementPointMapper measurementPointMapper,
                                    TestConfigMapper testConfigMapper,
                                    TestConfigPointMapper testConfigPointMapper,
                                    OperationLogMapper operationLogMapper,
                                    SysUserMapper sysUserMapper,
                                    ObjectMapper objectMapper) {
        this.measurementPointMapper = measurementPointMapper;
        this.testConfigMapper = testConfigMapper;
        this.testConfigPointMapper = testConfigPointMapper;
        this.operationLogMapper = operationLogMapper;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    /** 将启用的测量点实体转换为页面需要的基础信息。 */
    public List<ConfigDto.PointResponse> listPoints() {
        return enabledPoints().stream()
                .map(point -> new ConfigDto.PointResponse(point.getId(), point.getPointCode(),
                        point.getPointName(), point.getPointType(), point.getSortNo()))
                .toList();
    }

    /**
     * 组合参数方案主表、六个测量点和各点参数，供设置页一次加载。
     *
     * @param configId 参数方案主键
     * @return 完整六点参数方案
     */
    public ConfigDto.ConfigResponse getConfig(Long configId) {
        TestConfig config = requireConfig(configId);
        List<MeasurementPoint> points = enabledPoints();
        Map<Long, TestConfigPoint> configPointMap = testConfigPointMapper.selectList(
                        Wrappers.<TestConfigPoint>lambdaQuery().eq(TestConfigPoint::getConfigId, configId))
                .stream()
                .collect(Collectors.toMap(TestConfigPoint::getPointId, Function.identity()));
        List<ConfigDto.PointConfigResponse> pointResponses = points.stream()
                .map(point -> toPointResponse(point, configPointMap.get(point.getId())))
                .toList();
        return new ConfigDto.ConfigResponse(config.getId(), config.getConfigName(),
                config.getConfigVersion(), config.getStatus(), pointResponses);
    }

    /**
     * 原子保存整套六点参数、递增方案版本并写操作审计。
     *
     * <p>不允许只提交部分测量点，避免页面保存后形成一套不完整方案。</p>
     *
     * @param configId 参数方案主键
     * @param request 页面提交的完整方案
     * @param username 当前登录账号
     * @return 保存后的最新方案
     */
    @Transactional
    public ConfigDto.ConfigResponse updateConfig(Long configId,
                                                 ConfigDto.UpdateConfigRequest request,
                                                 String username) {
        TestConfig config = requireConfig(configId);
        List<MeasurementPoint> points = enabledPoints();
        validatePointSet(points, request.points());
        Map<String, ConfigDto.PointConfigRequest> requestMap = request.points().stream()
                .collect(Collectors.toMap(ConfigDto.PointConfigRequest::pointCode, Function.identity()));
        Map<Long, TestConfigPoint> configPointMap = testConfigPointMapper.selectList(
                        Wrappers.<TestConfigPoint>lambdaQuery().eq(TestConfigPoint::getConfigId, configId))
                .stream()
                .collect(Collectors.toMap(TestConfigPoint::getPointId, Function.identity()));
        // 按数据库中的启用点逐个更新，不能依赖前端传入顺序。
        for (MeasurementPoint point : points) {
            TestConfigPoint configPoint = configPointMap.get(point.getId());
            if (configPoint == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "参数方案缺少测量点数据");
            }
            copyValues(requestMap.get(point.getPointCode()), configPoint);
            testConfigPointMapper.updateById(configPoint);
        }
        config.setConfigName(request.configName());
        // 方案内容变化即产生新版本，历史操作内容保存在审计日志中。
        config.setConfigVersion(config.getConfigVersion() + 1);
        testConfigMapper.updateById(config);
        operationLogMapper.insertConfigUpdate(sysUserMapper.selectIdByUsername(username),
                configId, serialize(request));
        return getConfig(configId);
    }

    /**
     * 新增或更新一个通道的数据库参数，并递增方案版本、记录当前通道的审计快照。
     * 保存时不操作 PLC；自动测量动作开始前才读取 PLC 控制所需参数并下发。
     *
     * @param configId 参数方案主键
     * @param pointCode 页面当前 Tab 的测量点编码
     * @param request 当前通道提交的完整参数
     * @param username 当前登录账号
     * @return 保存后的完整方案
     */
    @Transactional
    public ConfigDto.ConfigResponse savePointConfig(Long configId, String pointCode,
                                                    ConfigDto.PointConfigRequest request,
                                                    String username) {
        TestConfig config = requireConfig(configId);
        if (!pointCode.equals(request.pointCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "路径通道与请求通道不一致");
        }
        MeasurementPoint point = requireEnabledPoint(pointCode);
        // 设置页只保存业务参数；自动测量开始前再写入并回读 PLC 控制所需参数。
        TestConfigPoint configPoint = testConfigPointMapper.selectOne(
                Wrappers.<TestConfigPoint>lambdaQuery()
                        .eq(TestConfigPoint::getConfigId, configId)
                        .eq(TestConfigPoint::getPointId, point.getId()));
        if (configPoint == null) {
            // 旧库或新测量点可能尚无参数行，首次保存时补建唯一的方案-测量点记录。
            configPoint = new TestConfigPoint();
            configPoint.setConfigId(configId);
            configPoint.setPointId(point.getId());
            copyValues(request, configPoint);
            testConfigPointMapper.insert(configPoint);
        } else {
            copyValues(request, configPoint);
            testConfigPointMapper.updateById(configPoint);
        }
        config.setConfigVersion(config.getConfigVersion() + 1);
        testConfigMapper.updateById(config);
        operationLogMapper.insertConfigUpdate(sysUserMapper.selectIdByUsername(username),
                configId, serialize(request));
        return getConfig(configId);
    }

    /**
     * 保存工作页当前通道的手动目标位移；参数行不存在时按数据库默认值补建。
     *
     * @param configId 参数方案主键
     * @param pointCode 当前工作通道编码
     * @param request 手动目标位移，单位 mm
     * @param username 当前登录账号
     * @return 保存后的完整参数方案
     */
    @Transactional
    public ConfigDto.ConfigResponse saveManualTarget(Long configId, String pointCode,
                                                     ConfigDto.ManualTargetRequest request,
                                                     String username) {
        TestConfig config = requireConfig(configId);
        MeasurementPoint point = requireEnabledPoint(pointCode);
        TestConfigPoint configPoint = testConfigPointMapper.selectOne(
                Wrappers.<TestConfigPoint>lambdaQuery()
                        .eq(TestConfigPoint::getConfigId, configId)
                        .eq(TestConfigPoint::getPointId, point.getId()));
        if (configPoint == null) {
            configPoint = new TestConfigPoint();
            configPoint.setConfigId(configId);
            configPoint.setPointId(point.getId());
            configPoint.setManualTargetDisplacement(request.targetDisplacementMm());
            testConfigPointMapper.insert(configPoint);
        } else {
            configPoint.setManualTargetDisplacement(request.targetDisplacementMm());
            testConfigPointMapper.updateById(configPoint);
        }
        config.setConfigVersion(config.getConfigVersion() + 1);
        testConfigMapper.updateById(config);
        operationLogMapper.insertConfigUpdate(sysUserMapper.selectIdByUsername(username),
                configId, serialize(Map.of("pointCode", pointCode,
                        "manualTargetDisplacement", request.targetDisplacementMm())));
        return getConfig(configId);
    }

    private List<MeasurementPoint> enabledPoints() {
        return measurementPointMapper.selectList(Wrappers.<MeasurementPoint>lambdaQuery()
                .eq(MeasurementPoint::getEnabled, ENABLED)
                .orderByAsc(MeasurementPoint::getSortNo));
    }

    private TestConfig requireConfig(Long configId) {
        TestConfig config = testConfigMapper.selectById(configId);
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "参数方案不存在");
        }
        return config;
    }

    private MeasurementPoint requireEnabledPoint(String pointCode) {
        return enabledPoints().stream()
                .filter(item -> pointCode.equals(item.getPointCode()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "测量点不存在或已停用"));
    }

    /** 校验提交集合与当前启用测量点完全一致，防止缺点和重复点。 */
    private void validatePointSet(List<MeasurementPoint> points,
                                  List<ConfigDto.PointConfigRequest> requests) {
        HashSet<String> expectedCodes = points.stream()
                .map(MeasurementPoint::getPointCode)
                .collect(Collectors.toCollection(HashSet::new));
        HashSet<String> actualCodes = requests.stream()
                .map(ConfigDto.PointConfigRequest::pointCode)
                .collect(Collectors.toCollection(HashSet::new));
        if (requests.size() != points.size() || !actualCodes.equals(expectedCodes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须提交全部六个测量点且不能重复");
        }
    }

    private void copyValues(ConfigDto.PointConfigRequest request, TestConfigPoint target) {
        target.setStandardForce(request.standardForce());
        target.setTotalValue(request.totalValue());
        target.setJackingCoefficient(request.jackingCoefficient());
        target.setMaxDisplacement(request.maxDisplacement());
        target.setMotorRpm(request.motorRpm());
        target.setForceIntervalKn(request.forceIntervalKn());
        target.setHoldSeconds(request.holdSeconds());
    }

    private ConfigDto.PointConfigResponse toPointResponse(MeasurementPoint point, TestConfigPoint configPoint) {
        if (configPoint == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "参数方案缺少测量点数据");
        }
        return new ConfigDto.PointConfigResponse(point.getPointCode(), point.getPointName(),
                configPoint.getStandardForce(), configPoint.getTotalValue(),
                configPoint.getManualTargetDisplacement(), configPoint.getJackingCoefficient(),
                configPoint.getMaxDisplacement(), configPoint.getMotorRpm(),
                configPoint.getForceIntervalKn(), configPoint.getHoldSeconds());
    }

    private String serialize(Object request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("参数审计序列化失败", exception);
        }
    }
}
