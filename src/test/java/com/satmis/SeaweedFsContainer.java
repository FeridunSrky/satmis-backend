package com.satmis;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;

// Same image and access config as docker-compose, so tests see the storage the dev app talks to.
public class SeaweedFsContainer extends GenericContainer<SeaweedFsContainer> {

    public static final String BUCKET = "satmis-images";
    private static final int S3_PORT = 8333;
    private static final String CONFIG_PATH = "/etc/seaweedfs/s3.json";
    // Local-only keys from docker/seaweedfs/s3.json
    private static final String ACCESS_KEY = "satmis-dev";
    private static final String SECRET_KEY = "satmis-dev-secret";

    public SeaweedFsContainer() {
        super(DockerImageName.parse("chrislusf/seaweedfs:4.51"));
        withCopyFileToContainer(MountableFile.forHostPath("docker/seaweedfs/s3.json"), CONFIG_PATH);
        withCommand("server", "-s3", "-s3.config=" + CONFIG_PATH, "-dir=/data");
        withExposedPorts(S3_PORT);
        waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT));
    }

    public void registerStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.endpoint", this::endpoint);
        registry.add("app.storage.bucket", () -> BUCKET);
        registry.add("app.storage.access-key", () -> ACCESS_KEY);
        registry.add("app.storage.secret-key", () -> SECRET_KEY);
        registry.add("app.storage.public-base-url", () -> endpoint() + "/" + BUCKET);
    }

    public String endpoint() {
        return "http://" + getHost() + ":" + getMappedPort(S3_PORT);
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        try {
            ExecResult result = execInContainer("sh", "-c",
                    "echo 's3.bucket.create -name " + BUCKET + "' | weed shell -master=localhost:9333");
            if (!result.getStdout().contains("created bucket")) {
                throw new IllegalStateException("Creating bucket failed: " + result.getStdout() + result.getStderr());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Creating bucket failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating the bucket", ex);
        }
    }
}
