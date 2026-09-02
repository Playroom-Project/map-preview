package io.github.playroomproject.mappreview.platform;

import io.github.playroomproject.mappreview.core.scheduler.HardwareProfile;
import java.nio.file.Path;
import java.util.Map;

/** Loader hooks, paths and hardware probes are outside every algorithm module. */
public interface LoaderPlatform {
    String loaderId();
    String loaderVersion();
    Path configurationDirectory();
    Map<String, String> installedMods();
    boolean isClient();
    HardwareProfile hardware();
    void executeOnClient(Runnable work);
}
