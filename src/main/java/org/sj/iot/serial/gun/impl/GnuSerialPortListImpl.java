package org.sj.iot.serial.gun.impl;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import org.sj.iot.serial.ISerialPort;
import org.sj.iot.serial.ISerialPortList;
import org.sj.iot.serial.ISerialPortListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于gnu实现串口设备列表
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-12-16
 */
public class GnuSerialPortListImpl implements ISerialPortList {
    private static final Map<String, ISerialPort> cacheSerialPort = new HashMap<>();

    static {
        Enumeration<CommPortIdentifier> identifiers = CommPortIdentifier.getPortIdentifiers();
        for (; identifiers.hasMoreElements(); ) {
            CommPortIdentifier identifier = identifiers.nextElement();
            if (identifier.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                // 缓存串口设备
                cacheSerialPort.put(identifier.getName(), new GnuSerialPortImpl(identifier));
            }
        }
    }

    @Override
    public Map<String, ISerialPort> listSerialPort() {
        return new HashMap<>(cacheSerialPort);
    }

    /**
     * 串口设备对象
     */
    private static class GnuSerialPortImpl implements ISerialPort {
        private final CommPortIdentifier identifier; // 串口设备描述
        private SerialPort serialPort; // 串口设备对象
        private String name; // 串口设备名称
        private OutputStream sent; // 串口设备写入数据流
        private InputStream receive; // 串口设备读取数据流
        private Map<String, ISerialPortListener> cacheListener = new ConcurrentHashMap<>();
        private boolean isClose; // 是否被关闭

        private GnuSerialPortImpl(CommPortIdentifier identifier) {
            if (identifier == null) {
                throw new IllegalArgumentException(String.format("未找到串口设备[%s]", name));
            }
            this.name = identifier.getName();
            this.identifier = identifier;
        }

        @Override
        public String getName() {
            return identifier.getName();
        }

        @Override
        public void open(int timeout) {
            open(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE, timeout);
        }

        @Override
        public void open(int b, int d, int s, int p, int timeout) {
            if (timeout < 0) {
                timeout = 1000;
            } else if (timeout > 60000) {
                timeout = 60000;
            }
            try {
                serialPort = (SerialPort) identifier.open(name, timeout / 1000);
            } catch (Exception e) {
                throw new RuntimeException(String.format("打开串口设备[%s]失败: %s", name, e.getMessage()));
            }
            try {
                sent = serialPort.getOutputStream();
            } catch (Exception e) {
                throw new RuntimeException(String.format("获取串口设备[%s]通信数据写入流失败: %s", name, e.getMessage()));
            }
            try {
                receive = serialPort.getInputStream();
            } catch (Exception e) {
                throw new RuntimeException(String.format("获取串口设备[%s]通信数据读取流失败: %s", name, e.getMessage()));
            }
            try {
                // 设置串口的读写参数, 比特率, 数据位, 停止位, 奇偶检验位
                serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                isClose = false;
            } catch (Exception e) {
                throw new RuntimeException(String.format("设置串口设备[%s]参数失败: %s", name, e.getMessage()));
            }
            try {
                serialPort.addEventListener(this::listener);
            } catch (Exception e) {
                throw new RuntimeException(String.format("添加串口设备[%s]状态监听器失败: %s", name, e.getMessage()));
            }
            serialPort.notifyOnDataAvailable(true); // 开启数据通知监听器
        }

        public void listener(SerialPortEvent event) {
            if (event.getEventType() != SerialPortEvent.DATA_AVAILABLE) {
                return;
            }
            try {
                if (receive.available() == 0) {
                    return;
                }
                if (!cacheListener.isEmpty()) {
                    byte[] buffer = new byte[2048];
                    try (ByteArrayOutputStream tmp = new ByteArrayOutputStream()) {
                        for (int len; (len = receive.read(buffer)) > 0; ) {
                            tmp.write(buffer, 0, len);
                        }
                        byte[] result = tmp.toByteArray();
                        cacheListener.values().parallelStream().filter(Objects::nonNull).forEach(listener -> {
                            try {
                                listener.accept(result.clone());
                            } catch (Exception e) {
                                throw new RuntimeException(String.format("处理串口设备[%s]数据失败: %s", name, e.getMessage()));
                            }
                        });
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format("读取串口设备[%s]发送数据失败: %s", name, e.getMessage()));
            }
        }

        @Override
        public boolean isOpen() {
            return serialPort != null;
        }

        @Override
        public boolean isClose() {
            return isClose;
        }

        @Override
        public void sent(byte[] data) {
            if (!isOpen()) {
                throw new IllegalStateException("未打开串口设备通信通道!");
            }
            try {
                sent.write(data);
                sent.flush();
            } catch (IOException e) {
                throw new RuntimeException(String.format("串口设备[%s]通讯通道写入数据失败: %s", name, e.getMessage()));
            }
        }

        @Override
        public void addListener(String id, ISerialPortListener listener) {
            cacheListener.put(id, listener);
        }

        @Override
        public ISerialPortListener getListener(String id) {
            return cacheListener.get(id);
        }

        @Override
        public Map<String, ISerialPortListener> listListener() {
            return new HashMap<>(cacheListener);
        }

        @Override
        public ISerialPortListener removeListener(String id) {
            return cacheListener.remove(id);
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                this.name = null;
                this.sent.close();
                this.receive.close();
                serialPort.close();
                isClose = true;
            }
        }
    }
}
