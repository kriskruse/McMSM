package dk.broegger_kruse.applicationdemo.interfaces;

import java.time.Instant;

public interface IModPack {
    Long getPackId();
    String getName();
    String getPath();
    String getPackVersion();
    String getMinecraftVersion();
    Integer getJavaVersion();
    String getPort();
    String getEntryPoint();
    String getContainerName();
    String getContainerId();
    Boolean getIsDeployed();
    String getStatus();
    Instant getLastDeployAt();
    String getLastDeployError();
    Instant getUpdatedAt();

    void setPackId(Long id);
    void setName(String name);
    void setPath(String path);
    void setPackVersion(String packVersion);
    void setMinecraftVersion(String minecraftVersion);
    void setJavaVersion(Integer javaVersion);
    void setPort(String port);
    void setEntryPoint(String entryPoint);
    void setContainerName(String containerName);
    void setContainerId(String containerId);
    void setIsDeployed(boolean isDeployed);
    void setStatus(String status);
    void setLastDeployAt(Instant lastDeployAt);
    void setLastDeployError(String lastDeployError);
    void setUpdatedAt(Instant updatedAt);
}

