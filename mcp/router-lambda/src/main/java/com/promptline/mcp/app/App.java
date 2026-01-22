package com.promptline.mcp.app;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptline.mcp.core.git.GetFileRequest;
import com.promptline.mcp.core.git.GetFileResponse;
import com.promptline.mcp.core.git.GitProvider;
import com.promptline.mcp.core.git.github.GitHubProvider;
import com.promptline.mcp.core.live.BackendConfigClient;
import com.promptline.mcp.core.live.ConfigDecisionService;
import com.promptline.mcp.core.notify.BackendNotifier;
import com.promptline.mcp.core.pr.ConfigPrService;
import com.promptline.mcp.core.pr.OpenPrDecisionService;
import com.promptline.mcp.core.publish.AwsS3Publisher;
import com.promptline.mcp.core.publish.PublishToS3Service;
import com.promptline.mcp.core.publish.S3Publisher;
import com.promptline.mcp.model.config.PublishCanonicalResponse;
import com.promptline.mcp.model.live.ConfigCheckLiveRequest;
import com.promptline.mcp.model.live.ConfigCheckLiveResponse;
import com.promptline.mcp.model.pr.ConfigCheckOpenPrResponse;
import com.promptline.mcp.model.pr.ConfigEnsurePrResponse;
import com.promptline.mcp.model.publish.PublishToS3Request;
import com.promptline.mcp.model.publish.PublishToS3Response;
import com.promptline.mcp.routing.Router;
import com.promptline.mcp.util.ApiException;

import software.amazon.awssdk.services.s3.S3Client;

public final class App {
    private static volatile App INSTANCE;

    public final Config config;
    public final ObjectMapper om;
    public final HttpClient http;
    public final GitProvider git;
    public final BackendNotifier notifier;
    public final ConfigPrService configPr;


    // new: publish vertical dependencies
    public final S3Client s3;
    public final S3Publisher s3Publisher;
    public final PublishToS3Service publishToS3;

    // Phase 0: live check
    public final BackendConfigClient backendConfig;
    public final ConfigDecisionService configDecision;

    // Phase 1: open PR check
    public final OpenPrDecisionService openPrDecision;

    public final Router router;

    private App() {
        this.config = Config.fromEnv();
        this.om = new ObjectMapper();

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        this.git = new GitHubProvider(http, config.githubToken(), config.repoOwner(), config.repoName(), om);

        // new: S3 publish vertical wiring
        this.s3 = S3Client.builder().build();
        this.s3Publisher = new AwsS3Publisher(s3);
        this.publishToS3 = new PublishToS3Service(
                s3Publisher,
                config.s3Bucket(),
                config.s3RuntimePrefix(),
                om
        );
        this.notifier = new BackendNotifier(http, om, config.backendNotifyUrl(), config.internalToken());

        this.backendConfig = new BackendConfigClient(http, config.backendPublicBaseUrl());
        this.configDecision = new ConfigDecisionService(backendConfig, om);

        this.openPrDecision = new OpenPrDecisionService(git, om, config.configBranchLive());
        this.configPr = new ConfigPrService(git, om, config.configBranchLive(), openPrDecision);

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

                // new: publish runtime cache to S3
                .add("POST", "/config/publish-to-s3", (evt, ctx) -> {
                    var req = Json.read(om, evt.getBody(), PublishToS3Request.class);

                    if (config.s3Bucket() == null || config.s3Bucket().isBlank()) {
                        throw new ApiException(500, "S3_BUCKET is not set");
                    }

                    PublishToS3Response out = publishToS3.publish(req);
                    return out;
                })
                .add("POST", "/config/publish-canonical", (evt, ctx) -> {
                    String ref = config.configBranchLive();

                    // read canonical configs
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

                    // Notify backend (env can be "prod" for now; version can be ref)
                    notifier.notifyConfigUpdated("prod", updated, ref);
                    return new PublishCanonicalResponse(
                            ref,
                            updated,
                            config.s3Bucket(),
                            config.s3RuntimePrefix() + "ui.json",
                            config.s3RuntimePrefix() + "policy.json"
                    );
                })

                // -----------------------
                // Phase 0: check-live
                // -----------------------
                .add("POST", "/config/check-live", (evt, ctx) -> {
                    var req = Json.read(om, evt.getBody(), ConfigCheckLiveRequest.class);
                    ConfigCheckLiveResponse out = configDecision.checkLive(req);
                    return out;
                })

                // -----------------------
                // Phase 1: check open PRs
                // -----------------------
                .add("POST", "/config/check-open-pr", (evt, ctx) -> {
                    var req = Json.read(om, evt.getBody(), ConfigCheckLiveRequest.class);
                    ConfigCheckOpenPrResponse out = openPrDecision.checkOpenPr(req);
                    return out;
                })

                .add("POST", "/config/ensure-pr", (evt, ctx) -> {
                    var req = Json.read(om, evt.getBody(), ConfigCheckLiveRequest.class);
                    ConfigEnsurePrResponse out = configPr.ensurePr(req);
                    return out;
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
