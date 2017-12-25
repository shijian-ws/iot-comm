package org.sj.iot;

import org.sj.iot.serial.ISerialPort;
import org.sj.iot.serial.SerialFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 串口通信工具
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-10-27
 */
public class CommUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommUtil.class);

    private static final Random random = new Random();

    private static String getRGB() {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            buffer.append(Integer.toHexString(random.nextInt(16)));
        }
        return buffer.toString();
    }

    public static void main(String[] args) throws Exception {
        String[] single = new String[1];
        ssdp("ssdp\n".getBytes(), "ssdp\n0\n".getBytes(), result -> single[0] = new String(result));
        String name = single[0];
        if (name == null) {
            throw new IllegalArgumentException("SSDP未响应");
        }
        while (true) {
            byte[] result = send(name, String.format("color\n%s", getRGB()).getBytes(), 3000);
            if (result == null) {
                System.err.println("未收到响应!");
            } else {
                System.err.println(new String(result));
            }
            Thread.sleep(1000);
        }
    }

    /**
     * 串口设备发现服务, 同步阻塞
     *
     * @param data 发送数据
     * @param ack  期望串口设备应答数据
     * @return 如果串口应答数据与期望应答数据匹配则返回串口设备名否则返回null
     */
    public static String ssdp(byte[] data, byte[] ack) {
        String[] single = new String[1];
        ssdp(data, ack, name -> single[0] = name);
        return single[0];
    }

    private static final Map<String, ISerialPort> serialPortMap = SerialFactory.createSerialPortList().listSerialPort();

    /**
     * 异步串口设备发现服务
     *
     * @param data 发送数据
     * @param ack  期望串口设备应答数据
     * @param func 接收串口名称函数, 如果串口应答数据与期望应答数据匹配则将调用, 否则将被跳过
     */
    public static void ssdp(byte[] data, byte[] ack, Consumer<String> func) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("向串口设备发送数据不能为空!");
        }
        if (ack == null || ack.length == 0) {
            throw new IllegalArgumentException("期望串口设备应答数据不能为空!");
        }
        if (func == null) {
            throw new IllegalArgumentException("接收串口设备名称函数不能为空!");
        }
        if (serialPortMap.isEmpty()) {
            throw new IllegalArgumentException("本地无串口设备!");
        }
        CountDownLatch counter = new CountDownLatch(1);// 应答检测函数
        Map<String, ISerialPort> cacheListener = new HashMap<>();
        Map<String, AtomicInteger> cache = new HashMap<>();
        // 生成监听对象
        for (Entry<String, ISerialPort> entry : serialPortMap.entrySet()) {
            String name = entry.getKey(); // 串口设备名称
            ISerialPort serialPort = entry.getValue();
            // 添加监听器
            String id = UUID.randomUUID().toString();
            serialPort.addListener(id, result -> {
                if (Arrays.equals(ack, result)) {
                    LOGGER.debug("SSDP发现服务搜索完成:{}", name);
                    func.accept(name);
                    counter.countDown(); // 通知已找到
                    return;
                }
                LOGGER.error("SSDP发现服务搜索应答{}, 期望应答:{}", new String(result), new String(ack));
            });
            if (!serialPort.isOpen()) {
                serialPort.open(3); // 打开通道
            }
            cacheListener.put(id, serialPort);
            cache.put(name, new AtomicInteger(0));
        }
        if (!cache.isEmpty()) {
            Thread thread = new Thread(() -> pollSSDP(data, counter, cacheListener, cache));
            thread.start(); // 启动进行发现
            try {
                counter.await(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.error("SSDP发现服务搜索超时: {}", e.getMessage());
            }
            try {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            } catch (Exception e) {
                LOGGER.warn("打断SSDP循环发现服务搜索: {}", e.getMessage());
            }
        }
    }

    /**
     * 轮询, 发现服务
     */
    private static void pollSSDP(byte[] data, CountDownLatch counter, Map<String, ISerialPort> cacheListener, Map<String, AtomicInteger> cache) {
        while (counter.getCount() != 0) {
            // 进行发现服务
            for (Entry<String, AtomicInteger> entry : cache.entrySet()) {
                String name = entry.getKey(); // 已经发送串口设备名
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("SSDP发现服务串口设备{}开始第{}次寻找!", name, entry.getValue().incrementAndGet());
                }
            }
            cacheListener.entrySet().parallelStream().forEach(entry -> entry.getValue().sent(data));
            try {
                Thread.sleep(1500);
            } catch (Exception e) {
                cacheListener.entrySet().parallelStream().forEach(entry -> entry.getValue().removeListener(entry.getKey()));
            }
        }
    }

    /**
     * 发送消息并同步阻塞接收响应
     *
     * @param name
     * @param data
     * @param timeout 超时时间, 单位: 毫秒
     * @return
     */
    public static byte[] send(String name, byte[] data, long timeout) {
        byte[][] single = new byte[1][];
        CountDownLatch counter = new CountDownLatch(1);
        send(name, data, result -> {
            single[0] = result;
            counter.countDown(); // 结束同步阻塞
        });
        if (timeout < 0) {
            timeout = 1000;
        } else if (timeout > 60000) {
            timeout = 60000;
        }
        try {
            counter.await(timeout, TimeUnit.MILLISECONDS); // 开始阻塞等待结束阻塞或超时
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("等待串口设备[{}]响应超时: {}", name, e.getMessage());
            }
        }
        return single[0];
    }

    /**
     * 发送消息
     */
    public static void send(String name, byte[] data) {
        send(name, data, null);
    }

    /**
     * 发送消息并接受响应
     *
     * @param name
     * @param data
     * @param callback
     */
    public static void send(String name, byte[] data, Consumer<byte[]> callback) {
        if (name == null) {
            throw new IllegalArgumentException("串口设备名称不能为空!");
        }
        ISerialPort serialPort = serialPortMap.get(name);
        if (serialPort == null) {
            throw new IllegalArgumentException(String.format("未找到%s串口设备!", name));
        }
        CountDownLatch counter = new CountDownLatch(1);
        String id = UUID.randomUUID().toString();
        Thread thread = new Thread(() -> pollSentCallBack(data, counter, id, serialPort));
        // 添加监听
        serialPort.addListener(id, result -> {
            serialPort.removeListener(id); // 响应移除监听
            if (callback != null) {
                callback.accept(result); // 回调
            }
            counter.countDown(); // 通知结束轮询
            try {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            } catch (Exception e) {
                LOGGER.warn("打断数据循环发送: {}", e.getMessage());
            }
        });
        if (!serialPort.isOpen()) {
            serialPort.open(3); // 打开串口设备
        }
        thread.start(); // 启动进行数据发送
    }

    /**
     * 轮询, 数据响应
     */
    private static void pollSentCallBack(byte[] data, CountDownLatch counter, String id, ISerialPort serialPort) {
        AtomicInteger count = new AtomicInteger(0);
        while (counter.getCount() != 0) {
            // 进行发送数据
            String name = serialPort.getName(); // 已经发送串口设备名
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("串口设备{}开始第{}次发送数据: {}", name, count.incrementAndGet(), id);
            }
            serialPort.sent(data);
            try {
                Thread.sleep(1500);
            } catch (Exception e) {
                serialPort.removeListener(id);
            }
        }
    }
}
