package com.shafting.weighing.measurement.controller;

import com.shafting.weighing.measurement.dto.ConfigDto;
import com.shafting.weighing.measurement.service.MeasurementConfigService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
/**
 * 测量参数接口，对应前端“轴系设置界面”。
 *
 * <p>页面先读取六个逻辑测量点和指定参数方案；管理员或工程师可保存整套参数，
 * 也可在通道 Tab 页只新增或更新当前测量点参数，两种操作均形成审计记录。</p>
 */
public class MeasurementConfigController {

    private final MeasurementConfigService measurementConfigService;

    public MeasurementConfigController(MeasurementConfigService measurementConfigService) {
        this.measurementConfigService = measurementConfigService;
    }

    /**
     * 读取启用的六个测量点，用于生成设置页的列和工作页的通道选择器。
     *
     * @return 按显示顺序排列的测量点
     */
    @GetMapping("/points")
    public List<ConfigDto.PointResponse> listPoints() {
        return measurementConfigService.listPoints();
    }

    /**
     * 读取一个参数方案，对应设置页初始化和刷新操作。
     *
     * @param configId 参数方案主键
     * @return 参数方案及六个测量点的参数
     */
    @GetMapping("/configs/{configId}")
    public ConfigDto.ConfigResponse getConfig(@PathVariable Long configId) {
        return measurementConfigService.getConfig(configId);
    }

    /**
     * 保存设置页提交的六点参数，仅管理员和工程师可操作。
     *
     * @param configId 参数方案主键
     * @param request 设置页提交的完整六点参数
     * @param authentication 当前登录用户，用于记录操作人
     * @return 保存并递增版本号后的参数方案
     */
    @PutMapping("/configs/{configId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ConfigDto.ConfigResponse updateConfig(@PathVariable Long configId,
                                                 @Valid @RequestBody ConfigDto.UpdateConfigRequest request,
                                                 Authentication authentication) {
        return measurementConfigService.updateConfig(configId, request, authentication.getName());
    }

    /**
     * 保存设置页当前通道的参数；数据库没有该通道参数时新增，已有时按主键更新。
     *
     * <p>仅管理员和工程师可调用。路径通道与请求中的 pointCode 必须一致；成功后递增
     * 参数方案版本并返回包含六个通道的最新方案。</p>
     *
     * @param configId 参数方案主键
     * @param pointCode 当前 Tab 对应的测量点编码
     * @param request 当前通道的全部参数
     * @param authentication 当前登录用户，用于记录操作人
     * @return 保存后的最新参数方案
     */
    @PutMapping("/configs/{configId}/points/{pointCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER')")
    public ConfigDto.ConfigResponse savePointConfig(@PathVariable Long configId,
                                                    @PathVariable String pointCode,
                                                    @Valid @RequestBody ConfigDto.PointConfigRequest request,
                                                    Authentication authentication) {
        return measurementConfigService.savePointConfig(configId, pointCode, request,
                authentication.getName());
    }

    /**
     * 保存工作页当前通道的手动目标位移，供后续手动上升命令读取。
     *
     * <p>ADMIN、ENGINEER、OPERATOR 可调用。目标必须大于 0；数据库没有当前通道参数行时
     * 新增，已有时只更新手动目标位移，同时递增参数版本并写入审计。</p>
     *
     * @param configId 参数方案主键
     * @param pointCode 工作页当前通道编码
     * @param request 目标位移，单位 mm、精度 0.01
     * @param authentication 当前登录用户，用于记录操作人
     * @return 保存后的最新参数方案
     */
    @PutMapping("/configs/{configId}/points/{pointCode}/manual-target")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'OPERATOR')")
    public ConfigDto.ConfigResponse saveManualTarget(@PathVariable Long configId,
                                                     @PathVariable String pointCode,
                                                     @Valid @RequestBody ConfigDto.ManualTargetRequest request,
                                                     Authentication authentication) {
        return measurementConfigService.saveManualTarget(configId, pointCode, request,
                authentication.getName());
    }
}
