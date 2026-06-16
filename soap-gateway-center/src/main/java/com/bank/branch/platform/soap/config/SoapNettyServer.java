package com.bank.branch.platform.soap.config;

import com.bank.branch.platform.soap.handler.SoapChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SoapNettyServer implements SmartLifecycle {

    private final SoapNettyProperties props;
    private final SoapChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = false;

    @Override
    public void start() {
        bossGroup = new NioEventLoopGroup(props.getBossThreads());
        workerGroup = new NioEventLoopGroup(props.getWorkerThreads());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(channelInitializer)
                    .option(ChannelOption.SO_BACKLOG, 256)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            serverChannel = bootstrap.bind(props.getPort()).sync().channel();
            running = true;
            log.info("SOAP Netty server started on port {}", props.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownGroups();
            throw new RuntimeException("SOAP Netty server start interrupted", e);
        } catch (Exception e) {
            // 绑定失败最常见原因：端口已被占用（同机多开实例时第二个实例未用
            // --platform.soap.netty.port 覆盖端口）。这里点名端口，避免 "Failed to
            // start bean 'soapNettyServer'" 的笼统报错让人误以为是 server.port 的问题。
            shutdownGroups();
            throw new IllegalStateException(
                    "SOAP Netty 服务绑定端口 " + props.getPort() + " 失败：" + e.getMessage()
                            + "。若在同一台机器多开实例，请为第二个实例指定不同的 SOAP 端口："
                            + "--platform.soap.netty.port=<其它端口>", e);
        }
    }

    /** 启动失败时回收已创建的 EventLoopGroup，避免 boss/worker 线程泄漏。 */
    private void shutdownGroups() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    @Override
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        running = false;
        log.info("SOAP Netty server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }
}
