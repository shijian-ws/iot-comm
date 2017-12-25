package org.sj.iot.serial;

import java.util.Map;

/**
 * 串口设备列表
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-12-16
 */
public interface ISerialPortList {
    /**
     * 串口设备列表
     */
    Map<String, ISerialPort> listSerialPort();
}
