
package me.qurti.mod.command;
import me.main.MainClass;
import me.qurti.mod.util.ChatUtil;
import net.minecraft.util.math.MathHelper;


public class Commands {
    private static String pingCommand;
    private static String pingToggleCommand;

    // Ping modifier states
    private static long outgoingDelayMs = 0;  // Delay for packets being sent
    private static long incomingDelayMs = 0;  // Delay for packets being received
    private static boolean pingmodifierEnabled = false;

    static {
        // Direct assignment
        pingCommand = ".ping";
        pingToggleCommand = ".ptoggle";
    }

    public static boolean commandHandler(String message) {
        if (message == null || message.isEmpty()) return false;
        boolean handled = false;

        if (message.startsWith(pingCommand)) {
            Commands.handlePingCommand(message);
            handled = true;
        } else if (message.startsWith(pingToggleCommand)) {
            Commands.handlePingToggleCommand();
            handled = true;
        }
        return handled;
    }

    // Ping modifier command handler
    public static void handlePingCommand(String message) {
        try {
            String[] parts = message.split(" ");
            if (parts.length < 3) {
               ChatUtil.addChatMessage("Usage: .ping <send|recv> <ms>");
                return;
            }

            String direction = parts[1].toLowerCase();
            long delayMs = Long.parseLong(parts[2]);


            // Delay, safety max (Nun existent)
            delayMs = MathHelper.clamp(delayMs, 0, 100000000);

            if (direction.equals("send") || direction.equals("out")) {
                outgoingDelayMs = delayMs;
               ChatUtil.addChatMessage("Send delay: " + delayMs + "ms");
            } else if (direction.equals("recv") || direction.equals("in")) {
                incomingDelayMs = delayMs;
               ChatUtil.addChatMessage("Recv delay: " + delayMs + "ms");
            } else {
               ChatUtil.addChatMessage("Invalid direction. Use 'send' or 'recv'");
            }
        } catch (NumberFormatException e) {
           ChatUtil.addChatMessage("Invalid delay value");
        }
    }

    public static void handlePingToggleCommand() {
        pingmodifierEnabled = !pingmodifierEnabled;
       ChatUtil.addChatMessage("Ping modifier: " + (pingmodifierEnabled ? "ON" : "OFF"));

        // If disabled, reset delays
        if (!pingmodifierEnabled) {
            outgoingDelayMs = 0;
            incomingDelayMs = 0;
        }
    }

    // Public getters for the ping modifier logic to use
    public static long getOutgoingDelay() {
        return pingmodifierEnabled ? outgoingDelayMs : 0;
    }

    public static long getIncomingDelay() {
        return pingmodifierEnabled ? incomingDelayMs : 0;
    }

    public static boolean isPingmodifierEnabled() {
        return pingmodifierEnabled;
    }




}