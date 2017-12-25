package org.sj.iot.serial;

import java.io.Closeable;
import java.util.Map;

/**
 * 串口设备描述
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-12-16
 */
public interface ISerialPort extends Closeable {
    /**
     * 串口设备名称
     */
    String getName();

    /**
     * 打开串口设备通道, BAUDRATE:9600, DATABITS: 8, STOPBITS: 1, PARITY: none
     *
     * @param timeout 超时时间, 单位: 秒
     */
    void open(int timeout);

    /**
     * 打开串口设备通道, 并自定义: 比特率, 数据位, 停止位, 奇偶检验位, 超时
     */
    void open(int b, int d, int s, int p, int timeout);

    /**
     * 串口设备通道是否已被打开
     */
    boolean isOpen();

    /**
     * 串口设备通道是否已被关闭
     */
    boolean isClose();

    /**
     * 发送数据
     */
    void sent(byte[] data);

    /**
     * 绑定监听器
     */
    void addListener(String id, ISerialPortListener listener);

    /**
     * 获取一个监听器, 如果没有则返回null 否则返回监听器列表第一个
     */
    ISerialPortListener getListener(String id);

    /**
     * 获取当前监听器列表
     */
    Map<String, ISerialPortListener> listListener();

    /**
     * 移除监听器
     */
    ISerialPortListener removeListener(String id);
}
