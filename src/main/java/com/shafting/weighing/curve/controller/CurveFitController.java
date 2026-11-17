package com.shafting.weighing.curve.controller;

import com.shafting.weighing.curve.dto.CurveDto;
import com.shafting.weighing.curve.service.CurveFitService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 曲线拟合与测量结果接口，对应“轴系曲线”“主机曲线”和“显示数值”页面。 */
@RestController
@RequestMapping("/api")
public class CurveFitController {

    private final CurveFitService curveFitService;

    public CurveFitController(CurveFitService curveFitService) {
        this.curveFitService = curveFitService;
    }

    /**
     * 使用升、降阶段原始数据执行自动拟合。
     *
     * <p>系统分别从两个阶段选择斜率最大的相邻两点，再将直线延长到位移 0
     * 和本次最高位移，保存为曲线页可拖动的 AUTO 修订。</p>
     *
     * @param runId 测量运行主键
     * @param authentication 当前操作人
     * @return 拟合线、截距和计算结果；非管理员响应不包含调整系数
     */
    @PostMapping("/runs/{runId}/curve-fit/auto")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'OPERATOR')")
    public CurveDto.FitResponse auto(@PathVariable Long runId, Authentication authentication) {
        return visibleResult(curveFitService.autoFit(runId, authentication.getName()), authentication);
    }

    /**
     * 保存管理员在曲线页拖动端点后的人工拟合修订。
     *
     * @param runId 测量运行主键
     * @param request 升、降拟合线的两个端点；纵坐标必须分别为 0 和本次最高位移
     * @param authentication 当前管理员，用于审计
     * @return 新生成的 MANUAL 修订和重算结果
     */
    @PostMapping("/runs/{runId}/curve-fit/manual")
    @PreAuthorize("hasRole('ADMIN')")
    public CurveDto.FitResponse manual(@PathVariable Long runId,
                                       @Valid @RequestBody CurveDto.ManualRequest request,
                                       Authentication authentication) {
        return curveFitService.manualFit(runId, request, authentication.getName());
    }

    /**
     * 读取曲线页当前应显示的最新拟合修订。
     *
     * @param runId 测量运行主键
     * @param authentication 当前登录用户，用于判断调整系数可见性
     * @return 最新拟合及结果；非管理员响应不包含调整系数
     */
    @GetMapping("/runs/{runId}/curve-fit")
    public CurveDto.FitResponse latest(@PathVariable Long runId, Authentication authentication) {
        return visibleResult(curveFitService.latest(runId), authentication);
    }

    /**
     * 按任务读取“显示数值界面”所需的结果。
     *
     * @param taskId 测量任务主键
     * @param authentication 当前登录用户，用于判断调整系数可见性
     * @return 任务首个运行的最新拟合及结果；非管理员响应不包含调整系数
     */
    @GetMapping("/tasks/{taskId}/results")
    public CurveDto.FitResponse result(@PathVariable Long taskId, Authentication authentication) {
        return visibleResult(curveFitService.taskResult(taskId), authentication);
    }

    /**
     * 显示数值页切换通道时读取该通道最新一次计算结果。
     * 登录用户均可读取；该接口只返回计算结果，不返回压力、位移等原始采样。
     *
     * @param pointCode 六个测量点之一的稳定编码
     * @param authentication 当前登录用户，用于判断调整系数可见性
     * @return 任务、运行、保存状态以及标准值、测量值和偏差；非管理员不含调整系数
     * @throws org.springframework.web.server.ResponseStatusException 通道不存在结果时返回 404
     */
    @GetMapping("/results/latest")
    public CurveDto.ValuePageResponse latestResult(@RequestParam String pointCode,
                                                   Authentication authentication) {
        return visibleResult(curveFitService.latestPointResult(pointCode), authentication);
    }

    /**
     * 显示数值页选择已完成任务后，按运行读取该任务的计算结果。
     * 登录用户均可读取；运行尚未生成拟合和计算结果时返回 404。
     *
     * @param runId 已完成测量任务对应的运行主键
     * @param authentication 当前登录用户，用于判断调整系数可见性
     * @return 任务信息、保存状态、标准值、测量值和偏差结果；非管理员不含调整系数
     */
    @GetMapping("/runs/{runId}/result")
    public CurveDto.ValuePageResponse runResult(@PathVariable Long runId, Authentication authentication) {
        return visibleResult(curveFitService.runResult(runId), authentication);
    }

    /**
     * 点击显示数值页“保存结果”时确认当前计算结果。
     * ADMIN、ENGINEER、OPERATOR 可操作；曲线页是否可读取由测量流程完成状态决定。
     *
     * @param runId 当前通道页面展示的运行主键
     * @param authentication 当前操作账号，用于保存人和审计日志
     * @return 保存后的结果及 SAVED 状态
     * @throws org.springframework.web.server.ResponseStatusException 运行或计算结果不存在时返回 404
     */
    @PostMapping("/runs/{runId}/result/save")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENGINEER', 'OPERATOR')")
    public CurveDto.ValuePageResponse saveResult(@PathVariable Long runId, Authentication authentication) {
        return visibleResult(curveFitService.saveResult(runId, authentication.getName()), authentication);
    }

    /**
     * 管理员在显示数值页修改本次运行的调整系数并重新计算结果。
     *
     * <p>修改后结果恢复为 DRAFT，管理员需再次点击“保存结果”确认；其他角色返回 403。</p>
     *
     * @param runId 当前测量运行主键
     * @param request 大于 0、最多四位小数的调整系数
     * @param authentication 当前管理员，用于审计日志
     * @return 重新计算后的测量值、偏差和调整系数
     */
    @PatchMapping("/runs/{runId}/result/adjustment-coefficient")
    @PreAuthorize("hasRole('ADMIN')")
    public CurveDto.ValuePageResponse updateAdjustmentCoefficient(
            @PathVariable Long runId,
            @Valid @RequestBody CurveDto.AdjustmentCoefficientRequest request,
            Authentication authentication) {
        return curveFitService.updateAdjustmentCoefficient(runId, request.adjustmentCoefficient(),
                authentication.getName());
    }

    /**
     * 点击显示数值页“删除结果”时逻辑删除结果和原始采样。
     * 仅 ADMIN 可操作；任务、运行、曲线修订及审计记录不会删除。
     *
     * @param runId 当前通道页面展示的运行主键
     * @param authentication 当前管理员，用于审计日志
     * @throws org.springframework.web.server.ResponseStatusException 运行结果不存在时返回 404
     */
    @DeleteMapping("/runs/{runId}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteResult(@PathVariable Long runId, Authentication authentication) {
        curveFitService.deleteResult(runId, authentication.getName());
    }

    private CurveDto.FitResponse visibleResult(CurveDto.FitResponse response, Authentication authentication) {
        return isAdmin(authentication) ? response : response.withoutAdjustmentCoefficient();
    }

    private CurveDto.ValuePageResponse visibleResult(CurveDto.ValuePageResponse response,
                                                     Authentication authentication) {
        return isAdmin(authentication) ? response : response.withoutAdjustmentCoefficient();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
