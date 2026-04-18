const UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];

export function formatBytes(bytes: number, digits = 1): string {
    if (!Number.isFinite(bytes) || bytes <= 0) {
        return '0 B';
    }
    const exp = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), UNITS.length - 1);
    const value = bytes / Math.pow(1024, exp);
    return `${value.toFixed(exp === 0 ? 0 : digits)} ${UNITS[exp]}`;
}
