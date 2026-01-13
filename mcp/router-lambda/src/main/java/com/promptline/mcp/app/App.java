package com.promptline.mcp.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.core.git.GitProvider;
import com.promptline.mcp.core.git.github.GitHubProvider;
import com.promptline.mcp.core.notify.BackendNotifier;
import com.promptline.mcp.core.publish.AwsS3Publisher;
import com.promptline.mcp.core.publish.PublishToS3Service;
import com.promptline.mcp.core.publish.S3Publisher;
import com.promptline.mcp.model.config.PublishCanonicalResponse;
import com.promptline.mcp.model.git.GetFileRequest;
import com.promptline.mcp.model.git.GetFileResponse;
import com.promptline.mcp.model.publish.PublishToS3Request;
import com.promptline.mcp.model.publish.PublishToS3Response;
import com.promptline.mcp.routing.Router;
import com.promptline.mcp.util.ApiException;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class App {
    private static volatile App INSTANCE;

    public final Config config;
    public final ObjectMapper om;
    public final HttpClient http;
    public final GitProvider git;
    public final BackendNotifier notifier;

    // publish vertical dependencies
    public final S3Client s3;
    public final S3Publisher s3Publisher;
    public final PublishToS3Service publishToS3;

    public final Router router;

    private App() {
        this.config = Config.fromEnv();
        this.om = new ObjectMapper();

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.git = new GitHubProvider(http, config.githubToken(), config.repoOwner(), config.repoName(), om);

        this.s3 = S3Client.builder().build();
        this.s3Publisher = new AwsS3Publisher(s3);
        this.publishToS3 = new PublishToS3Service(
                s3Publisher,
                config.s3Bucket(),
                config.s3RuntimePrefix(),
                om
        );

        this.notifier = new BackendNotifier(http, om, config.backendNotifyUrl(), config.internalToken());

        this.router = new Router()
                .add("GET", "/healthz", (evt, ctx) -> new Healthz(true))

                .add("POST", "/git/get-file", (evt, ctx) -> {
                    var req = Json.read(om, evt.getBody(), GetFileRequest.class);

                    String ref = req.ref();
                    String path = req.path();

                    if (ref == null || ref.isBlank()) throw new ApiException(400, "ref is required");
                    if (path == null || path.isBlank()) throw new ApiException(400, "path is required");

                    var opt = git.getFileText(ref, path);
                    return new GetFileResponse(ref, path, opt.isPresent(), opt.orElse(""));
                })

                .add("POST", "/config/publish-to-s3", (evt, ctx) -> {
                    var req = Json.read(om, evt.getBody(), PublishToS3Request.class);

                    if (config.s3Bucket() == null || config.s3Bucket().isBlank()) {
                        throw new ApiException(500, "S3_BUCKET is not set");
                    }

                    return publishToS3.publish(req);
                })

                .add("POST", "/config/publish-canonical", (evt, ctx) -> {
                    String ref = config.configBranchLive();

                    var uiOpt = git.getFileText(ref, "config/ui.json");
                    var polOpt = git.getFileText(ref, "config/policy.json");

                    List<String> updated = new ArrayList<>();

                    if (uiOpt.isPresent()) {
                        publishToS3.publish(new PublishToS3Request("ui", uiOpt.get()));
                        updated.add("ui");
                    }
                    if (polOpt.isPresent()) {
                        publishToS3.publish(new PublishToS3Request("policy", polOpt.get()));
                        updated.add("policy");
                    }

                    notifier.notifyConfigUpdated("prod", updated, ref);

                    return new PublishCanonicalResponse(
                            ref,
                            updated,
                            config.s3Bucket(),
                            config.s3RuntimePrefix() + "ui.json",
                            config.s3RuntimePrefix() + "policy.json"
                    );
                });
    }

    public static App get() {
        if (INSTANCE == null) {
            synchronized (App.class) {
                if (INSTANCE == null) INSTANCE = new App();
            }
        }
        return INSTANCE;
    }

    public record Healthz(boolean ok) {}

    static final class Json {
        static <T> T read(ObjectMapper om, String body, Class<T> cls) throws Exception {
            if (body == null || body.isBlank()) throw new IllegalArgumentException("empty body");
            return om.readValue(body, cls);
        }
    }
}
