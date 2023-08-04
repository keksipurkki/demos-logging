package net.keksipurkki.demos;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.vertx.core.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;

@Slf4j
@ToString
public class Application extends AbstractVerticle implements Handler<RoutingContext> {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        log.info("Starting {}", this);

        var server = vertx.createHttpServer();
        var router = Router.router(vertx);
        router.route().handler(this);
        server.requestHandler(router);

        var deployment = Future.succeededFuture();

        deployment
            .flatMap(v -> vertx.deployVerticle(new HelloWorld()))
            .flatMap(v -> server.listen(8080))
            .onComplete(ar -> {
                log.info("Deployment complete. Success = {}", ar.succeeded());
                if (ar.failed()) {
                    log.error("Deployment failed", ar.cause());
                    startPromise.fail(ar.cause());
                } else {
                    startPromise.complete();
                }
            });
    }

    @Override
    public void stop() throws Exception {
        log.info("Stopping {}", this);
    }

    @Override
    public void handle(RoutingContext rc) {

        log.info("Handling request. Method = {}. URI = {}", rc.request().method(), rc.request().absoluteURI());

        vertx.eventBus().request("HELLO_WORLD", "").onSuccess(m -> {
            rc.json(m.body());
        });
    }

    public static void main(String... args) {

        log.info("Starting {}", Main.class);
        var sdkTracerProvider = SdkTracerProvider.builder().build();

        var openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();

        var opts = new VertxOptions()
            .setTracingOptions(new OpenTelemetryOptions(openTelemetry));

        var vertx = Vertx.vertx(opts);

        vertx.deployVerticle(new Application());

        var console = new Scanner(System.in);
        while (console.hasNext()) {
        }
        vertx.close();
    }


}
