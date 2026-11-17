package com.shafting.weighing.device.controller;

import com.shafting.weighing.device.entity.MeasurementSample;
import com.shafting.weighing.device.mapper.MeasurementSampleMapper;
import com.shafting.weighing.task.entity.TestRun;
import com.shafting.weighing.task.mapper.TestRunMapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 原始采样接口，对应两个曲线页面左侧的升降数据表。 */
@RestController
@RequestMapping("/api/runs")
public class MeasurementSampleController {

    private final MeasurementSampleMapper sampleMapper;
    private final TestRunMapper testRunMapper;

    public MeasurementSampleController(MeasurementSampleMapper sampleMapper, TestRunMapper testRunMapper) {
        this.sampleMapper = sampleMapper;
        this.testRunMapper = testRunMapper;
    }

    /**
     * 按采样序号读取指定运行的有效原始数据。
     *
     * <p>曲线页面将 LIFTING 和 LOWERING 阶段分别组成上升、下降曲线；
     * 人工调整拟合线不会修改这里返回的原始数据。登录用户均可读取，
     * 但运行尚未完整完成上升和下降流程时不允许提前查看。</p>
     *
     * @param runId 测量运行主键
     * @return 按 sequenceNo 升序排列的有效采样
     * @throws ResponseStatusException 运行不存在时返回 404，流程未完成时返回 409
     */
    @GetMapping("/{runId}/samples")
    public List<MeasurementSample> samples(@PathVariable Long runId) {
        TestRun run = testRunMapper.selectById(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "测量运行不存在");
        }
        if (!"COMPLETED".equals(run.getState()) || !"COMPLETED".equals(run.getProcessStage())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "测量运行尚未完成上升和下降流程");
        }
        return sampleMapper.selectByRunId(runId);
    }
}
