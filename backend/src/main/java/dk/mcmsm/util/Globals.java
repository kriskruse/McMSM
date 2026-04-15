package dk.mcmsm.util;

import dk.mcmsm.services.UpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class Globals {
    private static final Logger logger = LoggerFactory.getLogger(Globals.class);
    private final BuildProperties buildProperties;


    public static final String DEV_VERSION = "dev";
    public static Boolean IS_WINDOWS;
    public static Path WORKING_DIRECTORY;
    public static Boolean IS_RUNNING_FROM_JAR;
    public static String APP_VERSION;
    public static String JAVA_EXE;
    public static String SERVER_PORT;
    public static long PROCESS_ID;


    public Globals(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;

        IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
        WORKING_DIRECTORY = workingDirAsPath();
        IS_RUNNING_FROM_JAR = isRunningFromJar();
        APP_VERSION = getCurrentVersion();
        JAVA_EXE = Path.of(System.getProperty("java.home"), "bin", IS_WINDOWS ? "java.exe" : "java").toAbsolutePath().toString();
        SERVER_PORT = Optional.ofNullable(System.getProperty("server.port")).orElse("8080");
        PROCESS_ID = ProcessHandle.current().pid();
    }



    private Path workingDirAsPath() {

        try {
            String path = URLDecoder.decode(resolveWorkingPath(), StandardCharsets.UTF_8);

            if (path.startsWith("nested:")) {
                path = path.substring("nested:".length());
            }

            int jarIndex = path.indexOf(".jar");
            if (jarIndex != -1) {
                path = path.substring(0, jarIndex + 4);
            }
            logger.info("workingDirAsPath found path: {}", path);

            // On Windows, remove leading slash before drive letter (e.g., /C:/...)
            if (IS_WINDOWS && path.matches("^/[A-Za-z]:.*")) {
                path = path.substring(1);
            }
            return Path.of(path);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot resolve current JAR path.", e);
        }
    }

    private String resolveWorkingPath(){
        return getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
    }

    private boolean isRunningFromJar() {
        return resolveWorkingPath().contains(".jar");
    }

    private String getCurrentVersion() {
        String version = buildProperties.getVersion();
        return version != null ? version : DEV_VERSION;
    }
}
