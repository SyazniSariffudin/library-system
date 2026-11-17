package dev.syaz.library.acceptance;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.LogUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryApiIT {
    private static final Logger LOG = LoggerFactory.getLogger(LibraryApiIT.class);
    private static final int APPLICATION_PORT = 8080;
    private static final String RUN_LABEL = "dev.syaz.library.acceptance-run";

    @Test
    void testPackagedApplication() throws Exception {
        String imageId = readImageId();
        Path composeFile = Path.of(System.getProperty("library.compose.file"));
        assertTrue(Files.isRegularFile(composeFile), "Acceptance Compose file is missing: " + composeFile);
        String runId = UUID.randomUUID().toString();

        // The file constructor uses the installed Docker Compose v2 command.
        try (ComposeContainer stack = new ComposeContainer(composeFile.toFile())
                .withEnv("LIBRARY_IMAGE", imageId)
                .withEnv("POSTGRES_IMAGE", System.getProperty("postgres.image"))
                .withEnv("LIBRARY_TEST_RUN_ID", runId)
                .withPull(false)
                .withRemoveVolumes(true)
                .withStartupTimeout(Duration.ofSeconds(120))
                .withExposedService("application", APPLICATION_PORT,
                        Wait.forHttp("/actuator/health/readiness").forStatusCode(200)
                                .withStartupTimeout(Duration.ofSeconds(90)))) {
            try {
                stack.start();
                var application = stack.getContainerByServiceName("application").orElseThrow();
                assertEquals(imageId, application.getContainerInfo().getImageId(),
                        "Acceptance tests must use the image produced by this build");
                String baseUrl = "http://" + stack.getServiceHost("application", APPLICATION_PORT)
                        + ":" + stack.getServicePort("application", APPLICATION_PORT);
                Results results = Runner.path("classpath:features")
                        .systemProperty("baseUrl", baseUrl)
                        .outputCucumberJson(true)
                        .parallel(3);
                assertEquals(0, results.getFailCount(), results.getErrorMessages());
            } finally {
                saveLogs(runId);
            }
        }
    }

    private static String readImageId() throws Exception {
        Path imageIdFile = Path.of(System.getProperty("library.image.idfile"));
        assertTrue(Files.isRegularFile(imageIdFile), "Build the image first: mvn -pl library-karate-tests -am verify");
        return Files.readString(imageIdFile).strip();
    }

    private static void saveLogs(String runId) {
        try {
            // Query labels directly so logs are available even if Compose fails during startup.
            var docker = DockerClientFactory.instance().client();
            var containers = docker.listContainersCmd().withShowAll(true)
                    .withLabelFilter(Map.of(RUN_LABEL, runId)).exec();
            Path directory = Path.of("target", "container-logs");
            Files.createDirectories(directory);
            for (var container : containers) {
                String service = container.getLabels().get("com.docker.compose.service");
                try {
                    Files.writeString(directory.resolve(service + ".log"), LogUtils.getOutput(docker, container.getId()));
                } catch (Exception error) {
                    LOG.atWarn().addKeyValue("event", "test.container_logs.capture_failed")
                            .addKeyValue("container", service).setCause(error).log("Could not capture container logs");
                }
            }
        } catch (Exception error) {
            LOG.atWarn().addKeyValue("event", "test.container_logs.capture_failed")
                    .addKeyValue("run_id", runId).setCause(error).log("Could not discover containers for log capture");
        }
    }
}
