package kpi.ipt.labs.distributed.vertx.logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import kpi.ipt.labs.distributed.vertx.client.NamesClient;

public class NamesLogger extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamesLogger.class);
    private static final long DELAY = 3000L;

    private final NamesClient namesClient;
    private long timerId;

    public NamesLogger(NamesClient namesClient) {
        this.namesClient = namesClient;
    }

    @Override
    public void start(Future<Void> completeFuture) throws Exception {
        this.timerId = getVertx().setPeriodic(DELAY, this::printNames);
        this.namesClient.start(completeFuture);
    }

    private void printNames(long id) {
        this.namesClient.getNamesWithCircuitBreaker(namesFuture -> {
            if (namesFuture.failed()) {
                LOGGER.warn("Unable to fetch names list");
            } else {
                JsonArray names = namesFuture.result();

                LOGGER.info("Names fetched: {0}", names);
            }
        });
    }

    @Override
    public void stop() throws Exception {
        getVertx().cancelTimer(this.timerId);
        this.namesClient.stop();
    }
}
