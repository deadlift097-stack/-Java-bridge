# Running Java mods without rewriting them

What works: a normal jar whose Fabric entrypoints (`ModInitializer` / `ClientModInitializer`) use only
plain Java and Android-compatible libraries. Convert it with the `convert-mods` workflow (it dexes the jar and
writes the `.json` from `fabric.mod.json`), then copy both files to `<dataDir>/mods`.

What cannot work: mods that touch Minecraft Java Edition classes (`net.minecraft.*`, Mixins, Fabric API
events). Bedrock has none of those classes and a different code base, so there is nothing to run them against.
Such mods load, then log "needs Minecraft Java Edition classes" and are skipped. BetterHitreg is in this group.
The converter's REPORT.txt tells you per jar which case you have.
