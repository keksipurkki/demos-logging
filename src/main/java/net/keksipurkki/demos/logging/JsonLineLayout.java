package net.keksipurkki.demos.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.LineSeparatorConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import ch.qos.logback.core.pattern.Converter;
import ch.qos.logback.core.pattern.ConverterUtil;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.parser.Parser;
import ch.qos.logback.core.spi.ScanException;
import ch.qos.logback.core.status.ErrorStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

import static com.fasterxml.jackson.core.util.DefaultIndenter.SYSTEM_LINEFEED_INSTANCE;
import static java.util.Objects.nonNull;

public class JsonLineLayout extends PatternLayout {

    public static Marker JSON_MESSAGE = MarkerFactory.getMarker("JSON_MESSAGE");

    private static final Map<String, String> CONVERTER_CLASS_TO_KEY = new HashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    static {

        // Hide null values from logs
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Json arrays elements on new lines
        var prettyPrinter = new DefaultPrettyPrinter()
                .withArrayIndenter(SYSTEM_LINEFEED_INSTANCE)
                .withObjectIndenter(SYSTEM_LINEFEED_INSTANCE);

        mapper.setDefaultPrettyPrinter(prettyPrinter);

    }

    private boolean prettyPrint;

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    private Converter<ILoggingEvent> head;

    @Override
    public void start() {

        if (getPattern() == null || getPattern().isEmpty()) {
            addError("Empty or null pattern.");
            return;
        }

        try {

            var p = new Parser<ILoggingEvent>(getPattern());

            if (this.getContext() != null) {
                p.setContext(this.getContext());
            }

            head = p.compile(p.parse(), getEffectiveConverterMap());

        } catch (ScanException exception) {
            var sm = this.getContext().getStatusManager();
            sm.add(new ErrorStatus("Failed to parse pattern \"" + this.getPattern() + "\".", this, exception));
        }

        BinaryOperator<String> longerString = (a, b) -> a.length() > b.length() ? a : b;

        for (var entry : getEffectiveConverterMap().entrySet()) {
            CONVERTER_CLASS_TO_KEY.putIfAbsent(entry.getValue(), entry.getKey());
            CONVERTER_CLASS_TO_KEY.computeIfPresent(entry.getValue(), (_k, v) -> longerString.apply(v, entry.getKey()));
        }

        ConverterUtil.setContextForConverters(this.getContext(), this.head);
        ConverterUtil.startConverters(this.head);
    }

    @SneakyThrows
    @Override
    public String doLayout(ILoggingEvent event) {
        var json = doJsonLayout(event);
        var writer = prettyPrint ? mapper.writerWithDefaultPrettyPrinter() : mapper.writer();
        return unescape(writer.writeValueAsString(json)) + "\n";
    }

    private <E> Converter<E> leafConverter(Converter<E> converter) {
        if (converter instanceof CompositeConverter<E> composite) {
            return leafConverter(composite.getChildConverter());
        } else {
            return converter;
        }
    }

    private <E> String jsonLineKey(Converter<E> converter) {
        var leaf = leafConverter(converter);
        if (leaf instanceof JsonLineKey jsonLineKey) {
            return jsonLineKey.key();
        } else {
            return CONVERTER_CLASS_TO_KEY.get(leaf.getClass().getName());
        }
    }

    private Map<String, Object> doJsonLayout(ILoggingEvent event) {
        var json = new HashMap<String, Object>();

        for (var c = head; c != null; c = c.getNext()) {

            if (c instanceof LineSeparatorConverter) {
                continue;
            }

            var key = jsonLineKey(c);

            if (nonNull(key)) {
                json.put(key, c.convert(event));
            }

        }

        if (json.containsKey("throwable") && json.get("throwable") instanceof String str) {
            if (str.isEmpty()) {
                json.remove("throwable");
            } else {
                var readableStacktrace = str.replaceAll("\t", "").split("\n");
                json.put("throwable", readableStacktrace);
            }
        }

        if (prettyPrint) {
            return highlightKeys(json);
        } else {
            return json;
        }
    }

    private Map<String, Object> highlightKeys(Map<String, Object> json) {
        var highlighted = new HashMap<String, Object>();

        for (var entry : json.entrySet()) {
            highlighted.put(bold(entry.getKey()), entry.getValue());
        }

        return highlighted;
    }

    private String bold(String str) {
        return String.format("\u001B[%sm%s\u001B[%sm", ANSIConstants.BOLD + ANSIConstants.BLACK_FG, str, ANSIConstants.RESET);
    }

    private String unescape(String input) {
        return input.replaceAll("\\\\u001B", "\u001B");
    }

}

