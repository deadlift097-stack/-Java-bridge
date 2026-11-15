package com.modhost.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ModMetadata;
import net.fabricmc.loader.api.Version;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class FabricLoaderImpl implements FabricLoader {
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();

    private FabricLoaderImpl() {}

    public boolean isModLoaded(String id) {
        for (Bootstrap.Entry e : Bootstrap.list()) {
            if (e.id.equals(id) && e.status == Bootstrap.OK) return true;
        }
        return false;
    }

    public Collection<ModContainer> getAllMods() {
        List<ModContainer> out = new ArrayList<ModContainer>();
        for (Bootstrap.Entry e : Bootstrap.list()) if (e.status == Bootstrap.OK) out.add(new Container(e));
        return out;
    }

    public Optional<ModContainer> getModContainer(String id) {
        for (Bootstrap.Entry e : Bootstrap.list()) {
            if (e.id.equals(id) && e.status == Bootstrap.OK) return Optional.<ModContainer>of(new Container(e));
        }
        return Optional.empty();
    }

    public Path getConfigDir() {
        File d = new File(Bootstrap.dataRoot(), "config");
        d.mkdirs();
        return d.toPath();
    }

    public Path getGameDir() { return new File(Bootstrap.dataRoot()).toPath(); }
    public boolean isDevelopmentEnvironment() { return false; }
    public EnvType getEnvironmentType() { return EnvType.CLIENT; }

    private static final class Container implements ModContainer, ModMetadata {
        private final Bootstrap.Entry e;
        Container(Bootstrap.Entry e) { this.e = e; }
        public ModMetadata getMetadata() { return this; }
        public String getId() { return e.id; }
        public String getName() { return e.name; }
        public Version getVersion() {
            return new Version() { public String getFriendlyString() { return e.version; } };
        }
        public String getDescription() { return ""; }
    }
}
