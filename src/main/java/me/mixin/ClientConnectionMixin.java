package me.mixin;

import me.qurti.mod.command.Commands;
import me.qurti.mod.util.PingModifierHandler;
import me.main.MainClass;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {

    @Shadow
    private Channel channel;

    @Shadow
    @Final
    private NetworkSide side;

    @Unique
    private PingModifierHandler pingModifier;

    @Unique
    private static final String HANDLER_NAME = "qurti_ping_modifier";

    @Inject(at={@At(value="HEAD")}, method="send(Lnet/minecraft/network/packet/Packet;)V", cancellable=true)
    private void onSendPacketHead(Packet<?> packet, CallbackInfo ci) {
        if ((packet instanceof ChatMessageC2SPacket pack)) {
            if (Commands.commandHandler(pack.chatMessage())) ci.cancel();
        }
    }

    @Inject(method = "addFlowControlHandler", at = @At("RETURN"))
    private void addPingModifier(ChannelPipeline pipeline, CallbackInfo ci) {
        if (!isClientSide()) return;
        try {
            injectHandler(pipeline);
        } catch (Exception e) {}
    }

    @Inject(method = "channelActive", at = @At("TAIL"))
    private void ensurePingModifierActive(io.netty.channel.ChannelHandlerContext ctx, CallbackInfo ci) {
        if (!isClientSide()) return;
        try {
            injectHandler(ctx.pipeline());
        } catch (Exception e) {}
    }

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void cleanupPingModifier(CallbackInfo ci) {
        if (pingModifier != null && channel != null && channel.isOpen()) {
            try {
                channel.pipeline().remove(HANDLER_NAME);
            } catch (Exception e) {}
        }
    }

    @Unique
    private boolean isClientSide() {
        return side == NetworkSide.CLIENTBOUND;
    }

    @Unique
    private void injectHandler(ChannelPipeline pipeline) {
        if (pipeline == null || pipeline.get(HANDLER_NAME) != null) {
            return;
        }

       /* System.out.println("[qurti] Injecting ping handler into pipeline");*/

        if (pingModifier == null) {
            pingModifier = new PingModifierHandler();
        }


        // ts makes sure we see ByteBufs (encoded data), not internal Lambda objects.
        if (pipeline.get("encoder") != null) {
            pipeline.addBefore("encoder", HANDLER_NAME, pingModifier);
      /*      System.out.println("[qurti] Success: Injected BEFORE encoder (ByteBuf Mode)");*/
            return;
        }

        // Fallback, If no encoder, try before packet_handler
        if (pipeline.get("packet_handler") != null) {
            pipeline.addBefore("packet_handler", HANDLER_NAME, pingModifier);
            /*System.out.println("[qurti] Warning: Injected before packet_handler (Fallback)");*/
            return;
        }

        pipeline.addLast(HANDLER_NAME, pingModifier);
    }
}