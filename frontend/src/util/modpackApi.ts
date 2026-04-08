import type {
    ModPackCardDto,
    ModPackMetadataRequestDto,
    ModPackMetadataResponseDto,
    ModPackStatus,
    ModPackUploadResponseDto,
} from '../dto';

type BackendModPackDto = {
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
};

const API_BASE = '/api/modpacks';

function normalizeStatus(status: string | null): ModPackStatus {
    const normalized = (status ?? 'unknown').toLowerCase();
    if (normalized === 'running') {
        return 'running';
    }
    if (normalized === 'stopped') {
        return 'stopped';
    }
    if (normalized === 'deployed') {
        return 'deployed';
    }
    if (normalized === 'deploy_failed') {
        return 'deploy_failed';
    }
    if (normalized === 'error') {
        return 'error';
    }
    if (normalized === 'saved') {
        return 'saved';
    }
    if (normalized === 'not_deployed') {
        return 'saved';
    }
    return 'unknown';
}

function mapToCardDto(pack: BackendModPackDto): ModPackCardDto {
    return {
        packId: pack.packId,
        name: pack.name,
        path: pack.path,
        packVersion: pack.packVersion,
        minecraftVersion: pack.minecraftVersion,
        javaVersion: pack.javaVersion,
        javaXmx: pack.javaXmx ?? '5G',
        port: pack.port,
        entryPoint: pack.entryPoint ?? 'startserver.sh',
        entryPointCandidates: pack.entryPointCandidates?.length ? pack.entryPointCandidates : ['startserver.sh'],
        containerName: pack.containerName,
        containerId: pack.containerId,
        lastDeployError: pack.lastDeployError,
        status: normalizeStatus(pack.status),
        isDeployed: Boolean(pack.isDeployed),
        updatedAt: pack.updatedAt,
    };
}

async function fetchModpacks(path: string): Promise<ModPackCardDto[]> {
    const response = await fetch(`${API_BASE}${path}`);
    if (!response.ok) {
        throw new Error(`Failed to fetch modpacks (${response.status})`);
    }

    const payload = (await response.json()) as BackendModPackDto[];
    return payload.map(mapToCardDto);
}

export function getAllPacks(): Promise<ModPackCardDto[]> {
    return fetchModpacks('/');
}


async function runPackAction(path: string, method: 'POST' | 'DELETE'): Promise<void> {
    const response = await fetch(`${API_BASE}${path}`, { method });
    if (response.ok) {
        return;
    }

    const message = await response.text();
    throw new Error(message || `Request failed (${response.status}).`);
}

export function deployPack(packId: number): Promise<void> {
    return runPackAction(`/${packId}/deploy`, 'POST');
}

export function startPack(packId: number): Promise<void> {
    return runPackAction(`/${packId}/start`, 'POST');
}

export function stopPack(packId: number): Promise<void> {
    return runPackAction(`/${packId}/stop`, 'POST');
}

export function archivePack(packId: number): Promise<void> {
    return runPackAction(`/${packId}/archive`, 'POST');
}

export function deletePack(packId: number): Promise<void> {
    return runPackAction(`/delete?packId=${packId}`, 'DELETE');
}

export async function getPackLogs(packId: number, tail: number = 200): Promise<string> {
    const response = await fetch(`${API_BASE}/${packId}/logs?tail=${tail}`);
    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || `Failed to fetch logs (${response.status}).`);
    }

    return response.text();
}

function parseUploadResponse(xhr: XMLHttpRequest): ModPackUploadResponseDto {
    if (xhr.response && typeof xhr.response === 'object') {
        return xhr.response as ModPackUploadResponseDto;
    }

    if (xhr.responseText) {
        return JSON.parse(xhr.responseText) as ModPackUploadResponseDto;
    }

    return {
        packId: null,
        name: null,
        path: null,
        packVersion: null,
        minecraftVersion: null,
        javaVersion: null,
        javaXmx: '5G',
        port: null,
        entryPoint: null,
        entryPointCandidates: null,
        message: 'Upload completed.',
    };
}

export async function updatePackMetadata(
    packId: number,
    metadata: ModPackMetadataRequestDto,
): Promise<ModPackMetadataResponseDto> {
    const response = await fetch(`${API_BASE}/${packId}/metadata`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(metadata),
    });

    const payload = (await response.json()) as ModPackMetadataResponseDto;
    if (response.ok) {
        return payload;
    }

    throw new Error(payload.message || `Metadata update failed (${response.status}).`);
}

function sendModpackArchive(
    url: string,
    file: File,
    onProgress?: (progressPercent: number) => void,
): Promise<ModPackUploadResponseDto> {
    return new Promise((resolve, reject) => {
        const formData = new FormData();
        formData.append('file', file);

        const xhr = new XMLHttpRequest();
        xhr.open('POST', url);
        xhr.responseType = 'json';

        xhr.upload.onprogress = (event) => {
            if (!event.lengthComputable || !onProgress) {
                return;
            }

            const progress = Math.round((event.loaded / event.total) * 100);
            onProgress(progress);
        };

        xhr.onerror = () => {
            reject(new Error('Failed to connect to backend while uploading.'));
        };

        xhr.onload = () => {
            try {
                const payload = parseUploadResponse(xhr);
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve(payload);
                    return;
                }

                reject(new Error(payload.message || `Upload failed (${xhr.status}).`));
            } catch {
                reject(new Error(`Upload failed (${xhr.status}).`));
            }
        };

        xhr.send(formData);
    });
}

export function uploadModpack(
    file: File,
    onProgress?: (progressPercent: number) => void,
): Promise<ModPackUploadResponseDto> {
    return sendModpackArchive(`${API_BASE}/upload`, file, onProgress);
}

export function updateModpack(
    packId: number,
    file: File,
    onProgress?: (progressPercent: number) => void,
): Promise<ModPackUploadResponseDto> {
    return sendModpackArchive(`${API_BASE}/${packId}/update`, file, onProgress);
}

