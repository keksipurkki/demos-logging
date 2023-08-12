package net.keksipurkki.demos;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.keksipurkki.demos.logging.JsonLineLayout.JSON_MESSAGE;

@ToString
public class HelloWorld extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(HelloWorld.class);

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        log.info("Deploying {}", this);
        vertx.eventBus().consumer("HELLO_WORLD", m -> helloWorld().onSuccess(m::reply));
        startPromise.complete();
    }

    private Future<JsonObject> helloWorld() {
        var resp = new JsonObject()
                .put("message", "Hello world!");

        var promise = Promise.<JsonObject>promise();

        vertx.setTimer(500L, l -> {
            log.info(JSON_MESSAGE, resp.encode());
            promise.complete(resp);
        });

        return promise.future();
    }
}
