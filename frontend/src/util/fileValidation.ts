export function isZipFile(file: File): boolean {
    return file.name.toLowerCase().endsWith('.zip');
}
