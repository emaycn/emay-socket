package cn.emay.socket.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Socket客户端
 *
 * @author Frank
 */
public abstract class SocketClient {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 名字
     */
    private final String name;

    /**
     * 是否启动
     */
    private boolean isStart = false;

    /**
     * 业务线程组
     */
    private EventLoopGroup workerGroup;

    /**
     * 启动器
     */
    private Bootstrap bootStrap;

    /**
     * 服务端地址
     */
    private final InetSocketAddress address;

    /**
     * 读超时时间[默认30秒]
     */
    private int readerIdleTimeSeconds = 30;

    /**
     * 写超时时间[默认30秒]
     */
    private int writerIdleTimeSeconds = 30;

    /**
     * 全部超时时间[默认30秒]
     */
    private int allIdleTimeSeconds = 30;

    /**
     * 链接等待时间秒[默认30秒]
     */
    private int connectWaitTime = 30000;

    /**
     * 数据通道(连接)管理器
     */
    private final ChannelManager manager;

    /**
     * @param name                  名字
     * @param address               服务端地址
     * @param connectWaitTime       链接等待时间[秒]
     * @param readerIdleTimeSeconds 读超时时间[秒]
     * @param writerIdleTimeSeconds 写超时时间[秒]
     * @param allIdleTimeSeconds    全部超时时间[秒]
     */
    public SocketClient(String name, String address, int connectWaitTime, int readerIdleTimeSeconds, int writerIdleTimeSeconds,
                        int allIdleTimeSeconds) {
        assertNull(address);
        assertNull(name);
        String[] addrs = address.split(":");
        if (addrs.length != 2) {
            throw new IllegalArgumentException("address is not be ip:port  ");
        }
        this.address = new InetSocketAddress(addrs[0], Integer.parseInt(addrs[1]));
        this.name = name;
        this.connectWaitTime = connectWaitTime > 0 ? connectWaitTime * 1000 : this.connectWaitTime;
        this.readerIdleTimeSeconds = readerIdleTimeSeconds > 0 ? readerIdleTimeSeconds : this.readerIdleTimeSeconds;
        this.writerIdleTimeSeconds = writerIdleTimeSeconds > 0 ? writerIdleTimeSeconds : this.writerIdleTimeSeconds;
        this.allIdleTimeSeconds = allIdleTimeSeconds > 0 ? allIdleTimeSeconds : this.allIdleTimeSeconds;
        this.manager = new ChannelManager();
        if (logger.isDebugEnabled()) {
            logger.debug("socket client[" + name + "] inited");
        }
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
    }

    /**
     * 检测为空
     *
     * @param o 对象
     */
    private void assertNull(String o) {
        if (o == null || o.trim().length() == 0) {
            throw new NullPointerException();
        }
    }

    /**
     * 启动
     */
    public synchronized void startup() {
        if (isStart) {
            logger.info("socket client[" + name + "] has start , not need start again");
            return;
        }
        bootStrap = new Bootstrap();
        bootStrap.remoteAddress(address);
        workerGroup = new NioEventLoopGroup();
        bootStrap.group(workerGroup);
        bootStrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectWaitTime);
        bootStrap.channel(NioSocketChannel.class);
        bootStrap.handler(new ChannelInitializer<NioSocketChannel>() {
            @Override
            protected void initChannel(NioSocketChannel ch) {
                ch.pipeline().addLast("IdleStateHandler", new IdleStateHandler(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds));
                ch.pipeline().addLast("ConnectHandler", new ConnectHandler());
                ch.pipeline().addLast("EncodeHandler", new EncodeHandler());
                ch.pipeline().addLast("DecodeHandler", new DecodeHandler());
                ch.pipeline().addLast("BusinessHandler", new BusinessHandler());
            }
        });
        if (logger.isDebugEnabled()) {
            logger.debug("socket client[" + name + "] start success");
        }
        isStart = true;
    }

    /**
     * 关闭
     */
    public synchronized void shutdown() {
        if (!isStart) {
            logger.info("socket client[" + name + "] has stop, not need stop again");
            return;
        }
        manager.removeAndCloseAll();
        workerGroup.shutdownGracefully();
        if (logger.isDebugEnabled()) {
            logger.debug("socket client[" + name + "] stoped");
        }
        isStart = false;
    }

    /**
     * 新建一个连接
     *
     * @return 链接ID
     * @throws IllegalArgumentException 链接失败报错
     */
    public synchronized ChannelId connect() {
        if (!isStart) {
            throw new IllegalArgumentException("socketclient[" + name + "]  is stopped");
        }
        ChannelFuture channelFuture = bootStrap.connect();
        ChannelFuture syncresult = channelFuture.awaitUninterruptibly();
        if (!syncresult.isSuccess()) {
            throw new IllegalArgumentException("socketclient[" + name + "] connect error", syncresult.cause());
        }
        Channel channel = channelFuture.channel();
        ChannelId channelId = channel.id();
        manager.addChannel(channelId, channel);
        return channelId;
    }

    /**
     * 关闭一个连接
     *
     * @param channelId 链接ID
     */
    public synchronized void disconnect(ChannelId channelId) {
        if (!isStart) {
            logger.info("socketclient[" + name + "]  is stopped");
            return;
        }
        if (channelId == null) {
            return;
        }
        Channel channel = manager.getChannel(channelId);
        if (channel == null) {
            return;
        }
        manager.removeAndCloseChannel(channelId);
    }

    /**
     * 推送消息,不等待消息真正发出去
     *
     * @param channelId 链接ID
     * @param message   消息
     * @return 是否发送成功
     */
    public boolean sendMessage(ChannelId channelId, Object message) {
        return sendMessage(channelId, message, false);
    }

    /**
     * 推送消息
     *
     * @param channelId    链接ID
     * @param message      消息
     * @param isWaitSendOk 是否确保消息已经推送出去
     * @return 是否发送成功
     */
    public boolean sendMessage(ChannelId channelId, Object message, boolean isWaitSendOk) {
        if (!isStart) {
            throw new IllegalArgumentException("channel is not start");
        }
        Channel channel = manager.getChannel(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("channel is not found");
        }
        if (!channel.isActive()) {
            throw new IllegalArgumentException("channel is stopped");
        }
        ChannelFuture result0 = channel.writeAndFlush(message);

        if (isWaitSendOk) {
            result0 = result0.awaitUninterruptibly();
            if (result0.isSuccess()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("socket client[" + name + "] ,channelId[" + channelId + "] send message ok");
                }
                return true;
            } else {
                logger.error("socket client[" + name + "] ,channelId[" + channelId + "] send message error", result0.cause());
                return false;
            }
        } else {
            return true;
        }
    }

    /**
     * 获取client名字
     *
     * @return client名字
     */
    public String getName() {
        return name;
    }

    /**
     * 是否启动
     *
     * @return 是否启动
     */
    public boolean isStart() {
        return isStart;
    }

    /**
     * 获取连接管理器
     *
     * @return 连接管理器
     */
    public ChannelManager getChannelManager() {
        return manager;
    }

    /**
     * 链接处理
     */
    protected abstract void connectHandle(ChannelHandlerContext ctx) throws Exception;

    /**
     * 断开链接处理
     */
    protected abstract void closedHandle(ChannelHandlerContext ctx) throws Exception;

    /**
     * 链接异常处理
     */
    protected abstract void exceptionHandle(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * 读链接空闲处理
     */
    protected abstract void readIdleHandle(ChannelHandlerContext ctx) throws Exception;

    /**
     * 读写链接空闲处理
     */
    protected abstract void allIdleHandle(ChannelHandlerContext ctx) throws Exception;

    /**
     * 写链接空闲处理
     */
    protected abstract void writeIdleHandle(ChannelHandlerContext ctx) throws Exception;

    /**
     * 编码
     */
    protected abstract byte[] encode(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * 接收到数据的解码处理
     */
    protected abstract List<Object> decodeHandle(ChannelHandlerContext ctx, ByteBuf in) throws Exception;

    /**
     * 经过decode以后的业务数据处理器
     */
    protected abstract void businessHandle(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * 链接处理器
     */
    class ConnectHandler extends ChannelInboundHandlerAdapter {

        /**
         * 链接
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            SocketClient.this.connectHandle(ctx);
            super.channelActive(ctx);
        }

        /**
         * 断开连接
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            SocketClient.this.closedHandle(ctx);
            super.channelInactive(ctx);
        }

        /**
         * 链接异常
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            SocketClient.this.exceptionHandle(ctx, cause);
            super.exceptionCaught(ctx, cause);
        }

        /**
         * 空闲处理
         */
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                switch (event.state()) {
                    case WRITER_IDLE:
                        SocketClient.this.writeIdleHandle(ctx);
                        break;
                    case READER_IDLE:
                        SocketClient.this.readIdleHandle(ctx);
                        break;
                    case ALL_IDLE:
                        SocketClient.this.allIdleHandle(ctx);
                        break;
                    default:
                        break;
                }
            }
            super.userEventTriggered(ctx, evt);
        }

    }

    /**
     * 接收到数据的解码处理器
     */
    class DecodeHandler extends ByteToMessageDecoder {

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            List<Object> objs = SocketClient.this.decodeHandle(ctx, in);
            if (objs != null) {
                out.addAll(objs);
            }
        }

    }

    /**
     * 经过decode以后的业务数据处理器
     */
    class BusinessHandler extends SimpleChannelInboundHandler<Object> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            SocketClient.this.businessHandle(ctx, msg);
        }

    }

    /**
     * 待发数据编码处理器
     */
    class EncodeHandler extends MessageToByteEncoder<Object> {

        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            byte[] bytes = SocketClient.this.encode(ctx, msg);
            if (bytes != null) {
                out.writeBytes(bytes);
            }
        }
    }

}