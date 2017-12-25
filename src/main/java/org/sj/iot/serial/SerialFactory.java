package org.sj.iot.serial;

import org.sj.iot.serial.jssc.impl.JsscSerialPortListImpl;

/**
 * 串口操作
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-12-16
 */
public abstract class SerialFactory {
    /**
     * 获取串口设备列表对象
     */
    public static ISerialPortList createSerialPortList() {
        // return new GnuSerialPortListImpl();
        return new JsscSerialPortListImpl();
    }
}
