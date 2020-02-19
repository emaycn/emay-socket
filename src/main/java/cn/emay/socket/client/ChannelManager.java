package cn.emay.socket.client;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;

/**
 * 链接管理器
 * 
 * @author Frank
 *
 */
public class ChannelManager {

	/**
	 * 随机获取通道队列
	 */
	private Queue<ChannelId> channelQueue = new ConcurrentLinkedQueue<ChannelId>();

	/**
	 * 所有链接
	 */
	private Map<ChannelId, Channel> channels = new ConcurrentHashMap<ChannelId, Channel>();

	/**
	 * 获取所有链接的ID
	 * 
	 * @return
	 */
	public synchronized Set<ChannelId> getAllChannelId() {
		return channels.keySet();
	}

	/**
	 * 添加一个连接
	 * 
	 * @param channelId 链接ID
	 * @param channel   链接
	 */
	protected synchronized void addChannel(ChannelId channelId, Channel channel) {
		if (channel == null) {
			return;
		}
		channels.put(channelId, channel);
	}

	/**
	 * 获取一个链接
	 * 
	 * @param channelId 链接ID
	 * @return
	 */
	protected synchronized Channel getChannel(ChannelId channelId) {
		if (channelId == null) {
			return null;
		}
		return channels.get(channelId);
	}

	/**
	 * 随机选择数据通道发送
	 */
	public synchronized ChannelId randomChannel() {
		ChannelId id = channelQueue.poll();
		if (id == null) {
			if (channels.size() == 0) {
				return null;
			}
			channelQueue.addAll(channels.keySet());
			id = channelQueue.poll();
		}
		if (channels.containsKey(id)) {
			return id;
		} else {
			return null;
		}
	}

	/**
	 * 删除并关闭一个链接
	 * 
	 * @param channelId 链接ID
	 */
	public synchronized void removeAndCloseChannel(ChannelId channelId) {
		if (channelId == null) {
			return;
		}
		Channel channel = channels.get(channelId);
		if (channel == null) {
			return;
		}
		channel.close().addListener(new ChannelFutureListener() {
			public void operationComplete(ChannelFuture future) {
				// future.isDone();
			}
		});
		channels.remove(channelId);
	}

	/**
	 * 删除并关闭所有链接
	 */
	protected synchronized void removeAndCloseAll() {
		for (Channel channel : channels.values()) {
			channel.close().addListener(new ChannelFutureListener() {
				public void operationComplete(ChannelFuture future) {
					// future.isDone();
				}
			});
		}
		channels.clear();
	}

}
