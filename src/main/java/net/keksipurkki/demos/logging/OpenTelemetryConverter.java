package net.keksipurkki.demos.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.trace.Span;

import java.util.Map;
import java.util.function.Supplier;

public class OpenTelemetryConverter extends ClassicConverter implements JsonLineKey {

    final private Map<String, Supplier<String>> suppliers = Map.of(
        "spanId", this::getSpanId,
        "traceId", this::getTraceId
    );

    private Supplier<String> supplier;

    @Override
    public void start() {
        supplier = suppliers.getOrDefault(getFirstOption(), () -> "");
    }

    @Override
    public String convert(ILoggingEvent iLoggingEvent) {
        return supplier.get();
    }

    private String getTraceId() {
        return Span.current().getSpanContext().getTraceId();
    }

    private String getSpanId() {
        return Span.current().getSpanContext().getSpanId();
    }

    @Override
    public String key() {
        return getFirstOption();
    }

}
