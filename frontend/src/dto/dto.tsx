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
    containerName: string | null;
    containerId: string | null;
    status: ModPackStatus;
    isDeployed: boolean;
    lastDeployError: string | null;
    updatedAt: string | null;
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
    isDeployed: boolean | null;
    status: string | null;
    message: string;
};

