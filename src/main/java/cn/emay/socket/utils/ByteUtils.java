package cn.emay.socket.utils;

/**
 * 字节码转换工具
 * 
 * @author frank
 *
 */
public class ByteUtils {

	/**
	 * int转换为4个字节
	 * 
	 * @param value
	 * @return
	 */
	public static byte[] intToBytes4(int value) {
		byte mybytes[] = new byte[4];
		mybytes[3] = (byte) (0xff & value);
		mybytes[2] = (byte) ((0xff00 & value) >> 8);
		mybytes[1] = (byte) ((0xff0000 & value) >> 16);
		mybytes[0] = (byte) ((0xff000000 & value) >> 24);
		return mybytes;
	}

	/**
	 * 4个字节转换为int
	 * 
	 * @param bytes
	 * @return
	 */
	public static int bytes4ToInt(byte[] bytes) {
		return bytes4ToInt(bytes, 0);
	}

	/**
	 * 4个字节转换为int
	 * 
	 * @param bytes
	 * @param startIndex 从第几个字节开始
	 * @return
	 */
	public static int bytes4ToInt(byte[] bytes, int startIndex) {
		return (0xff & bytes[0 + startIndex]) << 24 | (0xff & bytes[1 + startIndex]) << 16 | (0xff & bytes[2 + startIndex]) << 8 | 0xff & bytes[3 + startIndex];
	}

	/**
	 * 合并数组
	 * 
	 * @param bytes1
	 * @param bytes2
	 * @return
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
