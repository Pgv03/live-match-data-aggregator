package com.example.matchaggregator.poll;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** Runs pollOnce() on a fixed delay, so two cycles never overlap. Off unless poller.enabled=true. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "poller.enabled", havingValue = "true")
public class PollerScheduling implements SchedulingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(PollerScheduling.class);

    private final PollingService polling;
    private final PollerProperties props;

    public PollerScheduling(PollingService polling, PollerProperties props) {
        this.polling = polling;
        this.props = props;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::tick, props.interval());
    }

    private void tick() {
        try {
            polling.pollOnce();
        } catch (RuntimeException e) {
            log.error("Poll cycle failed", e);
        }
    }
}