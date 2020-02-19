package cn.emay.socket.server;

import java.net.InetSocketAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ResourceLeakDetector;

/**
 * 
 * @author frank
 *
 */
public abstract class SocketServer {

	private Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * 名字
	 */
	private String name;
	/**
	 * 是否启动
	 */
	private boolean isStart = false;

	/**
	 * boss 线程组
	 */
	private EventLoopGroup bossGroup;
	/**
	 * 业务线程组
	 */
	private EventLoopGroup workerGroup;
	/**
	 * 启动器
	 */
	private ServerBootstrap bootStrap;
	/**
	 * 数据通道
	 */
	private ChannelFuture channelFuture;
	/**
	 * 服务端地址
	 */
	private InetSocketAddress address;

	/**
	 * 读超时时间
	 */
	private int readerIdleTimeSeconds = 0;
	/**
	 * 写超时时间
	 */
	private int writerIdleTimeSeconds = 0;
	/**
	 * 读写超时时间
	 */
	private int allIdleTimeSeconds = 0;

	/**
	 * 客户端管理器
	 */
	private ClientManager clientManager;

	/**
	 * 
	 * @param name                  名字
	 * @param port                  绑定port
	 * @param handlerCreator        处理器创建器
	 * @param maxConnectOneIp       每个IP最大连接数
	 * @param readerIdleTimeSeconds 读超时时间[秒]
	 * @param writerIdleTimeSeconds 写超时时间[秒]
	 * @param allIdleTimeSeconds    全部超时时间[秒]
	 */
	public SocketServer(String name, int port, int maxConnectOneIp, int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {
		assertNull(name);
		this.address = new InetSocketAddress(port);
		this.name = name;
		if (readerIdleTimeSeconds > 0) {
			this.readerIdleTimeSeconds = readerIdleTimeSeconds;
		}
		if (writerIdleTimeSeconds > 0) {
			this.writerIdleTimeSeconds = writerIdleTimeSeconds;
		}
		if (allIdleTimeSeconds > 0) {
			this.allIdleTimeSeconds = allIdleTimeSeconds;
		}
		this.clientManager = new ClientManager(maxConnectOneIp);
		if (logger.isDebugEnabled()) {
			logger.debug("socket server[" + name + "] inited");
		}
		ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
	}

	/**
	 * 检测为空
	 * 
	 * @param o
	 */
	private void assertNull(Object o) {
		if (o == null) {
			throw new NullPointerException();
		}
	}

	/**
	 * 启动
	 * 
	 * @return
	 */
	public synchronized void startup() {
		if (isStart) {
			logger.info("socket server[" + name + "] has start , not need start again");
			return;
		}
		bootStrap = new ServerBootstrap();
		bootStrap.option(ChannelOption.SO_REUSEADDR, true);
		bossGroup = new NioEventLoopGroup();
		workerGroup = new NioEventLoopGroup();
		bootStrap.group(bossGroup, workerGroup);
		bootStrap.channel(NioServerSocketChannel.class);
		bootStrap.childHandler(new ChannelInitializer<NioSocketChannel>() {
			@Override
			protected void initChannel(NioSocketChannel ch) throws Exception {
				ch.pipeline().addLast("IdleStateHandler", new IdleStateHandler(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds));
				ch.pipeline().addLast("ConnectHandler", new ConnectHandler());
				ch.pipeline().addLast("EncodeHandler", new EncodeHandler());
				ch.pipeline().addLast("DecodeHandler", new DecodeHandler());
				ch.pipeline().addLast("BusinessHandler", new BusinessHandler());
			}
		});
		try {
			channelFuture = bootStrap.bind(address).sync();
		} catch (InterruptedException e) {
			throw new IllegalArgumentException(e);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("socket server[" + name + "] start success");
		}
		isStart = true;
	}

	/**
	 * 关闭
	 */
	public synchronized void shutdown() {
		if (!isStart) {
			logger.info("socket server[" + name + "] has stop, not need stop again");
			return;
		}
		try {
			channelFuture.channel().close().sync();
		} catch (InterruptedException e) {
			throw new IllegalArgumentException(e);
		}
		channelFuture.cancel(true);
		bossGroup.shutdownGracefully();
		workerGroup.shutdownGracefully();
		clientManager.removeAndCloseAll();
		if (logger.isDebugEnabled()) {
			logger.debug("socket server[" + name + "] stoped");
		}
		isStart = false;
	}

	/**
	 * 向客户端发送信息
	 * 
	 * @param ctx
	 * @param message
	 * @param wait    是否等待消息真实发送出去
	 * @return
	 */
	public synchronized boolean sendMessage(ChannelHandlerContext ctx, Object message, boolean wait) {
		if (!isStart) {
			logger.info("socket server[" + name + "]  is stopped");
			return false;
		}
		if (message == null) {
			logger.error("message is null");
			return false;
		}
		if (!ctx.channel().isActive()) {
			logger.error("socket client[" + name + "] session [ " + clientManager.getSessionId(ctx) + " ] is stopped");
			return false;
		}
		ChannelFuture result0 = ctx.channel().writeAndFlush(message);
		if (wait) {
			result0 = result0.awaitUninterruptibly();
			if (result0.isSuccess()) {
				if (logger.isDebugEnabled()) {
					logger.debug("socket server[" + name + "]  send message ok");
				}
				return true;
			} else {
				logger.error("socket server[" + name + "]  send message error", result0.cause());
				return false;
			}
		} else {
			return true;
		}
	}

	/**
	 * 向客户端发送信息
	 * 
	 * @param sessionId
	 * @param message
	 * @param wait      是否等待消息真实发送出去
	 * @return
	 */
	public synchronized boolean sendMessage(String sessionId, Object message, boolean wait) {
		if (!isStart) {
			logger.info("socket server[" + name + "]  is stopped");
			return false;
		}
		if (message == null) {
			logger.error("message is null");
			return false;
		}
		ChannelHandlerContext ctx = clientManager.getChannelHandlerContext(sessionId);
		if (ctx == null) {
			logger.error("socket server sessionId[" + sessionId + "]  is unkonw");
			return false;
		}
		return sendMessage(ctx, message, wait);
	}

	/**
	 * 获取server名字
	 * 
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * 是否启动
	 * 
	 * @return
	 */
	public boolean isStart() {
		return isStart;
	}

	/**
	 * 获取客户管理器
	 * 
	 * @return
	 */
	public ClientManager getClientManager() {
		return clientManager;
	}

	/**
	 * 处理链接信息
	 */
	protected abstract void connectHandle(ChannelHandlerContext ctx, String address) throws Exception;

	/**
	 * 处理断开链接信息
	 */
	protected abstract void closedHandle(ChannelHandlerContext ctx, String address) throws Exception;

	/**
	 * 链接异常处理
	 */
	protected abstract void exceptionHandle(ChannelHandlerContext ctx, String address, Throwable cause) throws Exception;

	/**
	 * 读空闲链接处理
	 */
	protected abstract void readIdleHandle(ChannelHandlerContext ctx, String address) throws Exception;

	/**
	 * 全部空闲链接处理
	 */
	protected abstract void allIdleHandle(ChannelHandlerContext ctx, String address) throws Exception;

	/**
	 * 写空闲链接处理
	 */
	protected abstract void writeIdleHandle(ChannelHandlerContext ctx, String address) throws Exception;

	/**
	 * 编码
	 */
	protected abstract byte[] encode(ChannelHandlerContext ctx, Object msg) throws Exception;

	/**
	 * 解码
	 */
	protected abstract List<Object> decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception;

	/**
	 * 数据处理
	 */
	protected abstract void businessLogic(ChannelHandlerContext ctx, Object msg) throws Exception;

	/**
	 * 
	 * @author frank
	 *
	 */
	class EncodeHandler extends MessageToByteEncoder<Object> {

		@Override
		protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
			byte[] bytes = SocketServer.this.encode(ctx, msg);
			if (bytes != null) {
				out.writeBytes(bytes);
			}
		}
	}

	/**
	 * 
	 * @author frank
	 *
	 */
	class DecodeHandler extends ByteToMessageDecoder {

		@Override
		protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
			List<Object> objs = SocketServer.this.decode(ctx, in);
			if (objs != null) {
				out.addAll(objs);
			}
		}
	}

	/**
	 * 
	 * @author frank
	 *
	 */
	class ConnectHandler extends ChannelInboundHandlerAdapter {

		/**
		 * 链接
		 */
		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			String address = ctx.channel().remoteAddress().toString();
			boolean enable = clientManager.addChannelHandlerContext(ctx);
			if (!enable) {
				ctx.close().sync();
				logger.error(" more connect from address : " + address + " , close it");
				return;
			}
			SocketServer.this.connectHandle(ctx, address);
			super.channelActive(ctx);
		}

		/**
		 * 断开连接
		 */
		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			String address = ctx.channel().remoteAddress().toString();
			SocketServer.this.closedHandle(ctx, address);
			clientManager.removeAndClose(ctx);
			super.channelInactive(ctx);
		}

		/**
		 * 链接异常
		 */
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			String address = ctx.channel().remoteAddress().toString();
			SocketServer.this.exceptionHandle(ctx, address, cause);
			super.exceptionCaught(ctx, cause);
		}

		/**
		 * 空闲处理
		 */
		@Override
		public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
			if (evt instanceof IdleStateEvent) {
				String address = ctx.channel().remoteAddress().toString();
				IdleStateEvent event = (IdleStateEvent) evt;
				switch (event.state()) {
				case WRITER_IDLE:
					SocketServer.this.writeIdleHandle(ctx, address);
					break;
				case READER_IDLE:
					SocketServer.this.readIdleHandle(ctx, address);
					break;
				case ALL_IDLE:
					SocketServer.this.allIdleHandle(ctx, address);
					break;
				default:
					break;
				}
			}
			super.userEventTriggered(ctx, evt);
		}

	}

	/**
	 * 
	 * @author frank
	 *
	 */
	class BusinessHandler extends SimpleChannelInboundHandler<Object> {

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
			SocketServer.this.businessLogic(ctx, msg);
		}

	}

}