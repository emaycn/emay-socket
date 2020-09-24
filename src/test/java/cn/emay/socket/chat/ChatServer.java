package cn.emay.socket.chat;

import cn.emay.socket.server.SocketServer;
import cn.emay.socket.utils.ByteUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ChatServer extends SocketServer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public ChatServer(int port) {
        super("服务器", port, 1000, 10, 10, 10);
    }

    @Override
    protected void connectHandle(ChannelHandlerContext ctx, String address) {
        log.info(ctx.channel().remoteAddress().toString() + " connect ");
    }

    @Override
    protected void closedHandle(ChannelHandlerContext ctx, String address) {
        log.info(ctx.channel().remoteAddress().toString() + " disconnect ");
    }

    @Override
    protected void exceptionHandle(ChannelHandlerContext ctx, String address, Throwable cause) {
        log.error(ctx.channel().remoteAddress().toString() + " exception ", cause);
    }

    @Override
    protected void readIdleHandle(ChannelHandlerContext ctx, String address) {
        log.info(ctx.channel().remoteAddress().toString() + " read idle more than 10s ");
    }

    @Override
    protected void allIdleHandle(ChannelHandlerContext ctx, String address) {
        log.info(ctx.channel().remoteAddress().toString() + " read and write idel more than 10s ");
    }

    @Override
    protected void writeIdleHandle(ChannelHandlerContext ctx, String address) {
        log.info(ctx.channel().remoteAddress().toString() + " write idle more than 10s ");
    }

    @Override
    protected byte[] encode(ChannelHandlerContext ctx, Object msg) {
        String message = (String) msg;
        byte[] mbyts = message.getBytes();
        int length = mbyts.length;
        byte[] lbyts = ByteUtils.intToBytes4(length);
        return ByteUtils.mergeBytes(lbyts, mbyts);
    }

    @Override
    protected List<Object> decode(ChannelHandlerContext ctx, ByteBuf in) {
        ArrayList<Object> list = new ArrayList<>();
        int headLength = 4;
        while (true) {
            int waitLength = in.readableBytes();
            if (waitLength > headLength) {
                ByteBuf bb = null;
                try {
                    bb = in.readBytes(headLength);
                    byte[] headbBytes = new byte[headLength];
                    bb.getBytes(0, headbBytes);
                    int bodyLength = ByteUtils.bytes4ToInt(headbBytes);
                    if (headLength + bodyLength <= waitLength) {
                        ByteBuf bbc = null;
                        try {
                            bbc = in.readBytes(bodyLength);
                            byte[] bodyBytes = new byte[bodyLength];
                            bbc.getBytes(0, bodyBytes);
                            String message = new String(bodyBytes, StandardCharsets.UTF_8);
                            list.add(message);
                            in.discardReadBytes();
                        } catch (Exception e) {
                            log.error("read error", e);
                        } finally {
                            if (bbc != null) {
                                bbc.release();
                            }
                        }
                    } else {
                        in.resetReaderIndex();
                        break;
                    }
                } catch (Exception e) {
                    log.error("read error", e);
                } finally {
                    if (bb != null) {
                        bb.release();
                    }
                }
            } else {
                in.resetReaderIndex();
                break;
            }
        }
        return list;
    }

    @Override
    protected void businessLogic(ChannelHandlerContext ctx, Object msg) {
        String message = (String) msg;
        log.info("receive [" + ctx.channel().remoteAddress().toString() + "] message : " + message);
        String repay = "你向我说了【" + message + "】，收到！";
        this.sendMessage(ctx, repay, false);
    }
}
