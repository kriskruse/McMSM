package dk.broegger_kruse.applicationdemo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipArchiveUtil {
    private static final Logger logger = LoggerFactory.getLogger(ZipArchiveUtil.class);

    private ZipArchiveUtil() {
    }

    public static void extractArchiveToDirectory(Path archivePath, Path outputDirectory) throws IOException {
        Objects.requireNonNull(archivePath, "archivePath must not be null");
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");

        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            String rootPrefix = resolveSingleRootPrefix(zipFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                Optional<ZipExtractionEntry> extractionEntry = toExtractionEntry(zipEntry, rootPrefix);
                if (extractionEntry.isEmpty()) {
                    continue;
                }
                ZipExtractionEntry resolvedEntry = extractionEntry.get();

                Path relativePath = resolvedEntry.relativePath();
                Path targetPath = outputDirectory.resolve(relativePath).normalize();
                if (!targetPath.startsWith(outputDirectory)) {
                    throw new IllegalStateException("Blocked zip entry outside target directory: " + zipEntry.getName());
                }

                if (resolvedEntry.directory()) {
                    Files.createDirectories(targetPath);
                    continue;
                }

                Path parentPath = Objects.requireNonNullElse(targetPath.getParent(), outputDirectory);
                Files.createDirectories(parentPath);
                try (InputStream entryInputStream = zipFile.getInputStream(zipEntry)) {
                    Files.copy(entryInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String resolveSingleRootPrefix(ZipFile zipFile) {
        Set<String> rootSegments = new HashSet<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            String normalizedName = normalizeZipEntryName(zipEntry.getName());
            if (normalizedName.isEmpty()) {
                continue;
            }

            int separatorIndex = normalizedName.indexOf('/');
            if (separatorIndex < 0) {
                return "";
            }

            String rootSegment = normalizedName.substring(0, separatorIndex);
            if (rootSegment.isBlank()) {
                return "";
            }

            rootSegments.add(rootSegment);
            if (rootSegments.size() > 1) {
                return "";
            }
        }

        if (rootSegments.size() != 1) {
            return "";
        }

        String rootPrefix = rootSegments.iterator().next() + "/";
        logger.debug("Detected single zip wrapper directory '{}'; stripping it during extraction.", rootPrefix);
        return rootPrefix;
    }

    private static Optional<ZipExtractionEntry> toExtractionEntry(ZipEntry zipEntry, String rootPrefix) {
        String normalizedName = normalizeZipEntryName(zipEntry.getName());
        if (normalizedName.isEmpty()) {
            return Optional.empty();
        }

        String relativeName = stripRootPrefix(normalizedName, rootPrefix);
        if (relativeName.isEmpty()) {
            return Optional.empty();
        }

        Path relativePath = Path.of(relativeName).normalize();
        if (relativePath.isAbsolute()) {
            throw new IllegalStateException("Blocked absolute zip entry path: " + zipEntry.getName());
        }

        return Optional.of(new ZipExtractionEntry(relativePath, zipEntry.isDirectory()));
    }

    private static String normalizeZipEntryName(String entryName) {
        String normalizedName = Objects.requireNonNullElse(entryName, "").replace('\\', '/');
        while (normalizedName.startsWith("/")) {
            normalizedName = normalizedName.substring(1);
        }
        return normalizedName;
    }

    private static String stripRootPrefix(String entryName, String rootPrefix) {
        if (rootPrefix.isEmpty()) {
            return entryName;
        }

        if (!entryName.startsWith(rootPrefix)) {
            return entryName;
        }

        return entryName.substring(rootPrefix.length());
    }

    private record ZipExtractionEntry(Path relativePath, boolean directory) {
    }
}




