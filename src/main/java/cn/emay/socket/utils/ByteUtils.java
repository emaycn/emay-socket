package cn.emay.socket.utils;

/**
 * 字节码转换工具
 *
 * @author frank
 */
public class ByteUtils {

    /**
     * int转换为4个字节
     *
     * @param value int
     * @return 字节数组
     */
    public static byte[] intToBytes4(int value) {
        byte[] myBytes = new byte[4];
        myBytes[3] = (byte) (0xff & value);
        myBytes[2] = (byte) ((0xff00 & value) >> 8);
        myBytes[1] = (byte) ((0xff0000 & value) >> 16);
        myBytes[0] = (byte) ((0xff000000 & value) >> 24);
        return myBytes;
    }

    /**
     * 4个字节转换为int
     *
     * @param bytes 字节数组
     * @return int
     */
    public static int bytes4ToInt(byte[] bytes) {
        return bytes4ToInt(bytes, 0);
    }

    /**
     * 4个字节转换为int
     *
     * @param bytes      字节数组
     * @param startIndex 从第几个字节开始
     * @return int
     */
    public static int bytes4ToInt(byte[] bytes, int startIndex) {
        return (0xff & bytes[startIndex]) << 24 | (0xff & bytes[1 + startIndex]) << 16 | (0xff & bytes[2 + startIndex]) << 8 | 0xff & bytes[3 + startIndex];
    }

    /**
     * 合并数组
     *
     * @param bytes1 数组1
     * @param bytes2 数组2
     * @return 合并后的数组
     */
    public static byte[] mergeBytes(byte[] bytes1, byte[] bytes2) {
        byte[] result = new byte[bytes1.length + bytes2.length];
        int index = 0;
        System.arraycopy(bytes1, 0, result, index, bytes1.length);
        index += bytes1.length;
        System.arraycopy(bytes2, 0, result, index, bytes2.length);
        return result;
    }

}
