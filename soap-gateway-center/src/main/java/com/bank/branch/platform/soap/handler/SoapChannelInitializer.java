package com.bank.branch.platform.soap.handler;

import com.bank.branch.platform.soap.config.SoapNettyProperties;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SoapChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final SoapNettyProperties props;
    private final SoapDispatchHandler soapDispatchHandler;

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("idle", new IdleStateHandler(props.getReadIdleSeconds(), 0, 0, TimeUnit.SECONDS));
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(props.getMaxContentLength()));
        pipeline.addLast("soapDispatch", soapDispatchHandler);
    }
}
