package me.qurti.mod.util;

import me.qurti.mod.command.Commands;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.packet.Packet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;







/*

The following handles ping modif for both incoming and also outgoing packets! This Extends ChannelDuplexHandler to intercept operations in both dirs!
 */









public class PingModifierHandler extends ChannelDuplexHandler {
    private static final long SPIN_WAIT_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(2L);
    private static final long MIN_SCHEDULE_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(1L);

    private final ConcurrentLinkedQueue<DelayedOutgoingPacket> outgoingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingOutgoing = new AtomicBoolean(false);

    private final ConcurrentLinkedQueue<DelayedIncomingPacket> incomingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingIncoming = new AtomicBoolean(false);

    private volatile ChannelHandlerContext ctx;
    private volatile boolean active = true;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }







    /*
    This will be called when the handler is removed so when the mod is disabled somehow or disconnected, WE MUST FLUSH ALL THE QUEUED PACKETS isntantly
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        flushAll();
        this.ctx = null;
    }







    // Intercepts OUTBOUND packets (Writing to the server)
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!active || !Commands.isPingmodifierEnabled()) {
            super.write(ctx, msg, promise);
            return;
        }

        // OUTBOUND: We expect ByteBufs because we are AFTER the encoder.
        // If it's a ByteBuf, we can delay it.
        // If it's a Packet (rare, maybe unencoded??? Idk honestly), we can delay it.
        // If it's anything else (Lambda), we PASS IT THROUGH!!!!

        boolean canDelay = (msg instanceof ByteBuf) || (msg instanceof Packet);

        if (!canDelay) {
            super.write(ctx, msg, promise);
            return;
        }

        long delayMs = Commands.getOutgoingDelay();
        if (delayMs <= 0) {
            super.write(ctx, msg, promise);
            return;
        }

        long executeTime = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        outgoingQueue.offer(new DelayedOutgoingPacket(msg, promise, executeTime));
        scheduleOutgoingProcessing(ctx);
    }


    // Intercepts INBOUND packets (Reading from the server)
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!active || !Commands.isPingmodifierEnabled()) {
            super.channelRead(ctx, msg);
            return;
        }

        // INBOUND: We expect to get some Packets (because we are usually after the decoder in read order)
        // or ByteBufs if decoder is after us
        // Safest to just delay everything that looks a bit networky

        boolean canDelay = (msg instanceof Packet) || (msg instanceof ByteBuf);

        if (!canDelay) {
            super.channelRead(ctx, msg);
            return;
        }



        // These are the packets we CAN'T DELAY
        // If we delay these, the server thinks we timed out / disconnected. Which obviously results in a kick for
        Packet<?> packet = (msg instanceof Packet) ? (Packet<?>) msg : null;
        if (packet != null && isKeepAlivePacket(packet)) {
            super.channelRead(ctx, msg);
            return;
        }

        long delayMs = Commands.getIncomingDelay();
        if (delayMs <= 0) {
            super.channelRead(ctx, msg);
            return;
        }

        long executeTime = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        incomingQueue.offer(new DelayedIncomingPacket(msg, executeTime));
        scheduleIncomingProcessing(ctx);
    }

    // Process outgoing packets
    private void scheduleOutgoingProcessing(ChannelHandlerContext ctx) {
        if (!processingOutgoing.compareAndSet(false, true)) {
            return;
        }
        if (ctx.executor().inEventLoop()) {
            processOutgoingQueue(ctx);
        } else {
            ctx.executor().execute(() -> processOutgoingQueue(ctx));
        }
    }

    private void processOutgoingQueue(ChannelHandlerContext ctx) {
        try {
            boolean needsFlush = false;
            while (true) {
                DelayedOutgoingPacket delayed = outgoingQueue.peek();
                if (delayed == null) {
                    processingOutgoing.set(false);
                    if (needsFlush && ctx.channel().isOpen()) ctx.flush();
                    return;
                }
                long remainingNs = delayed.executeTime - System.nanoTime();
                if (remainingNs <= 0) {
                    outgoingQueue.poll();
                    if (ctx.channel().isOpen()) {
                        ctx.write(delayed.packet, delayed.promise);
                        needsFlush = true;
                    }
                    continue;
                }
                if (needsFlush && ctx.channel().isOpen()) {
                    ctx.flush();
                    needsFlush = false;
                }
                if (remainingNs <= SPIN_WAIT_THRESHOLD_NS) {
                    spinWait(remainingNs);
                    continue;
                }
                long scheduleDelayNs = Math.max(remainingNs - SPIN_WAIT_THRESHOLD_NS, MIN_SCHEDULE_DELAY_NS);
                ctx.executor().schedule(() -> scheduleOutgoingProcessing(ctx), scheduleDelayNs, TimeUnit.NANOSECONDS);
                processingOutgoing.set(false);
                return;
            }
        } catch (Exception e) {
            processingOutgoing.set(false);
            throw e;
        }
    }

    private void scheduleIncomingProcessing(ChannelHandlerContext ctx) {
        if (!processingIncoming.compareAndSet(false, true)) {
            return;
        }
        if (ctx.executor().inEventLoop()) {
            processIncomingQueue(ctx);
        } else {
            ctx.executor().execute(() -> processIncomingQueue(ctx));
        }
    }

    private void processIncomingQueue(ChannelHandlerContext ctx) {
        try {
            while (true) {
                DelayedIncomingPacket delayed = incomingQueue.peek();
                if (delayed == null) {
                    processingIncoming.set(false);
                    return;
                }
                long remainingNs = delayed.executeTime - System.nanoTime();
                if (remainingNs <= 0) {
                    incomingQueue.poll();
                    if (ctx.channel().isOpen()) {
                        ctx.fireChannelRead(delayed.packet);
                    }
                    continue;
                }
                if (remainingNs <= SPIN_WAIT_THRESHOLD_NS) {
                    spinWait(remainingNs);
                    continue;
                }
                long scheduleDelayNs = Math.max(remainingNs - SPIN_WAIT_THRESHOLD_NS, MIN_SCHEDULE_DELAY_NS);
                ctx.executor().schedule(() -> scheduleIncomingProcessing(ctx), scheduleDelayNs, TimeUnit.NANOSECONDS);
                processingIncoming.set(false);
                return;
            }
        } catch (Exception e) {
            processingIncoming.set(false);
            throw e;
        }
    }

    private void flushAll() {
        ChannelHandlerContext context = this.ctx;
        if (context == null || !context.channel().isOpen()) {
            outgoingQueue.clear();
            incomingQueue.clear();
            return;
        }
        active = false;
        DelayedOutgoingPacket outgoing;
        while ((outgoing = outgoingQueue.poll()) != null) {
            if (context.channel().isOpen()) context.write(outgoing.packet, outgoing.promise);
        }
        if (context.channel().isOpen()) context.flush();
        DelayedIncomingPacket incoming;
        while ((incoming = incomingQueue.poll()) != null) {
            if (context.channel().isOpen()) context.fireChannelRead(incoming.packet);
        }
        processingOutgoing.set(false);
        processingIncoming.set(false);
    }



    private static void spinWait(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }



    // func if packet is keep alive
    private static boolean isKeepAlivePacket(Packet<?> packet) {
        String className = packet.getClass().getSimpleName();
        return className.contains("KeepAlive") || className.contains("Pong") || className.contains("QueryPing");
    }







    private static class DelayedOutgoingPacket {
        final Object packet;
        final ChannelPromise promise;
        final long executeTime;

        DelayedOutgoingPacket(Object packet, ChannelPromise promise, long executeTime) {
            this.packet = packet;
            this.promise = promise;
            this.executeTime = executeTime;
        }
    }

    private static class DelayedIncomingPacket {
        final Object packet;
        final long executeTime;

        DelayedIncomingPacket(Object packet, long executeTime) {
            this.packet = packet;
            this.executeTime = executeTime;
        }
    }
}