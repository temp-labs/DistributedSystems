package kpi.ipt.labs.distributed.vertx.client;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import kpi.ipt.labs.distributed.vertx.NamesConstants;

import java.util.Collections;

public abstract class NamesClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamesClient.class);

    private static final String CIRCUIT_BREAKER_NAME = "get-names-circuit-breaker";

    protected final Vertx vertx;
    private HttpClient client;
    private CircuitBreaker circuitBreaker;

    public NamesClient(Vertx vertx) {
        this.vertx = vertx;
    }

    public void start(Future<Void> completeFuture) throws Exception {
        CircuitBreakerOptions options = defaultCircuitBreakerOptions();
        this.circuitBreaker = CircuitBreaker.create(CIRCUIT_BREAKER_NAME, vertx, options);

        getHttpClient().setHandler(clientResult -> {
            if (clientResult.succeeded()) {
                client = clientResult.result();
                completeFuture.complete();
            } else {
                LOGGER.error("Names-service not found");
                completeFuture.fail(clientResult.cause());
            }
        });
    }

    protected abstract Future<HttpClient> getHttpClient();

    public void stop() {
        if (client != null) {
            client.close();
        }

        circuitBreaker.close();
    }

    public void addName(String name, Handler<AsyncResult<Void>> handler) {
        client.put(NamesConstants.NAMES_ENDPOINT,
                response -> {
                    if (response.statusCode() == HttpResponseStatus.CREATED.code()) {
                        handler.handle(Future.succeededFuture());
                    } else {
                        handler.handle(Future.failedFuture(response.statusMessage()));
                    }
                })
                .exceptionHandler(exceptionHandler(handler))
                .end(Json.encode(Collections.singletonMap("name", name)));
    }

    public void getNames(Handler<AsyncResult<JsonArray>> handler) {
        Handler<Throwable> exceptionHandler = exceptionHandler(handler);

        client.get(NamesConstants.NAMES_ENDPOINT,
                response -> {
                    response.bodyHandler(body -> handler.handle(Future.succeededFuture(body.toJsonArray())));
                    response.exceptionHandler(exceptionHandler);
                })
                .exceptionHandler(exceptionHandler)
                .end();
    }

    public void getNamesWithCircuitBreaker(Handler<AsyncResult<JsonArray>> handler) {
        Handler<Future<JsonArray>> command = cmdFuture -> {

            Handler<HttpClientResponse> responseHandler = response -> {
                response.exceptionHandler(cmdFuture::fail);
                response.bodyHandler(body -> cmdFuture.complete(body.toJsonArray()));
            };

            client.get(NamesConstants.NAMES_ENDPOINT, responseHandler)
                    .exceptionHandler(cmdFuture::fail)
                    .end();
        };

        this.circuitBreaker.executeWithFallback(command, throwable -> null)
                .setHandler(handler);
    }

    private static <T> Handler<Throwable> exceptionHandler(Handler<AsyncResult<T>> handler) {
        return t -> handler.handle(Future.failedFuture(t));
    }

    private static CircuitBreakerOptions defaultCircuitBreakerOptions() {
        return new CircuitBreakerOptions()
                .setMaxFailures(5)
                .setTimeout(1000)
                .setFallbackOnFailure(true)
                .setResetTimeout(10000);
    }
}
