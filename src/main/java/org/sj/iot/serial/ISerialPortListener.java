package org.sj.iot.serial;

import java.util.function.Consumer;

/**
 * 串口设备监听
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-12-16
 */
public interface ISerialPortListener extends Consumer<byte[]> {
}
