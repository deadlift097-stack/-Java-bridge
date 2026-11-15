# Running Java mods without rewriting them

1. Put the `.jar` in `mods-in/` in your repo, run the **convert-mods** workflow, download `converted-mods`.
2. Copy the `.jar` + `.json` pairs into `<dataDir>/mods` and restart the game.
3. Open the in-game **Mods** button. Each mod shows a gear icon, a status line, and a switch.

Fabric entrypoints and NeoForge `@Mod` classes are both detected (see API_COMPAT.md for what is supported).
Mods that need Minecraft Java Edition game code (Mixins, `net.minecraft.*` classes) cannot work on Bedrock:
they appear as "Java Edition only" and stay off. Sodium, Lithium, Sodium Extra, BetterHitreg and similar
mods change how the Java engine renders, ticks or registers hits, and Bedrock has its own engine, so there
is nothing for them to change. REPORT.txt in the converted-mods artifact tells you which case each jar is.
