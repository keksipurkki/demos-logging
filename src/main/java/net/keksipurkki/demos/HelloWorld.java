package net.keksipurkki.demos;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import static net.keksipurkki.demos.logging.JsonLineLayout.JSON_MESSAGE;

@Slf4j
@ToString
public class HelloWorld extends AbstractVerticle {

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
            log.info(JSON_MESSAGE, resp.toString());
            promise.complete(resp);
        });

        return promise.future();
    }
}
