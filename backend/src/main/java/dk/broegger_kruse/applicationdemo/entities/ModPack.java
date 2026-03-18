package dk.broegger_kruse.applicationdemo.entities;

import dk.broegger_kruse.applicationdemo.interfaces.IModPack;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "modpacks", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class ModPack implements IModPack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long packId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @Column(name = "pack_version", nullable = false)
    private String packVersion;

    @Column(name = "minecraft_version", nullable = false)
    private String minecraftVersion;

    @Column(name = "java_version", nullable = false)
    private Integer javaVersion;

    @Column(name = "java_xmx")
    private String javaXmx;

    @Column(nullable = false)
    private String port;

    @Column(name = "entry_point", nullable = false)
    private String entryPoint;

    @Column(name = "container_name")
    private String containerName;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "is_deployed")
    private Boolean isDeployed;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_deploy_at")
    private Instant lastDeployAt;

    @Column(name = "last_deploy_error", length = 4000)
    private String lastDeployError;

    @Column(name = "updated_at")
    private Instant updatedAt;


    public ModPack(String name,
                   String path,
                   String packVersion,
                   String minecraftVersion,
                   Integer javaVersion,
                   String javaXmx,
                   String port,
                   String entryPoint,
                   String containerName,
                   String containerId,
                   String status,
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
    public String getContainerName() { return containerName; }
    public String getContainerId() { return containerId; }
    public Boolean getIsDeployed() {return isDeployed;}
    public String getStatus() { return status; }
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
    public void setContainerName(String containerName) { this.containerName = containerName; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public void setIsDeployed(boolean isDeployed) {this.isDeployed = isDeployed;}
    public void setStatus(String status) { this.status = status; }
    public void setLastDeployAt(Instant lastDeployAt) { this.lastDeployAt = lastDeployAt; }
    public void setLastDeployError(String lastDeployError) { this.lastDeployError = lastDeployError; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
