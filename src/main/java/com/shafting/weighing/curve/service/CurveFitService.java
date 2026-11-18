package com.shafting.weighing.curve.service;

import com.shafting.weighing.curve.dto.CurveDto;
import com.shafting.weighing.curve.dto.CurvePoint;
import com.shafting.weighing.curve.dto.CurveSegment;
import com.shafting.weighing.curve.entity.CurveFitRevision;
import com.shafting.weighing.curve.entity.MeasurementResult;
import com.shafting.weighing.curve.mapper.CurveFitRevisionMapper;
import com.shafting.weighing.curve.mapper.MeasurementResultMapper;
import com.shafting.weighing.device.entity.MeasurementSample;
import com.shafting.weighing.device.mapper.MeasurementSampleMapper;
import com.shafting.weighing.measurement.entity.MeasurementPoint;
import com.shafting.weighing.measurement.entity.TestConfigPoint;
import com.shafting.weighing.measurement.mapper.MeasurementPointMapper;
import com.shafting.weighing.measurement.mapper.OperationLogMapper;
import com.shafting.weighing.measurement.mapper.TestConfigPointMapper;
import com.shafting.weighing.security.mapper.SysUserMapper;
import com.shafting.weighing.task.entity.MeasurementTask;
import com.shafting.weighing.task.entity.TestRun;
import com.shafting.weighing.task.mapper.MeasurementTaskMapper;
import com.shafting.weighing.task.mapper.TestRunMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
/** 自动拟合、人工修订、测量结果计算和修订版本保存业务。 */
public class CurveFitService {

    private final MeasurementSampleMapper sampleMapper;
    private final CurveFitRevisionMapper revisionMapper;
    private final MeasurementResultMapper resultMapper;
    private final TestRunMapper runMapper;
    private final MeasurementTaskMapper taskMapper;
    private final TestConfigPointMapper configPointMapper;
    private final SysUserMapper userMapper;
    private final OperationLogMapper operationLogMapper;
    private final MeasurementPointMapper pointMapper;

    public CurveFitService(MeasurementSampleMapper sampleMapper,
                           CurveFitRevisionMapper revisionMapper,
                           MeasurementResultMapper resultMapper,
                           TestRunMapper runMapper,
                           MeasurementTaskMapper taskMapper,
                           TestConfigPointMapper configPointMapper,
                           SysUserMapper userMapper,
                           OperationLogMapper operationLogMapper,
                           MeasurementPointMapper pointMapper) {
        this.sampleMapper = sampleMapper;
        this.revisionMapper = revisionMapper;
        this.resultMapper = resultMapper;
        this.runMapper = runMapper;
        this.taskMapper = taskMapper;
        this.configPointMapper = configPointMapper;
        this.userMapper = userMapper;
        this.operationLogMapper = operationLogMapper;
        this.pointMapper = pointMapper;
    }

    /**
     * 分别从上升和下降原始采样中自动选择拟合线，并保存 AUTO 修订。
     *
     * @param runId 测量运行主键
     * @param username 当前操作账号
     * @return 新拟合版本和计算结果
     */
    @Transactional
    public CurveDto.FitResponse autoFit(Long runId, String username) {
        List<MeasurementSample> samples = sampleMapper.selectByRunId(runId);
        BigDecimal maximumDisplacement = maximumDisplacement(samples);
        CurveSegment lifting = CurveMath.anchorAtMaximumDisplacement(
                fit(samples, "LIFTING"), maximumDisplacement);
        CurveSegment lowering = CurveMath.anchorAtMaximumDisplacement(
                fit(samples, "LOWERING"), maximumDisplacement);
        return save(runId, "AUTO", lifting, lowering, username);
    }

    /**
     * 保存管理员拖动后的端点，形成不可覆盖的 MANUAL 修订和审计记录。
     */
    @Transactional
    public CurveDto.FitResponse manualFit(Long runId, CurveDto.ManualRequest request, String username) {
        requireValid(request.lifting());
        requireValid(request.lowering());
        requireManualEndpoints(request.lifting());
        requireManualEndpoints(request.lowering());
        CurveDto.FitResponse response = save(runId, "MANUAL", request.lifting(), request.lowering(), username);
        operationLogMapper.insertOperation(userMapper.selectIdByUsername(username), "CURVE", "MANUAL_REVISION",
                runId, "{\"revisionNo\":" + response.revisionNo() + "}");
        return response;
    }

    /** 读取某次运行最新修订及与该修订对应的结果。 */
    public CurveDto.FitResponse latest(Long runId) {
        requireRun(runId);
        CurveFitRevision revision = revisionMapper.selectLatest(runId);
        if (revision == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未生成拟合结果");
        }
        return response(revision, requireResult(runId));
    }

    /** 根据任务找到其首个运行并读取最新结果。 */
    public CurveDto.FitResponse taskResult(Long taskId) {
        TestRun run = runMapper.selectFirstByTaskId(taskId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "测量任务不存在");
        }
        return latest(run.getId());
    }

    /** 按通道读取最新一条未删除结果，供显示数值页切换六个测量点。 */
    public CurveDto.ValuePageResponse latestPointResult(String pointCode) {
        TestRun run = runMapper.selectLatestResultRunByPointCode(pointCode);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该通道尚无测量结果");
        }
        return valuePageResponse(run);
    }

    /** 按曲线页或显示数值页选择的运行读取对应计算结果，不回退到通道最新结果。 */
    public CurveDto.ValuePageResponse runResult(Long runId) {
        return valuePageResponse(requireRun(runId));
    }

    /**
     * 确认保存显示数值页当前结果。
     * 保存后曲线页才允许读取该运行的原始位移和压力采样。
     */
    @Transactional
    public CurveDto.ValuePageResponse saveResult(Long runId, String username) {
        TestRun run = requireRun(runId);
        MeasurementResult result = requireResult(runId);
        if (!"SAVED".equals(result.getResultStatus())) {
            LocalDateTime savedAt = LocalDateTime.now();
            resultMapper.markSaved(runId, userMapper.selectIdByUsername(username), savedAt);
            operationLogMapper.insertOperation(userMapper.selectIdByUsername(username), "RESULT", "SAVE",
                    runId, "{\"revisionNo\":" + result.getRevisionNo() + "}");
        }
        return valuePageResponse(run);
    }

    /**
     * 管理员修改本次测量结果的调整系数，并按最新曲线平均截距重新计算测量值和偏差。
     *
     * <p>调整系数属于本次结果，不反向修改参数方案；数值变化后原保存确认失效，
     * 结果恢复为 DRAFT 并记录审计日志。</p>
     *
     * @param runId 测量运行主键
     * @param adjustmentCoefficient 新调整系数，无单位、精度 0.0001
     * @param username 当前管理员账号
     * @return 重新计算后的显示数值页结果
     */
    @Transactional
    public CurveDto.ValuePageResponse updateAdjustmentCoefficient(
            Long runId, BigDecimal adjustmentCoefficient, String username) {
        TestRun run = requireRun(runId);
        MeasurementResult result = requireResult(runId);
        BigDecimal coefficient = adjustmentCoefficient.setScale(4, RoundingMode.HALF_UP);
        if (coefficient.compareTo(result.getAdjustmentCoefficient()) == 0) {
            return valuePageResponse(run);
        }
        CurveFitRevision revision = revisionMapper.selectLatest(runId);
        if (revision == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "测量结果缺少曲线拟合数据");
        }
        recalculateResult(result, result.getStandardForceKn(), revision.getMeanIntercept(), coefficient);
        resultMapper.updateById(result);
        resultMapper.markDraft(runId);
        operationLogMapper.insertOperation(userMapper.selectIdByUsername(username), "RESULT",
                "UPDATE_ADJUSTMENT_COEFFICIENT", runId,
                "{\"adjustmentCoefficient\":" + coefficient.toPlainString() + "}");
        return valuePageResponse(run);
    }

    /**
     * 逻辑删除结果和原始采样；任务、运行、拟合修订及审计记录保持不变。
     */
    @Transactional
    public void deleteResult(Long runId, String username) {
        MeasurementResult result = requireResult(runId);
        sampleMapper.invalidateByRunId(runId);
        resultMapper.deleteById(result.getId());
        operationLogMapper.insertOperation(userMapper.selectIdByUsername(username), "RESULT", "DELETE",
                runId, "{\"revisionNo\":" + result.getRevisionNo() + "}");
    }

    /**
     * 仅使用指定流程阶段的有效原始点，并严格按相邻点规则拟合。
     */
    private CurveSegment fit(List<MeasurementSample> samples, String stage) {
        List<CurvePoint> points = samples.stream()
                .filter(sample -> stage.equals(sample.getStage()))
                .map(sample -> new CurvePoint(sample.getForceKn(), sample.getDisplacementMm()))
                .toList();
        try {
            return CurveMath.fitAdjacent(points);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, stage + "阶段数据不足或无有效相邻点", exception);
        }
    }

    /**
     * 保存一版拟合线并重算结果。
     *
     * <p>测量值 =（上升截距 + 下降截距）÷ 2 × 顶升系数；
     * 调整量公式尚未确认，因此 adjustmentAmountMm 明确保留为空。</p>
     */
    private CurveDto.FitResponse save(Long runId, String source, CurveSegment lifting,
                                      CurveSegment lowering, String username) {
        TestRun run = requireRun(runId);
        BigDecimal liftingIntercept = intercept(lifting);
        BigDecimal loweringIntercept = intercept(lowering);
        BigDecimal mean = liftingIntercept.add(loweringIntercept)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        // 每次自动或人工拟合都新增版本，历史修订不覆盖。
        CurveFitRevision revision = new CurveFitRevision();
        revision.setRunId(runId);
        revision.setRevisionNo(revisionMapper.selectMaximumRevision(runId) + 1);
        revision.setSource(source);
        revision.setSegments(lifting, lowering);
        revision.setLiftingIntercept(liftingIntercept);
        revision.setLoweringIntercept(loweringIntercept);
        revision.setMeanIntercept(mean);
        revision.setCreatedBy(userMapper.selectIdByUsername(username));
        revisionMapper.insert(revision);

        TestConfigPoint config = requireConfig(run);
        // 每次运行只有一条当前结果，revisionNo 指向最新拟合版本。
        MeasurementResult result = resultMapper.selectByRunId(runId);
        if (result == null) {
            result = new MeasurementResult();
            result.setRunId(runId);
        }
        recalculateResult(result, config.getStandardForce(), mean, config.getJackingCoefficient());
        result.setAdjustmentAmountMm(null);
        result.setRevisionNo(revision.getRevisionNo());
        if (result.getId() == null) {
            result.setResultStatus("DRAFT");
            resultMapper.insert(result);
        } else {
            resultMapper.updateById(result);
            // 拟合内容发生变化后必须重新到显示数值页确认保存。
            resultMapper.markDraft(runId);
        }
        return response(revision, result);
    }

    /** 按指定系数统一计算测量值、偏差量和偏差率，避免自动拟合与人工修改使用不同公式。 */
    private void recalculateResult(MeasurementResult result, BigDecimal standard,
                                   BigDecimal meanIntercept, BigDecimal coefficient) {
        BigDecimal measured = meanIntercept.multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deviation = measured.subtract(standard).setScale(2, RoundingMode.HALF_UP);
        result.setStandardForceKn(standard);
        result.setMeasuredForceKn(measured);
        result.setDeviationKn(deviation);
        // 标准值为 0 时除法无意义，偏差率按接口约定返回 null。
        result.setDeviationRate(standard.signum() == 0 ? null
                : deviation.multiply(BigDecimal.valueOf(100)).divide(standard, 4, RoundingMode.HALF_UP));
        result.setAdjustmentCoefficient(coefficient);
    }

    private BigDecimal intercept(CurveSegment segment) {
        try {
            return CurveMath.forceIntercept(segment);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void requireValid(CurveSegment segment) {
        if (segment == null || segment.first() == null || segment.second() == null
                || segment.first().forceKn() == null || segment.first().displacementMm() == null
                || segment.second().forceKn() == null || segment.second().displacementMm() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "拟合线端点不完整");
        }
    }

    /** 下端点必须保留在横轴，上端点允许管理员在纵轴正值范围内上下调整。 */
    private void requireManualEndpoints(CurveSegment segment) {
        if (segment.first().displacementMm().compareTo(BigDecimal.ZERO) != 0
                || segment.second().displacementMm().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "拟合线下端点必须位于横轴，上端点位移必须大于0");
        }
    }

    /** 读取本次运行的最高有效位移，作为两条拟合线共同的上端点纵坐标。 */
    private BigDecimal maximumDisplacement(List<MeasurementSample> samples) {
        return samples.stream()
                .map(MeasurementSample::getDisplacementMm)
                .max(BigDecimal::compareTo)
                .filter(value -> value.signum() > 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "本次运行没有有效位移数据"));
    }

    private TestRun requireRun(Long runId) {
        TestRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "测量运行不存在");
        }
        return run;
    }

    private TestConfigPoint requireConfig(TestRun run) {
        MeasurementTask task = taskMapper.selectById(run.getTaskId());
        TestConfigPoint config = task == null ? null : configPointMapper.selectOne(
                Wrappers.<TestConfigPoint>lambdaQuery()
                        .eq(TestConfigPoint::getConfigId, task.getConfigId())
                        .eq(TestConfigPoint::getPointId, run.getPointId()));
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "测量参数不存在");
        }
        return config;
    }

    private MeasurementResult requireResult(Long runId) {
        MeasurementResult result = resultMapper.selectByRunId(runId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未生成测量结果");
        }
        return result;
    }

    private CurveDto.FitResponse response(CurveFitRevision revision, MeasurementResult result) {
        return new CurveDto.FitResponse(revision.getRevisionNo(), revision.getSource(),
                revision.lifting(), revision.lowering(), revision.getLiftingIntercept(),
                revision.getLoweringIntercept(), result.getMeasuredForceKn(), result.getStandardForceKn(),
                result.getDeviationKn(), result.getDeviationRate(), result.getAdjustmentCoefficient(),
                result.getAdjustmentAmountMm());
    }

    private CurveDto.ValuePageResponse valuePageResponse(TestRun run) {
        MeasurementTask task = taskMapper.selectById(run.getTaskId());
        MeasurementPoint point = pointMapper.selectById(run.getPointId());
        CurveFitRevision revision = revisionMapper.selectLatest(run.getId());
        MeasurementResult result = requireResult(run.getId());
        if (task == null || point == null || revision == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "测量结果关联数据不完整");
        }
        return new CurveDto.ValuePageResponse(task.getId(), run.getId(), task.getTaskNo(), task.getTaskName(),
                point.getPointCode(), point.getPointName(), run.getState(), result.getResultStatus(),
                result.getSavedAt(), response(revision, result));
    }
}
