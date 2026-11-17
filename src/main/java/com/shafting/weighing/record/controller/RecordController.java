package com.shafting.weighing.record.controller;

import com.shafting.weighing.record.dto.RecordRow;
import com.shafting.weighing.record.mapper.RecordMapper;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/records")
/** 任务归档接口，对应前端“记录界面”。 */
public class RecordController {

    private final RecordMapper recordMapper;

    public RecordController(RecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    /**
     * 读取记录页的任务列表，最新任务排在前面。
     *
     * @return 任务、测量点、状态和结果摘要
     */
    @GetMapping
    public List<RecordRow> records() {
        return recordMapper.selectRecords();
    }

    /**
     * 曲线页切换通道时读取可选择的已完成测量任务。
     * 登录用户均可读取；只有已完成上升、下降并保留两阶段有效采样的任务会返回。
     *
     * @param pointCode 六个测量点之一的稳定编码
     * @return 最近完成优先的任务、运行和测量结果摘要
     */
    @GetMapping("/completed")
    public List<RecordRow> completed(@RequestParam String pointCode) {
        return recordMapper.selectCompletedByPointCode(pointCode);
    }

    /**
     * 导出记录页全部任务，生成 Excel 可直接打开的 UTF-8 CSV。
     *
     * @return 带下载文件名和 UTF-8 BOM 的 CSV 字节流
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export() {
        StringBuilder csv = new StringBuilder("\uFEFF任务编号,任务名称,测量点,状态,测量值(kN),开始时间,完成时间\r\n");
        for (RecordRow row : recordMapper.selectRecords()) {
            csv.append(value(row.getTaskNo())).append(',')
                    .append(value(row.getTaskName())).append(',')
                    .append(value(row.getPointName())).append(',')
                    .append(value(row.getState())).append(',')
                    .append(value(row.getMeasuredForceKn())).append(',')
                    .append(value(row.getStartedAt())).append(',')
                    .append(value(row.getCompletedAt())).append("\r\n");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("measurement-records.csv").build());
        return ResponseEntity.ok().headers(headers).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** CSV 字段转义：空值输出空串，其余值使用双引号包裹。 */
    private String value(Object value) {
        if (value == null) {
            return "";
        }
        return '"' + value.toString().replace("\"", "\"\"") + '"';
    }
}
