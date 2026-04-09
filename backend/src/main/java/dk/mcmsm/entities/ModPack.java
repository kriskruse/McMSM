package dk.mcmsm.entities;

import java.time.Instant;

public class ModPack {

    private Long packId;
    private String name;
    private String path;
    private String packVersion;
    private String minecraftVersion;
    private Integer javaVersion;
    private String javaXmx;
    private String port;
    private String entryPoint;
    private String[] entryPointCandidates;
    private String containerName;
    private String containerId;
    private Boolean isDeployed;
    private PackStatus status;
    private Instant lastDeployAt;
    private String lastDeployError;
    private Instant updatedAt;


    public ModPack(String name,
                   String path,
                   String packVersion,
                   String minecraftVersion,
                   Integer javaVersion,
                   String javaXmx,
                   String port,
                   String entryPoint,
                   String[] entryPointCandidates,
                   String containerName,
                   String containerId,
                   PackStatus status,
                   Instant lastDeployAt,
                   String lastDeployError,
                   Instant updatedAt) {
        this.name = name;
        this.path = path;
        this.packVersion = packVersion;
        this.minecraftVersion = minecraftVersion;
        this.javaVersion = javaVersion;
        this.javaXmx = javaXmx;
        this.port = port;
        this.entryPoint = entryPoint;
        this.entryPointCandidates = entryPointCandidates;
        this.containerName = containerName;
        this.containerId = containerId;
        this.isDeployed = Boolean.FALSE;
        this.status = status;
        this.lastDeployAt = lastDeployAt;
        this.lastDeployError = lastDeployError;
        this.updatedAt = updatedAt;
    }

    public ModPack() {

    }

    public Long getPackId() {return packId;}
    public String getName() { return name; }
    public String getPath() { return path; }
    public String getPackVersion() { return packVersion; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public Integer getJavaVersion() { return javaVersion; }
    public String getJavaXmx() { return javaXmx; }
    public String getPort() { return port; }
    public String getEntryPoint() { return entryPoint; }
    public String[] getEntryPointCandidates() { return entryPointCandidates; }
    public String getContainerName() { return containerName; }
    public String getContainerId() { return containerId; }
    public Boolean getIsDeployed() {return isDeployed;}
    public PackStatus getStatus() { return status; }
    public Instant getLastDeployAt() { return lastDeployAt; }
    public String getLastDeployError() { return lastDeployError; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPackId(Long packId) { this.packId = packId;}
    public void setName(String name) { this.name = name; }
    public void setPath(String path) { this.path = path; }
    public void setPackVersion(String packVersion) { this.packVersion = packVersion; }
    public void setMinecraftVersion(String minecraftVersion) { this.minecraftVersion = minecraftVersion; }
    public void setJavaVersion(Integer javaVersion) { this.javaVersion = javaVersion; }
    public void setJavaXmx(String javaXmx) { this.javaXmx = javaXmx; }
    public void setPort(String port) { this.port = port; }
    public void setEntryPoint(String entryPoint) { this.entryPoint = entryPoint; }
    public void setEntryPointCandidates(String[] entryPointCandidates) { this.entryPointCandidates = entryPointCandidates; }
    public void setContainerName(String containerName) { this.containerName = containerName; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public void setIsDeployed(boolean isDeployed) {this.isDeployed = isDeployed;}
    public void setStatus(PackStatus status) { this.status = status; }
    public void setLastDeployAt(Instant lastDeployAt) { this.lastDeployAt = lastDeployAt; }
    public void setLastDeployError(String lastDeployError) { this.lastDeployError = lastDeployError; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
