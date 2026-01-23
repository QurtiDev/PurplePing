package me.qurti.mod.util;

import net.minecraft.text.Text;

import static me.main.MainClass.mc;

public class ChatUtil {
    public static void addChatMessage(String message) {
        mc.inGameHud.getChatHud().addMessage(Text.literal(message));
    }
}


