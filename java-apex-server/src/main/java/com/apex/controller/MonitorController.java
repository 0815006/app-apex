package com.apex.controller;

import com.apex.common.Result;
import com.apex.entity.MonitorMachine;
import com.apex.model.*;
import com.apex.service.MonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 监控模块 REST 控制器。
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    // =============================================
    // 机器管理
    // =============================================

    @GetMapping("/machine/list")
    public Result<List<MonitorMachine>> listMachines() {
        return Result.success(monitorService.listMachines());
    }

    @PostMapping("/machine")
    public Result<MonitorMachine> addMachine(@Valid @RequestBody MonitorMachineDTO dto) {
        return Result.success(monitorService.addMachine(dto));
    }

    @PutMapping("/machine")
    public Result<Void> updateMachine(@Valid @RequestBody MonitorMachineDTO dto) {
        monitorService.updateMachine(dto);
        return Result.success();
    }

    @DeleteMapping("/machine/{id}")
    public Result<Void> deleteMachine(@PathVariable Integer id) {
        monitorService.deleteMachine(id);
        return Result.success();
    }

    @PutMapping("/machine/{id}/toggle")
    public Result<Void> toggleMachine(@PathVariable Integer id) {
        monitorService.toggleMachine(id);
        return Result.success();
    }

    // =============================================
    // 全量指标
    // =============================================

    @GetMapping("/machine/{machineId}/metrics")
    public Result<MonitorFullMetricsVO> getFullMetrics(@PathVariable Integer machineId) {
        return Result.success(monitorService.getFullMetrics(machineId));
    }

    // =============================================
    // 指标定制
    // =============================================

    @GetMapping("/machine/{machineId}/custom-metrics")
    public Result<List<CustomMetricVO>> getCustomizedMetrics(@PathVariable Integer machineId) {
        return Result.success(monitorService.getCustomizedMetrics(machineId));
    }

    @PostMapping("/machine/{machineId}/custom-metric")
    public Result<Void> addCustomMetric(@PathVariable Integer machineId,
                                         @Valid @RequestBody MonitorCustomMetricDTO dto) {
        monitorService.addCustomMetric(machineId, dto);
        return Result.success();
    }

    @DeleteMapping("/machine/{machineId}/custom-metric/{metricId}")
    public Result<Void> removeCustomMetric(@PathVariable Integer machineId,
                                            @PathVariable Integer metricId) {
        monitorService.removeCustomMetric(machineId, metricId);
        return Result.success();
    }

    // =============================================
    // 实时监控
    // =============================================

    @GetMapping("/machine/{machineId}/realtime")
    public Result<MonitorRealtimeVO> getRealtimeMetrics(@PathVariable Integer machineId) {
        return Result.success(monitorService.getRealtimeMetrics(machineId));
    }

    // =============================================
    // 采样任务
    // =============================================

    @GetMapping("/sample/task/list")
    public Result<List<SampleTaskVO>> listSampleTasks() {
        return Result.success(monitorService.listSampleTasks());
    }

    @PostMapping("/sample/task")
    public Result<Void> createSampleTask(@Valid @RequestBody MonitorSampleTaskDTO dto) {
        monitorService.createSampleTask(dto);
        return Result.success();
    }

    @DeleteMapping("/sample/task/{id}")
    public Result<Void> deleteSampleTask(@PathVariable Integer id) {
        monitorService.deleteSampleTask(id);
        return Result.success();
    }

    // =============================================
    // 历史数据
    // =============================================

    @GetMapping("/sample/task/{taskId}/history")
    public Result<List<MonitorHistoryVO>> getTaskHistory(@PathVariable Integer taskId) {
        return Result.success(monitorService.getTaskHistory(taskId));
    }

    @GetMapping("/sample/task/{taskId}/history/latest")
    public Result<MonitorHistoryVO> getTaskHistoryLatest(@PathVariable Integer taskId) {
        MonitorHistoryVO vo = monitorService.getTaskHistoryLatest(taskId);
        return Result.success(vo);
    }
}
