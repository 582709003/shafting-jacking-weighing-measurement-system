package com.shafting.weighing.device.service;

import com.shafting.weighing.device.config.PlcProperties;
import jakarta.annotation.PreDestroy;
import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * S7-1200 的 PLC4X 通讯客户端。
 *
 * <p>本类维护单一连接并串行执行读写，避免多个页面操作同时改变控制点。
 * 任一通讯异常都会关闭当前连接；下一次操作只尝试重新建立一次连接，失败后直接向页面返回 503，
 * PLC 模式下绝不使用模拟值掩盖现场故障。</p>
 */
@Component
public class S7PlcClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(S7PlcClient.class);
    private static final String VALUE_ALIAS = "value";

    private final PlcProperties properties;
    private final DefaultPlcDriverManager driverManager = new DefaultPlcDriverManager();
    private final ReentrantLock ioLock = new ReentrantLock(true);
    private PlcConnection connection;

    public S7PlcClient(PlcProperties properties) {
        this.properties = properties;
    }

    /**
     * 向 BOOL 点发送完整脉冲，并保证脉冲期间没有其他线程插入 PLC 写操作。
     *
     * @param address PLC4X BOOL 标签
     */
    public void pulse(String address) {
        ensurePlcMode();
        ioLock.lock();
        try {
            writeInternal(Map.of(VALUE_ALIAS, new TagValue(address, true)));
            try {
                Thread.sleep(properties.getPulseMs());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PLC 指令脉冲被中断", exception);
            } finally {
                // 即使等待过程被中断，也必须尽力复位按钮位，避免 PLC 持续收到接通信号。
                writeInternal(Map.of(VALUE_ALIAS, new TagValue(address, false)));
            }
        } finally {
            ioLock.unlock();
        }
    }

    /** 等待 INT 状态点达到期望值，超时后返回明确的反馈超时错误。 */
    public void awaitInt(String address, int expectedValue, int timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (readInt(address) == expectedValue) {
                return;
            }
            sleepPollingInterval();
        }
        throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                "PLC 状态确认超时，期望值：" + expectedValue);
    }

    /** 等待 REAL 位移进入允许误差范围。 */
    public void awaitDecimal(String address, BigDecimal expectedValue, BigDecimal tolerance, int timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            BigDecimal difference = readDecimal(address).subtract(expectedValue).abs();
            if (difference.compareTo(tolerance) <= 0) {
                return;
            }
            sleepPollingInterval();
        }
        throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "PLC 位移清零确认超时");
    }

    /** 读取一个 INT 点。 */
    public int readInt(String address) {
        return readValues(Map.of(VALUE_ALIAS, address)).get(VALUE_ALIAS).intValue();
    }

    /** 读取一个 DINT 点。 */
    public long readLong(String address) {
        return readValues(Map.of(VALUE_ALIAS, address)).get(VALUE_ALIAS).longValue();
    }

    /** 读取一个 REAL 点。 */
    public BigDecimal readDecimal(String address) {
        return readValues(Map.of(VALUE_ALIAS, address)).get(VALUE_ALIAS);
    }

    /**
     * 批量读取 PLC 标签。
     *
     * @param addresses 业务别名与 PLC4X 标签的映射
     * @return 业务别名与数值的映射；BOOL 返回 0 或 1，其余数值统一转为 BigDecimal
     */
    public Map<String, BigDecimal> readValues(Map<String, String> addresses) {
        ensurePlcMode();
        ioLock.lock();
        try {
            PlcConnection activeConnection = connection();
            PlcReadRequest.Builder builder = activeConnection.readRequestBuilder();
            addresses.forEach(builder::addTagAddress);
            PlcReadResponse response = builder.build().execute()
                    .get(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS);
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            for (String alias : addresses.keySet()) {
                assertSuccess(alias, response.getResponseCode(alias));
                Object value = response.getObject(alias);
                values.put(alias, toDecimal(alias, value));
            }
            return values;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            invalidateConnection();
            throw communicationFailure("读取", exception);
        } finally {
            ioLock.unlock();
        }
    }

    /**
     * 批量写入 PLC 标签。调用方可继续批量回读并核对。
     *
     * @param values PLC4X 标签与待写入值的映射
     */
    public void writeValues(Map<String, Object> values) {
        ensurePlcMode();
        ioLock.lock();
        try {
            Map<String, TagValue> aliases = new LinkedHashMap<>();
            int index = 0;
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                aliases.put("value" + index++, new TagValue(entry.getKey(), entry.getValue()));
            }
            writeInternal(aliases);
        } finally {
            ioLock.unlock();
        }
    }

    private void writeInternal(Map<String, TagValue> values) {
        try {
            PlcConnection activeConnection = connection();
            PlcWriteRequest.Builder builder = activeConnection.writeRequestBuilder();
            values.forEach((alias, tagValue) ->
                    builder.addTagAddress(alias, tagValue.address(), tagValue.value()));
            PlcWriteResponse response = builder.build().execute()
                    .get(properties.getWriteTimeoutMs(), TimeUnit.MILLISECONDS);
            for (String alias : values.keySet()) {
                assertSuccess(alias, response.getResponseCode(alias));
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            invalidateConnection();
            throw communicationFailure("写入", exception);
        }
    }

    private PlcConnection connection() throws Exception {
        if (connection == null || !connection.isConnected()) {
            invalidateConnection();
            connection = driverManager.getConnection(connectionString());
        }
        return connection;
    }

    String connectionString() {
        return "s7://" + properties.getHost()
                + "?controller-type=S7_1200"
                + "&cotp.remote-rack=" + properties.getRack()
                + "&cotp.remote-slot=" + properties.getSlot()
                + "&read-timeout-ms=" + properties.getReadTimeoutMs()
                + "&cotp.connect-timeout-ms=" + properties.getConnectTimeoutMs();
    }

    private void assertSuccess(String alias, PlcResponseCode responseCode) {
        if (responseCode != PlcResponseCode.OK) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PLC 点位操作失败：" + alias + "，响应：" + responseCode);
        }
    }

    private BigDecimal toDecimal(String alias, Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "PLC 点位返回了非数值类型：" + alias);
    }

    private void sleepPollingInterval() {
        try {
            Thread.sleep(properties.getPollIntervalMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "等待 PLC 反馈被中断", exception);
        }
    }

    private void ensurePlcMode() {
        if (!properties.isPlcMode()) {
            throw new IllegalStateException("只有 PLC 模式可以执行 S7 通讯");
        }
    }

    private ResponseStatusException communicationFailure(String operation, Exception exception) {
        LOGGER.error("PLC {}失败，连接已失效", operation, exception);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "PLC " + operation + "失败，请检查连接和点位配置", exception);
    }

    private void invalidateConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception exception) {
            LOGGER.warn("关闭失效 PLC 连接失败", exception);
        } finally {
            connection = null;
        }
    }

    /** 应用关闭时主动释放 ISO-on-TCP 连接。 */
    @PreDestroy
    public void close() {
        ioLock.lock();
        try {
            invalidateConnection();
        } finally {
            ioLock.unlock();
        }
    }

    private record TagValue(String address, Object value) { }
}
