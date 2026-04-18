export type LoginRequestDto = {
    username: string;
    password: string;
};

export type LoginResponseDto = {
    success: boolean;
    message: string;
    username: string | null;
};

export type ModPackStatus =
    | 'running'
    | 'stopped'
    | 'deployed'
    | 'deploy_failed'
    | 'error'
    | 'saved'
    | 'not_deployed'
    | 'unknown';

export type ModPackCardDto = {
    packId: number;
    name: string;
    path: string;
    packVersion: string;
    minecraftVersion: string;
    javaVersion: number;
    javaXmx: string;
    port: string;
    entryPoint: string;
    entryPointCandidates: string[];
    containerName: string | null;
    containerId: string | null;
    status: ModPackStatus;
    isDeployed: boolean;
    lastDeployError: string | null;
    updatedAt: string | null;
    loaderType: string | null;
};

export type ModPackUploadResponseDto = {
    packId: number | null;
    name: string | null;
    path: string | null;
    packVersion: string | null;
    minecraftVersion: string | null;
    javaVersion: number | null;
    javaXmx: string | null;
    port: string | null;
    entryPoint: string | null;
    entryPointCandidates: string[] | null;
    loaderType: string | null;
    loaderWarnings: string[] | null;
    message: string;
};

export type ModPackMetadataRequestDto = {
    name: string;
    packVersion: string;
    minecraftVersion: string;
    javaVersion: number;
    javaXmx: string;
    port: string;
    entryPoint: string;
};

export type ModPackMetadataResponseDto = {
    packId: number;
    name: string | null;
    path: string | null;
    packVersion: string | null;
    minecraftVersion: string | null;
    javaVersion: number | null;
    javaXmx: string | null;
    port: string | null;
    entryPoint: string | null;
    entryPointCandidates: string[] | null;
    isDeployed: boolean | null;
    status: string | null;
    loaderType: string | null;
    message: string;
};

export type BackendModPackDto = {
    packId: number;
    name: string;
    path: string;
    packVersion: string;
    minecraftVersion: string;
    javaVersion: number;
    javaXmx: string | null;
    port: string;
    entryPoint: string | null;
    entryPointCandidates: string[] | null;
    containerName: string | null;
    containerId: string | null;
    lastDeployError: string | null;
    status: string | null;
    isDeployed: boolean | null;
    updatedAt: string | null;
    loaderType: string | null;
};

export type ContainerStatsDto = {
    packId: number;
    cpuPercent: number;
    memoryUsedBytes: number;
    memoryLimitBytes: number;
    memoryPercent: number;
    timestamp: string;
};

export type SystemStatsDto = {
    cpuPercent: number;
    memoryUsedBytes: number;
    memoryTotalBytes: number;
    memoryPercent: number;
    diskUsedBytes: number;
    diskTotalBytes: number;
    diskPercent: number;
    timestamp: string;
};

export type UpdateStatusDto = {
    currentVersion: string;
    latestVersion: string;
    versionsBehind: number;
    majorVersionsBehind: number;
    minorVersionsBehind: number;
    patchVersionsBehind: number;
    updateAvailable: boolean;
    downloadUrl: string | null;
};
