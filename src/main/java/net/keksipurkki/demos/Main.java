package net.keksipurkki.demos;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

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
