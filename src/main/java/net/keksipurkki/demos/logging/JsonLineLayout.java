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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.fasterxml.jackson.core.util.DefaultIndenter.SYSTEM_LINEFEED_INSTANCE;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNullElse;

public class JsonLineLayout extends PatternLayout {

    public static Marker JSON_MESSAGE = MarkerFactory.getMarker("JSON_MESSAGE");

    private static final Map<String, String> CONVERTER_CLASS_TO_KEY = new HashMap<>();
    private static final ObjectMapper prettyMapper;
    private static final ObjectMapper mapper;

    static {
        prettyMapper = DatabindCodec.prettyMapper().copy();
        mapper = DatabindCodec.mapper().copy();

        // Hide null values from logs
        prettyMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // Json arrays elements on new lines
        var prettyPrinter = new DefaultPrettyPrinter();
        prettyMapper.setDefaultPrettyPrinter(prettyPrinter.withArrayIndenter(SYSTEM_LINEFEED_INSTANCE));

    }

    private boolean prettyPrint;

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    private Converter<ILoggingEvent> head;

    @Override
    public void start() {

        if (this.getPattern() != null && !this.getPattern().isEmpty()) {
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

            for (var entry : getEffectiveConverterMap().entrySet()) {
                CONVERTER_CLASS_TO_KEY.putIfAbsent(entry.getValue(), entry.getKey());
                CONVERTER_CLASS_TO_KEY.computeIfPresent(entry.getValue(), (_k, v) -> longerString(v, entry.getKey()));
            }

            ConverterUtil.setContextForConverters(this.getContext(), this.head);
            ConverterUtil.startConverters(this.head);

        } else {
            addError("Empty or null pattern.");
        }
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        try {
            var json = doJsonLayout(event);
            if (prettyPrint) {
                return unescape(prettyMapper.writeValueAsString(json)) + "\n";
            } else {
                return mapper.writeValueAsString(json) + "\n";
            }
        } catch (JsonProcessingException exception) {
            addError(exception.getMessage(), exception);
            return null;
        }
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

    private JsonObject doJsonLayout(ILoggingEvent event) {
        var json = new JsonObject();

        for (var c = head; c != null; c = c.getNext()) {

            if (c instanceof LineSeparatorConverter) {
                continue;
            }

            var key = jsonLineKey(c);

            if (nonNull(key)) {
                if (key.equals("message") && requireNonNullElse(event.getMarkerList(), emptyList()).contains(JSON_MESSAGE)) {
                    json.put(key, new JsonObject(event.getFormattedMessage()));
                } else {
                    json.put(key, c.convert(event));
                }
            }

        }

        if (json.containsKey("throwable")) {
            if (json.getString("throwable").isEmpty()) {
                json.remove("throwable");
            } else {
                var readableStacktrace = json.getString("throwable").replaceAll("\t", "").split("\n");
                json.put("throwable", readableStacktrace);
            }
        }

        if (prettyPrint) {
            return highlightKeys(json);
        } else {
            return json;
        }
    }

    private JsonObject highlightKeys(JsonObject json) {
        var highlighted = new JsonObject();

        for (var entry : json) {
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

    private String longerString(String a, String b) {
        return a.length() > b.length() ? a : b;
    }

}

