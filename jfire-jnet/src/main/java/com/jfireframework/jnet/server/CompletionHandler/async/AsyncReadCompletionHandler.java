package com.jfireframework.jnet.server.CompletionHandler.async;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.jfireframework.baseutil.collection.buffer.ByteBuf;
import com.jfireframework.baseutil.collection.buffer.DirectByteBuf;
import com.jfireframework.baseutil.disruptor.Disruptor;
import com.jfireframework.baseutil.simplelog.ConsoleLogFactory;
import com.jfireframework.baseutil.simplelog.Logger;
import com.jfireframework.jnet.common.channel.ServerChannelInfo;
import com.jfireframework.jnet.common.decodec.FrameDecodec;
import com.jfireframework.jnet.common.exception.BufNotEnoughException;
import com.jfireframework.jnet.common.exception.LessThanProtocolException;
import com.jfireframework.jnet.common.exception.NotFitProtocolException;
import com.jfireframework.jnet.common.handler.DataHandler;
import com.jfireframework.jnet.common.result.AsyncServerInternalResult;
import com.jfireframework.jnet.common.result.InternalResult;
import com.jfireframework.jnet.common.result.ServerInternalResult;

public class AsyncReadCompletionHandler implements CompletionHandler<Integer, ServerChannelInfo>
{
    private static final Logger               logger         = ConsoleLogFactory.getLogger();
    private final FrameDecodec                frameDecodec;
    private final DataHandler[]               handlers;
    private final DirectByteBuf               ioBuf          = DirectByteBuf.allocate(100);
    private final ServerChannelInfo           channelInfo;
    public final static int                   CONTINUE_READ  = 1;
    // 暂时不监听监听当前通道上的数据
    public final static int                   FREE_OF_READ   = 2;
    public final static int                   IN_READ        = 1;
    public final static int                   OUT_OF_READ    = 2;
    private AtomicInteger                     readState      = new AtomicInteger(IN_READ);
    private volatile long                     cursor;
    private long                              wrapPoint      = 0;
    private final AsyncWriteCompletionHandler writeCompletionHandler;
    // 读取超时时间
    private final long                        readTimeout;
    private final long                        waitTimeout;
    // 最后一次读取时间
    private long                              lastReadTime;
    // 本次读取的截止时间
    private long                              endReadTime;
    // 启动读取超时的计数
    private boolean                           startCountdown = false;
    private final Disruptor                   disruptor;
    private final int                         capacity;
    
    public AsyncReadCompletionHandler(ServerChannelInfo channelInfo, Disruptor disruptor)
    {
        this.disruptor = disruptor;
        this.channelInfo = channelInfo;
        frameDecodec = channelInfo.getFrameDecodec();
        handlers = channelInfo.getHandlers();
        readTimeout = channelInfo.getReadTimeout();
        waitTimeout = channelInfo.getWaitTimeout();
        writeCompletionHandler = new AsyncWriteCompletionHandler(this, channelInfo);
        capacity = channelInfo.getEntryArraySize();
    }
    
    @Override
    public void completed(Integer read, ServerChannelInfo channelInfo)
    {
        if (read == -1)
        {
            channelInfo.closeChannel();
            return;
        }
        ioBuf.addWriteIndex(read);
        doRead();
    }
    
    @Override
    public void failed(Throwable exc, ServerChannelInfo channelInfo)
    {
        catchThrowable(exc);
        ioBuf.release();
    }
    
    /**
     * 使用处理器处理异常，处理之后关闭当前的通道
     * 
     * @param exc
     */
    public void catchThrowable(Throwable exc)
    {
        try
        {
            InternalResult result = new AsyncServerInternalResult();
            result.setData(exc);
            result.setIndex(0);
            Object intermediateResult = exc;
            try
            {
                for (DataHandler each : handlers)
                {
                    intermediateResult = each.catchException(intermediateResult, result);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        catch (Exception e)
        {
            logger.error("关闭通道异常", e);
        }
        channelInfo.closeChannel();
        /**
         * 这个方法里不能去释放iobuf。因为这个方法有可能是异步处理的时候被调用，这样通道还没有关闭的情况下就先释放了iobuf，
         * 然后关闭通道又释放一次就会造成错误
         * 或者是该方法中被释放，其他地方回收了又再次使用，然后通过中关闭的时候释放掉，就错误的释放了别的地方的ioBuf。
         * 所以这个方法中是不可以释放iobuf的，
         * 一定是要在ReadCompletionHandler的complete或者fail方法中完成对iobuf的释放
         */
    }
    
    public void doRead()
    {
        while (true)
        {
            try
            {
                int result = frameAndHandle();
                if (result == CONTINUE_READ)
                {
                    startReadWait();
                    return;
                }
                else if (result == FREE_OF_READ)
                {
                    return;
                }
            }
            catch (LessThanProtocolException e)
            {
                startReadWait();
                return;
            }
            catch (BufNotEnoughException e)
            {
                ioBuf.compact().ensureCapacity(e.getNeedSize());
                continueRead();
                return;
            }
            catch (NotFitProtocolException e)
            {
                logger.debug("协议错误，关闭链接");
                catchThrowable(e);
                ioBuf.release();
                return;
            }
            catch (Throwable e)
            {
                catchThrowable(e);
                ioBuf.release();
                return;
            }
        }
    }
    
    public void reStartRead()
    {
        if (readState.get() == OUT_OF_READ)
        {
            if (readState.compareAndSet(OUT_OF_READ, IN_READ))
            {
                // logger.trace("恢复读取，当前读取{}", cursor - 1);
                doRead();
            }
        }
    }
    
    public int frameAndHandle() throws Exception
    {
        while (true)
        {
            testcursor: if (cursor >= wrapPoint)
            {
                wrapPoint = writeCompletionHandler.cursor() + capacity;
                if (cursor < wrapPoint)
                {
                    break testcursor;
                }
                // 在设置之前，可能写线程已经将所有的数据都写出完毕了并且写线程结束运行。此时就不会有人来唤醒读取线程了
                readState.set(OUT_OF_READ);
                // 设置之后必须进行尝试
                wrapPoint = writeCompletionHandler.cursor() + capacity;
                if (cursor < wrapPoint)
                {
                    if (readState.compareAndSet(OUT_OF_READ, IN_READ))
                    {
                        break testcursor;
                    }
                    else
                    {
                        return FREE_OF_READ;
                    }
                }
                return FREE_OF_READ;
            }
            ByteBuf<?> frame = frameDecodec.decodec(ioBuf);
            if (frame == null)
            {
                return CONTINUE_READ;
            }
            AsyncServerInternalResult result = new AsyncServerInternalResult();
            result.setData(frame);
            result.setChannelInfo(channelInfo);
            result.setReadCompletionHandler(this);
            result.setWriteCompletionHandler(writeCompletionHandler);
            result.setIndex(0);
            disruptor.publish(result);
            if (ioBuf.remainRead() == 0)
            {
                return CONTINUE_READ;
            }
        }
    }
    
    public boolean isAvailable(long cursor)
    {
        return cursor < this.cursor;
    }
    
    /**
     * 开始空闲读取等待，并且将倒数计时状态重置为false
     */
    public void startReadWait()
    {
        startCountdown = false;
        channelInfo.getChannel().read(getWriteBuffer(), waitTimeout, TimeUnit.MILLISECONDS, channelInfo, this);
    }
    
    /**
     * 将iobuf的内容进行压缩，返回一个处于可写状态的ByteBuffer
     * 
     * @return
     */
    private ByteBuffer getWriteBuffer()
    {
        ioBuf.compact();
        ByteBuffer ioBuffer = ioBuf.nioBuffer();
        ioBuffer.position(ioBuffer.limit()).limit(ioBuffer.capacity());
        return ioBuffer;
    }
    
    /**
     * 在通道上继续读取未读取完整的数据
     */
    public void continueRead()
    {
        if (startCountdown == false)
        {
            lastReadTime = System.currentTimeMillis();
            endReadTime = lastReadTime + readTimeout;
            startCountdown = true;
        }
        channelInfo.getChannel().read(getWriteBuffer(), getRemainTime(), TimeUnit.MILLISECONDS, channelInfo, this);
        lastReadTime = System.currentTimeMillis();
    }
    
    /**
     * 剩余的读取消息时间
     * 
     * @return
     */
    private long getRemainTime()
    {
        return endReadTime - lastReadTime;
    }
    
    public void turnToWorkDisruptor(ServerInternalResult result)
    {
        disruptor.publish(result);
    }
}