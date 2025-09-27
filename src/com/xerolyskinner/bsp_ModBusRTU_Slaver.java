package com.xerolyskinner;  // 包声明

import java.util.Arrays;

public class bsp_ModBusRTU_Slaver {
    public byte ID;
    // 离散输出 Coils
    public boolean[] coil_output_pool = new boolean[256];
    // 离散输入 Discrete Inputs
    public boolean[] discrete_input_pool = new boolean[256];
    // 保持寄存器 Holding Registers
    public short[] holding_register_pool = new short[256];
    // 输入寄存器 Input Registers
    public short[] input_register_pool = new short[256];

    // 构造函数，传入 ID 参数
    public bsp_ModBusRTU_Slaver(byte id) {
        // 将传入的 id 值赋给静态变量 ID
        ID = id;
    }
    //  功能码枚举
    private static enum ModBusFunctionCode {
        READ_COILS(0x01), // 读取线圈
        READ_DISCRETE_INPUTS(0x02), // 读取离散输入
        READ_HOLDING_REGISTERS(0x03), // 读取保持寄存器
        READ_INPUT_REGISTERS(0x04), // 读取输入寄存器
        WRITE_SINGLE_COIL(0x05), // 写单个线圈
        WRITE_SINGLE_REGISTER(0x06), // 写单个寄存器
        WRITE_MULTIPLE_COILS(0x0F), // 写多个线圈
        WRITE_MULTIPLE_REGISTERS(0x10); // 写多个寄存器

        private final int code;

        // 构造函数
        ModBusFunctionCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static ModBusFunctionCode fromCode(int code) {
            for (ModBusFunctionCode func : values()) {
                if (func.getCode() == code) {
                    return func;
                }
            }
            return null;  // 没有匹配的功能码
        }
    }
    // 打印测试十六进制数据
    public void transmit(byte[] buff, int len) {
        // 输出缓冲区中的数据，每个字节按十六进制格式输出
        for (int i = 0; i < len; i++) {
            // 打印每个字节的十六进制表示，并确保有空格分隔
            System.out.printf("%02X ", buff[i]);
        }
        // 换行
        System.out.println();
    }
// 计算 Modbus CRC 校验值的函数
    public static int crc_modbus(byte[] buff, int len) {
        int crc = 0xFFFF;  // CRC 初始值
        for (int i = 0; i < len; i++) {
            crc ^= (buff[i] & 0xFF);  // 将当前字节与 CRC 进行异或
            for (int j = 8; j > 0; j--) {
                // 如果 CRC 最低位为 1，进行多项式除法
                if ((crc & 0x0001) == 1) {
                    crc >>= 1;  // 右移一位
                    crc ^= 0xA001;  // 多项式 0xA001
                } else {
                    crc >>= 1;  // 否则直接右移一位
                }
            }
        }
        return crc & 0xFFFF;  // 返回 CRC 结果，确保是 16 位
    }

// 根据接收到的数据判断功能码并进行处理
    public void receive(byte[] buff, int len) {
        if (buff == null || len <= 0 || len > buff.length) {
            System.out.println("Invalid data length.");
            return;
        }
        if (buff[0] != ID) {
            System.out.println("ID unpass.");
            return;
        }

        if (crc_modbus(buff, len-2)!=(((buff[len-1] & 0xFF) << 8) | (buff[len-2] & 0xFF))) {
            
            System.out.println("CRC error "+crc_modbus(buff, len-2)+" to "+(((buff[len-1] & 0xFF) << 8) | (buff[len-2] & 0xFF)));
            return;
        }

        int functionCode = buff[1] & 0xFF;  // 将 byte 转为 int，确保处理无符号数
        ModBusFunctionCode function = ModBusFunctionCode.fromCode(functionCode);
        if (function == null) {
            System.out.println("Unknown function code: " + functionCode);
            return;
        }

        switch (function) {
            //  完成READ_COILS基本测试
            case READ_COILS: {
                System.out.println("Processing READ_COILS command");
                // 处理读取线圈的逻辑
                // 解析请求中的起始地址和读取数量
                int startAddress = ((buff[2] & 0xFF) << 8) | (buff[3] & 0xFF); // 起始地址
                int coilCount = ((buff[4] & 0xFF) << 8) | (buff[5] & 0xFF); // 读取数量

                // 限制读取数量的最大值（Modbus协议中通常为2000个线圈，但也可以根据实际限制）
                if (coilCount > 2000) {
                    coilCount = 2000;
                }

                // 计算需要多少个字节来表示这些线圈的状态
                int byteCount = (coilCount + 7) / 8; // 每个字节最多存储8个线圈的状态

                // 创建响应数据的字节数组，首先返回功能码
                byte[] response = new byte[3 + byteCount]; // 最少需要功能码 + 字节数 + 数据

                // 功能码和字节数
                response[0] = buff[0]; // 设备ID
                response[1] = buff[1]; // 功能码
                response[2] = (byte) byteCount; // 返回的字节数

                // 填充线圈状态数据
                for (int i = 0; i < coilCount; i++) {
                    // 获取coil_output_pool中的线圈状态
                    boolean coilState = coil_output_pool[startAddress + i];

                    // 计算字节中的哪个位对应该线圈
                    int byteIndex = 3 + i / 8; // 起始数据偏移 3 + 每8个线圈占用1个字节
                    int bitIndex = (i % 8); // 当前线圈对应字节的位

                    // 设置该位的值
                    if (coilState) {
                        response[byteIndex] |= (1 << bitIndex); // 设置该位置1
                    } else {
                        response[byteIndex] &= ~(1 << bitIndex); // 清除该位置0
                    }
                }

                // 计算并附加 CRC
                int crc = crc_modbus(response, response.length);
                // 将 CRC 拆分成两个字节并附加到响应数据的末尾
                response = Arrays.copyOf(response, response.length + 2);
                response[response.length - 2] = (byte) (crc & 0xFF); // CRC 低字节
                response[response.length - 1] = (byte) ((crc >> 8) & 0xFF); // CRC 高字节

                transmit(response, response.length); // 发送生成的响应数据
            }
            break;
            //  完成READ_DISCRETE_INPUTS基本测试
            case READ_DISCRETE_INPUTS: {
                System.out.println("Processing READ_DISCRETE_INPUTS command");
                // 处理读取离散输入的逻辑
                // 解析请求中的起始地址和读取数量
                int inputStartAddress = ((buff[2] & 0xFF) << 8) | (buff[3] & 0xFF); // 起始地址
                int inputCount = ((buff[4] & 0xFF) << 8) | (buff[5] & 0xFF); // 读取数量

                // 限制读取数量的最大值（Modbus协议中通常为2000个离散输入，但也可以根据实际限制）
                if (inputCount > 2000) {
                    inputCount = 2000;
                }

                // 计算需要多少个字节来表示这些离散输入的状态
                int inputByteCount = (inputCount + 7) / 8; // 每个字节最多存储8个离散输入的状态

                // 创建响应数据的字节数组，首先返回功能码
                byte[] inputResponse = new byte[3 + inputByteCount]; // 最少需要功能码 + 字节数 + 数据

                // 功能码和字节数
                inputResponse[0] = buff[0]; // 设备ID
                inputResponse[1] = buff[1]; // 功能码
                inputResponse[2] = (byte) inputByteCount; // 返回的字节数

                // 填充离散输入状态数据
                for (int i = 0; i < inputCount; i++) {
                    // 获取discrete_input_pool中的离散输入状态
                    boolean inputState = discrete_input_pool[inputStartAddress + i];

                    // 计算字节中的哪个位对应该离散输入
                    int byteIndex = 3 + i / 8; // 起始数据偏移 3 + 每8个离散输入占用1个字节
                    int bitIndex = i % 8; // 当前离散输入对应字节的位

                    // 设置该位的值
                    if (inputState) {
                        inputResponse[byteIndex] |= (1 << bitIndex); // 设置该位置1
                    } else {
                        inputResponse[byteIndex] &= ~(1 << bitIndex); // 清除该位置0
                    }
                }

                // 计算并附加 CRC
                int crcInput = crc_modbus(inputResponse, inputResponse.length);
                // 将 CRC 拆分成两个字节并附加到响应数据的末尾
                inputResponse = Arrays.copyOf(inputResponse, inputResponse.length + 2);
                inputResponse[inputResponse.length - 2] = (byte) (crcInput & 0xFF); // CRC 低字节
                inputResponse[inputResponse.length - 1] = (byte) ((crcInput >> 8) & 0xFF); // CRC 高字节

                transmit(inputResponse, inputResponse.length); // 发送生成的响应数据
            }
            break;
            //  完成READ_HOLDING_REGISTERS基本测试
            case READ_HOLDING_REGISTERS: {
                System.out.println("Processing READ_HOLDING_REGISTERS command");
                // 处理读取保持寄存器的逻辑
                // 解析请求中的起始地址和读取数量
                int startAddress = ((buff[2] & 0xFF) << 8) | (buff[3] & 0xFF); // 起始地址
                int registerCount = ((buff[4] & 0xFF) << 8) | (buff[5] & 0xFF); // 读取数量

                // 限制读取数量的最大值（Modbus协议中通常为125个寄存器）
                if (registerCount > 125) {
                    registerCount = 125;
                }

                // 创建响应数据的字节数组
                byte[] response = new byte[3 + 2 * registerCount]; // 每个寄存器2个字节

                // 功能码和字节数
                response[0] = buff[0]; // 设备ID
                response[1] = buff[1]; // 功能码
                response[2] = (byte) (2 * registerCount); // 返回的字节数（每个寄存器2字节）

                // 填充寄存器数据
                for (int i = 0; i < registerCount; i++) {
                    // 获取holding_register_pool中的寄存器值
                    short registerValue = holding_register_pool[startAddress + i];

                    // 将寄存器值转换成字节并存储
                    response[3 + i * 2] = (byte) (registerValue >> 8); // 高字节
                    response[3 + i * 2 + 1] = (byte) (registerValue & 0xFF); // 低字节
                }

                // 计算并附加 CRC
                int crc = crc_modbus(response, response.length);
                response = Arrays.copyOf(response, response.length + 2);
                response[response.length - 2] = (byte) (crc & 0xFF); // CRC 低字节
                response[response.length - 1] = (byte) ((crc >> 8) & 0xFF); // CRC 高字节

                transmit(response, response.length); // 发送生成的响应数据
            }
            break;
            //  完成READ_INPUT_REGISTERS基本测试
            case READ_INPUT_REGISTERS: {
                System.out.println("Processing READ_INPUT_REGISTERS command");
                // 处理读取输入寄存器的逻辑
                // 解析请求中的起始地址和读取数量
                int startAddress = ((buff[2] & 0xFF) << 8) | (buff[3] & 0xFF); // 起始地址
                int registerCount = ((buff[4] & 0xFF) << 8) | (buff[5] & 0xFF); // 读取数量

                // 限制读取数量的最大值（Modbus协议中通常为125个寄存器）
                if (registerCount > 125) {
                    registerCount = 125;
                }

                // 创建响应数据的字节数组
                byte[] response = new byte[3 + 2 * registerCount]; // 每个寄存器2个字节

                // 功能码和字节数
                response[0] = buff[0]; // 设备ID
                response[1] = buff[1]; // 功能码
                response[2] = (byte) (2 * registerCount); // 返回的字节数（每个寄存器2字节）

                // 填充输入寄存器数据
                for (int i = 0; i < registerCount; i++) {
                    // 获取input_register_pool中的寄存器值
                    short registerValue = input_register_pool[startAddress + i];

                    // 将寄存器值转换成字节并存储
                    response[3 + i * 2] = (byte) (registerValue >> 8); // 高字节
                    response[3 + i * 2 + 1] = (byte) (registerValue & 0xFF); // 低字节
                }

                // 计算并附加 CRC
                int crc = crc_modbus(response, response.length);
                response = Arrays.copyOf(response, response.length + 2);
                response[response.length - 2] = (byte) (crc & 0xFF); // CRC 低字节
                response[response.length - 1] = (byte) ((crc >> 8) & 0xFF); // CRC 高字节

                transmit(response, response.length); // 发送生成的响应数据
            }
            break;
            //  完成WRITE_SINGLE_COIL基本测试
            case WRITE_SINGLE_COIL: {
                System.out.println("Processing WRITE_SINGLE_COIL command");
                // 处理写单个线圈的逻辑
                // 解析请求中的地址和线圈状态
                int coilAddress = ((buff[2] & 0xFF) << 8) | (buff[3] & 0xFF); // 地址
                int coilValue = ((buff[4] & 0xFF) << 8) | (buff[5] & 0xFF); // 状态

                // 设置线圈状态（0为关，0xFF00为开）
                boolean coilState = (coilValue == 0xFF00);

                // 修改coil_output_pool中的状态
                coil_output_pool[coilAddress] = coilState;

                // 创建响应数据，返回接收到的命令
                byte[] response = Arrays.copyOf(buff, len-2);

                // 计算并附加 CRC
                int crc = crc_modbus(response, response.length);
                response = Arrays.copyOf(response, response.length + 2);
                response[response.length - 2] = (byte) (crc & 0xFF); // CRC 低字节
                response[response.length - 1] = (byte) ((crc >> 8) & 0xFF); // CRC 高字节

                transmit(response, response.length); // 发送生成的响应数据
            }
            break;
            //  完成WRITE_SINGLE_REGISTER基本测试
            case WRITE_SINGLE_REGISTER: {
                System.out.println("Processing WRITE_SINGLE_REGISTER command");
                // 处理写单个寄存器的逻辑
                // 解析请求中的寄存器地址和寄存器值
                int registerAddress = ((buff[2] & 0xFF) << 8) | (buff[3] & 0xFF); // 地址
                int registerValue = ((buff[4] & 0xFF) << 8) | (buff[5] & 0xFF); // 寄存器值

                // 修改holding_register_pool中的寄存器值
                holding_register_pool[registerAddress] = (short) registerValue;

                // 创建响应数据，返回接收到的命令
                byte[] response = Arrays.copyOf(buff, len-2);

                // 计算并附加 CRC
                int crc = crc_modbus(response, response.length);
                response = Arrays.copyOf(response, response.length + 2);
                response[response.length - 2] = (byte) (crc & 0xFF); // CRC 低字节
                response[response.length - 1] = (byte) ((crc >> 8) & 0xFF); // CRC 高字节

                transmit(response, response.length); // 发送生成的响应数据
            }
            break;
            //  完成WRITE_MULTIPLE_COILS基本测试
            case WRITE_MULTIPLE_COILS: {
                System.out.println("Processing WRITE_MULTIPLE_COILS command");
                // 处理写多个线圈的逻辑
                // 解析请求中的起始地址、线圈数量和线圈数据
                int startAddress = ((buff[2] & 0xFF) << 8) | (buff[3] & 0xFF); // 起始地址
                int coilCount = ((buff[4] & 0xFF) << 8) | (buff[5] & 0xFF); // 线圈数量
                int byteCount = buff[6] & 0xFF; // 数据字节数

                // 填充线圈状态
                int byteIndex = 7; // 从第7个字节开始
                for (int i = 0; i < coilCount; i++) {
                    boolean coilState = (buff[byteIndex] & (1 << (i % 8))) != 0;
                    coil_output_pool[startAddress + i] = coilState;

                    if (i % 8 == 7) {
                        byteIndex++;
                    }
                }

                // 创建响应数据，返回接收到的命令
                byte[] response = Arrays.copyOf(buff, 6);

                // 计算并附加 CRC
                int crc = crc_modbus(response, response.length);
                response = Arrays.copyOf(response, response.length + 2);
                response[response.length - 2] = (byte) (crc & 0xFF); // CRC 低字节
                response[response.length - 1] = (byte) ((crc >> 8) & 0xFF); // CRC 高字节

                transmit(response, response.length); // 发送生成的响应数据
            }
            break;
            //  完成WRITE_MULTIPLE_REGISTERS基本测试
            case WRITE_MULTIPLE_REGISTERS: {
                System.out.println("Processing WRITE_MULTIPLE_REGISTERS command");
                // 处理写多个寄存器的逻辑
                // 解析请求中的起始地址、寄存器数量和寄存器数据
                int startAddress = ((buff[2] & 0xFF) << 8) | (buff[3] & 0xFF); // 起始地址
                int registerCount = ((buff[4] & 0xFF) << 8) | (buff[5] & 0xFF); // 寄存器数量
                int byteCount = buff[6] & 0xFF; // 数据字节数

                // 填充寄存器数据
                int byteIndex = 7; // 从第7个字节开始
                for (int i = 0; i < registerCount; i++) {
                    short registerValue = (short) (((buff[byteIndex] & 0xFF) << 8) | (buff[byteIndex + 1] & 0xFF));
                    holding_register_pool[startAddress + i] = registerValue;
                    byteIndex += 2;
                }

                // 创建响应数据，返回接收到的命令
                byte[] response = Arrays.copyOf(buff, 6);

                // 计算并附加 CRC
                int crc = crc_modbus(response, response.length);
                response = Arrays.copyOf(response, response.length + 2);
                response[response.length - 2] = (byte) (crc & 0xFF); // CRC 低字节
                response[response.length - 1] = (byte) ((crc >> 8) & 0xFF); // CRC 高字节

                transmit(response, response.length); // 发送生成的响应数据
            }
            break;

            default:
                System.out.println("Unhandled function code: " + functionCode);
                break;
        }
    }
}
