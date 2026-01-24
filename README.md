# PurplePing
Silly Fabric client mod that simulates high ping by delaying network packets (client to server and/or server to client) inside Minecraft’s Netty connection pipeline. ​




# How to use?

| Command      | Usage                     | Description                                                                                                         |
| :----------- | :------------------------ | :------------------------------------------------------------------------------------------------------------------ |
| **.ptoggle** | `.ptoggle`                | Toggles, the mod ON or OFF. Turning it off immediately flushes all pending packets as well and set amount is reset. |
| **.ping**    | `.ping <send\|recv> <ms>` | Sets the delay in ms. Use `send` for packets from you. Or `recv` for packets you receive!                           |


TODO: Add a GUI for config, add custom keybinds!
