package com.modhost.api;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entry points called from C++. A mod is <name>.jar (dexed) + <name>.json with either
 * {"id","main"} (ModHostMod) or {"id","entrypoints":[...]} (Fabric-style ModInitializer classes).
 */
public final class Bootstrap {
    static final class Entry {
        final String id;
        final ModHostMod mod;
        final boolean fabric;
        boolean enabled; // user preference (persisted)
        boolean active;  // onEnable has run
        Entry(String id, ModHostMod mod, boolean fabric) { this.id = id; this.mod = mod; this.fabric = fabric; }
    }

    private static final List<Entry> MODS = new ArrayList<Entry>();
    private static final Set<String> DISABLED = new HashSet<String>();
    private static final ModLogger LOG = new ModLogger("ModHost");
    private static final Pattern MAIN = Pattern.compile("\"main\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ENTRIES = Pattern.compile("\"entrypoints\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static File disabledFile;
    private static boolean running;

    private Bootstrap() {}

    // ---------------- called from C++ ----------------

    public static void loadMods(String modsDir, String dataRoot) {
        new File(dataRoot).mkdirs();
        disabledFile = new File(dataRoot, "disabled.txt");
        readDisabled();
        File[] metas = new File(modsDir).listFiles(new FilenameFilter() {
            public boolean accept(File d, String n) { return n.endsWith(".json"); }
        });
        if (metas == null) { LOG.warn("no mods dir: " + modsDir); return; }
        for (File meta : metas) {
            try {
                loadOne(meta, dataRoot);
            } catch (Throwable t) {
                LOG.error("failed to load " + meta.getName() + ": " + explain(t));
            }
        }
        LOG.info(MODS.size() + " Java mod(s) loaded");
    }

    public static void enableAll() {
        running = true;
        for (Entry e : MODS) if (e.enabled) callEnable(e);
        Platform.start();
    }

    public static void disableAll() {
        for (Entry e : MODS) if (e.active) callDisable(e);
        Platform.stop();
        running = false;
    }

    public static void tick() { Events.fireTick(); }

    /** Native hooks use this to raise game events. */
    public static void fire(String name, String data) { Events.post(name, data); }

    // ---------------- used by the menu / event bus ----------------

    static List<Entry> list() { return MODS; }

    static boolean isActive(String owner) {
        if ("core".equals(owner)) return true;
        for (Entry e : MODS) if (e.id.equals(owner)) return e.active;
        return true;
    }

    static void setEnabled(String id, boolean on) {
        for (Entry e : MODS) {
            if (!e.id.equals(id)) continue;
            e.enabled = on;
            if (on) DISABLED.remove(id); else DISABLED.add(id);
            writeDisabled();
            if (running && on && !e.active) callEnable(e);
            if (!on && e.active) callDisable(e);
        }
    }

    // ---------------- internals ----------------

    private static void loadOne(File meta, String dataRoot) throws Exception {
        String json = read(meta);
        Matcher im = ID.matcher(json);
        String id = im.find() ? im.group(1) : meta.getName().replace(".json", "");

        List<String> classes = new ArrayList<String>();
        Matcher mm = MAIN.matcher(json);
        if (mm.find()) classes.add(mm.group(1));
        Matcher em = ENTRIES.matcher(json);
        if (em.find()) {
            Matcher q = QUOTED.matcher(em.group(1));
            while (q.find()) classes.add(q.group(1));
        }
        if (classes.isEmpty()) { LOG.warn(meta.getName() + ": needs \"main\" or \"entrypoints\""); return; }

        File data = new File(dataRoot, id);
        data.mkdirs();
        ModHostMod mod = null;
        List<Object> fabric = new ArrayList<Object>();
        Events.owner = id;
        try {
            for (String cn : classes) {
                Object o = Class.forName(cn, true, Bootstrap.class.getClassLoader())
                        .getDeclaredConstructor().newInstance();
                if (o instanceof ModHostMod) { if (mod == null) mod = (ModHostMod) o; }
                else if (o instanceof ModInitializer || o instanceof ClientModInitializer) fabric.add(o);
                else LOG.warn(id + ": " + cn + " is not a supported entrypoint");
            }
            boolean isFabric = false;
            if (mod == null && !fabric.isEmpty()) { mod = new FabricAdapter(fabric); isFabric = true; }
            if (mod == null) return;
            mod.onLoad(new ModContext(id, data.getAbsolutePath()));
            Entry e = new Entry(id, mod, isFabric);
            e.enabled = !DISABLED.contains(id);
            MODS.add(e);
            LOG.info("loaded " + id + (isFabric ? " (Fabric-style entrypoints)" : ""));
        } catch (Throwable t) {
            LOG.error(id + ": " + explain(t));
        } finally {
            Events.owner = "core";
        }
    }

    private static void callEnable(Entry e) {
        e.active = true;
        Events.owner = e.id;
        try { e.mod.onEnable(); }
        catch (Throwable t) { e.active = false; LOG.error(e.id + ": " + explain(t)); }
        finally { Events.owner = "core"; }
    }

    private static void callDisable(Entry e) {
        Events.owner = e.id;
        try { e.mod.onDisable(); }
        catch (Throwable t) { LOG.error(e.id + ": " + explain(t)); }
        finally { Events.owner = "core"; e.active = false; }
    }

    static String explain(Throwable t) {
        String s = String.valueOf(t);
        if (s.contains("net/minecraft") || s.contains("net.minecraft") || s.contains("com/mojang")) {
            return "needs Minecraft Java Edition classes, which don't exist on Bedrock, so this mod can't run unchanged (" + s + ")";
        }
        if (t instanceof NoClassDefFoundError || t instanceof ClassNotFoundException) return "missing class: " + s;
        return s;
    }

    private static final class FabricAdapter implements ModHostMod {
        private final List<Object> inits;
        private boolean done;
        FabricAdapter(List<Object> inits) { this.inits = inits; }
        public void onLoad(ModContext ctx) {}
        public void onEnable() {
            if (done) return;
            done = true;
            for (Object o : inits) {
                if (o instanceof ModInitializer) ((ModInitializer) o).onInitialize();
                if (o instanceof ClientModInitializer) ((ClientModInitializer) o).onInitializeClient();
            }
        }
        public void onDisable() {}
    }

    private static void readDisabled() {
        DISABLED.clear();
        if (disabledFile == null || !disabledFile.exists()) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(disabledFile), "UTF-8"));
            try { String l; while ((l = r.readLine()) != null) if (l.trim().length() > 0) DISABLED.add(l.trim()); }
            finally { r.close(); }
        } catch (Throwable t) { LOG.warn("could not read disabled.txt: " + t); }
    }

    private static void writeDisabled() {
        if (disabledFile == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            for (String s : DISABLED) sb.append(s).append('\n');
            FileOutputStream out = new FileOutputStream(disabledFile);
            try { out.write(sb.toString().getBytes("UTF-8")); } finally { out.close(); }
        } catch (Throwable t) { LOG.warn("could not save disabled.txt: " + t); }
    }

    private static String read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally { in.close(); }
    }
}
