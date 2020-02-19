package cn.emay.socket.server;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

/**
 * 客户管理器
 * 
 * @author Frank
 *
 */
public class ClientManager {

	/**
	 * SESSION标识
	 */
	private static final AttributeKey<String> SESSION = AttributeKey.newInstance("CLIENT_SESSION");

	/**
	 * 每个IP最多几个链接[小于0不限制]
	 */
	private int maxConnectOneIp = -1;

	/**
	 * IP链接计数
	 */
	private Map<String, Integer> ipManage = new ConcurrentHashMap<String, Integer>();

	/**
	 * 所有的链接
	 */
	private Map<String, ChannelHandlerContext> contexts = new ConcurrentHashMap<String, ChannelHandlerContext>();

	/**
	 * 
	 * @param maxConnectOneIp
	 *            每个IP最多几个链接[小于0不限制]
	 */
	public ClientManager(int maxConnectOneIp) {
		this.maxConnectOneIp = maxConnectOneIp;
	}

	/**
	 * 清空并关闭所有链接
	 */
	public synchronized void removeAndCloseAll() {
		for (ChannelHandlerContext conetxt : contexts.values()) {
			conetxt.close();
		}
		contexts.clear();
		ipManage.clear();
	}
	
	/**
	 * 获取所有的session ID
	 * 
	 * @return
	 */
	public synchronized Set<String> getAllSessionId() {
		return contexts.keySet();
	}

	/**
	 * 获取链接
	 * 
	 * @param sessionId
	 *            sessionId
	 * @return
	 */
	public synchronized ChannelHandlerContext getChannelHandlerContext(String sessionId) {
		if (sessionId == null) {
			return null;
		}
		return contexts.get(sessionId);
	}

	/**
	 * 添加链接
	 */
	public synchronized boolean addChannelHandlerContext(ChannelHandlerContext ctx) {
		if (ctx == null) {
			return false;
		}
		if (maxConnectOneIp < 0) {
			return true;
		}
		String ip = ctx.channel().remoteAddress().toString().split(":")[0];
		if (!ipManage.containsKey(ip)) {
			ipManage.put(ip, 0);
		}
		int num = ipManage.get(ip);
		num++;
		if (num > maxConnectOneIp) {
			return false;
		}
		addSessionId(ctx);
		contexts.put(getSessionId(ctx), ctx);
		ipManage.put(ip, num);
		return true;
	}
	
	/**
	 * 清空并关闭链接
	 */
	public synchronized void removeAndClose(ChannelHandlerContext ctx) {
		removeChannelHandlerContext(ctx);
		ctx.close();
	}
	
	/**
	 * 清空并关闭链接
	 */
	public synchronized void removeAndClose(String sessionId) {
		if (sessionId == null) {
			return;
		}
		ChannelHandlerContext ctx = contexts.get(sessionId);
		removeAndClose(ctx);
	}

	/**
	 * 移除链接
	 */
	public synchronized void removeChannelHandlerContext(String sessionId) {
		if (sessionId == null) {
			return;
		}
		ChannelHandlerContext ctx = contexts.get(sessionId);
		removeChannelHandlerContext(ctx);
	}

	/**
	 * 移除链接
	 */
	public synchronized void removeChannelHandlerContext(ChannelHandlerContext ctx) {
		if (ctx == null) {
			return;
		}
		String sessionId = getSessionId(ctx);
		if(sessionId == null) {
			return;
		}
		String ip = ctx.channel().remoteAddress().toString().split(":")[0];
		contexts.remove(sessionId);
		if (maxConnectOneIp < 0) {
			return;
		}
		if (!ipManage.containsKey(ip)) {
			return;
		}
		int num = ipManage.get(ip);
		if (num <= 0) {
			return;
		}
		ipManage.put(ip, num - 1);
	}

	/**
	 * 获取链接的参数
	 */
	public <T> T getAttr(String sessionId, AttributeKey<T> attr) {
		if (sessionId == null) {
			return null;
		}
		ChannelHandlerContext ctx = contexts.get(sessionId);
		if (ctx == null) {
			return null;
		}
		return getAttr(ctx, attr);
	}

	/**
	 * 获取链接的参数
	 */
	public <T> T getAttr(ChannelHandlerContext ctx, AttributeKey<T> attr) {
		return ctx.channel().attr(attr).get();
	}

	/**
	 * 获取Session ID
	 */
	public String getSessionId(ChannelHandlerContext ctx) {
		return ctx.channel().attr(SESSION).get();
	}

	/**
	 * 放入Session Id
	 * 
	 * @param ctx
	 */
	private void addSessionId(ChannelHandlerContext ctx) {
		ctx.channel().attr(SESSION).setIfAbsent(UUID.randomUUID().toString().replace("-", ""));
	}

}
