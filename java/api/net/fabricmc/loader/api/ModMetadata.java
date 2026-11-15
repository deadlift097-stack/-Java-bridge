package net.fabricmc.loader.api;

public interface ModMetadata {
    String getId();
    String getName();
    Version getVersion();
    String getDescription();
}
