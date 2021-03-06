package kpi.ipt.labs.distributed.rabbitmq.publishsubscribe;

public abstract class PublishSubscribeConstants {
    private PublishSubscribeConstants() {
    }

    public static final String EXCHANGE_NAME = "my.publish-subscribe.exchange";

    public static final String[] ROUTING_KEYS = new String[]{
            "foo.*",
            "*.bar"
    };
}
