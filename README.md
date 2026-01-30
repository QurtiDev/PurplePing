

<p align="center">
  <img src="src/main/resources/assets/modid/icon.png" width="150" alt="PurplePing Icon">
</p>

<h1 align="center">PurplePing</h1>

Silly Fabric client mod that simulates high ping by delaying network packets (client to server and/or server to client) inside Minecraft’s Netty connection pipeline. ​


WARNING: This is a fabric mod that manipulates packets!! Do not distribute or use this on servers where it would give unfair advantages. 
Respect Mojang/Microsoft ToS and individual server rules. 



## Planned Features TODO!!!
*   [ ] GUI for easier configs
*   [ ] Custom keybinds system
*   [ ] Random jitter mode and offset, for faking unstable lag!

# How to use?

| Command      | Usage                     | Description                                                                                                         |
| :----------- | :------------------------ | :------------------------------------------------------------------------------------------------------------------ |
| **.ptoggle** | `.ptoggle`                | Toggles, the mod ON or OFF. Turning it off immediately flushes all pending packets as well and set amount is reset. |
| **.ping**    | `.ping <send\|recv> <ms>` | Sets the delay in ms. Use `send` for packets from you. Or `recv` for packets you receive!                           |



### An example for you:
*   `.ping send 200` (Adds 200ms delay to your clicks/attacks) !!

## Installation
1. Have a [Fabric Loader](https://fabricmc.net/)!
2. Download the **PurplePing** `.jar` file from Releases or build it from src.
3. Drop the mod into your mods folder!
4. Launch the game!
5. Profit.

CurseForge page [here](https://www.curseforge.com/minecraft/mc-mods/purpleping)

Modrinth page [here](https://modrinth.com/mod/purpleping)
