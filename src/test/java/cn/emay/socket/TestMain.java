package cn.emay.socket;

import cn.emay.socket.chat.ChatClient;
import cn.emay.socket.chat.ChatServer;
import io.netty.channel.ChannelId;

public class TestMain {

    public static void main(String[] args) {

        ChatServer server = new ChatServer(9999);

        ChatClient client = new ChatClient("127.0.0.1:9999");

        server.startup();
        client.startup();

        ChannelId channelId = client.connect();
        client.sendMessage(channelId, "你好", true);

        client.shutdown();
        server.shutdown();

    }

}
