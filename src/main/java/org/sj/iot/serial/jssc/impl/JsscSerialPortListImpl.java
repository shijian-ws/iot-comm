package org.sj.iot.serial.jssc.impl;

import jssc.SerialNativeInterface;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import org.sj.iot.serial.ISerialPort;
import org.sj.iot.serial.ISerialPortList;
import org.sj.iot.serial.ISerialPortListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于JSSC实现串口列表
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-12-16
 */
public class JsscSerialPortListImpl implements ISerialPortList {
    private static final SerialNativeInterface snInterface = new SerialNativeInterface();
    private static final Map<String, ISerialPort> cacheSerialPort = new HashMap<>();

    static {
        String[] serialPortNames = snInterface.getSerialPortNames();
        if (serialPortNames != null && serialPortNames.length > 0) {
            for (String name : serialPortNames) {
                cacheSerialPort.put(name, new JsscSerialPortImpl(name));
            }
        }
    }

    @Override
    public Map<String, ISerialPort> listSerialPort() {
        return new HashMap<>(cacheSerialPort);
    }

    private static class JsscSerialPortImpl implements ISerialPort {
        private String name; // 串口设备名称
        private SerialPort serialPort; // 串口设备
        private Map<String, ISerialPortListener> cacheListener = new ConcurrentHashMap<>();

        public JsscSerialPortImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void open(int timeout) {
            open(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE, timeout);
        }

        @Override
        public void open(int b, int d, int s, int p, int timeout) {
            serialPort = new SerialPort(name);
            try {
                serialPort.openPort();
                boolean flag = serialPort.setParams(b, d, s, p);
                if (!flag) {
                    throw new IllegalArgumentException(String.format("错误的串口参数: %s, %s, %s, %s", b, d, s, p));
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format("打开串口设备[%s]失败: %s", name, e.getMessage()));
            }
            try {
                serialPort.addEventListener(this::listener);
            } catch (Exception e) {
                throw new RuntimeException(String.format("添加串口设备[%s]状态监听器失败: %s", name, e.getMessage()));
            }
        }

        /*private ByteBuffer inFromSerial = ByteBuffer.allocate(128);
        private CharBuffer outToMessage = CharBuffer.allocate(128);
        private CharsetDecoder bytesToStrings = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith("\u2e2e");*/

        private void listener(SerialPortEvent event) {
            if (!event.isRXCHAR()) {
                return;
            }
            try {
                byte[] result = serialPort.readBytes(event.getEventValue());
                cacheListener.values().parallelStream().filter(Objects::nonNull).forEach(listener -> {
                    try {
                        listener.accept(result.clone());
                    } catch (Exception e) {
                        throw new RuntimeException(String.format("处理串口设备[%s]数据失败: %s", name, e.getMessage()));
                    }
                });
                // 直接使用Arduion IDE源码 https://github.com/arduino/Arduino/blob/master/arduino-core/src/processing/app/Serial.java
                /*int next = 0;
                while (next < result.length) {
                    while (next < result.length && outToMessage.hasRemaining()) {
                        int spaceInIn = inFromSerial.remaining();
                        int copyNow = result.length - next < spaceInIn ? result.length - next : spaceInIn;
                        inFromSerial.put(result, next, copyNow);
                        next += copyNow;
                        inFromSerial.flip();
                        bytesToStrings.decode(inFromSerial, outToMessage, false);
                        inFromSerial.compact();
                    }
                    outToMessage.flip();
                    if (outToMessage.hasRemaining()) {
                        char[] chars = new char[outToMessage.remaining()];
                        outToMessage.get(chars);
                        System.out.println(new String(chars));
                    }
                    outToMessage.clear();
                }*/
            } catch (Exception e) {
                throw new RuntimeException(String.format("读取串口设备[%s]数据失败: %s", name, e.getMessage()));
            }
        }

        @Override
        public boolean isOpen() {
            return serialPort != null && serialPort.isOpened();
        }

        @Override
        public boolean isClose() {
            return serialPort != null && !serialPort.isOpened();
        }

        @Override
        public void sent(byte[] data) {
            if (!isOpen()) {
                throw new IllegalStateException("未打开串口设备通信通道!");
            }
            try {
                serialPort.writeBytes(data);
            } catch (Exception e) {
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
                try {
                    serialPort.closePort();
                } catch (Exception e) {
                    throw new RuntimeException(String.format("关闭串口设备[%s]通讯通道失败: %s", name, e.getMessage()));
                }
                this.name = null;
            }
        }
    }
}
