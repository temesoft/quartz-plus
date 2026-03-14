package org.quartzplus.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EmbeddedPortRetriever implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedPortRetriever.class);
    private static final int PORT_GUARD_WAIT_TIMEOUT = 10000;

    private volatile int retrievedPort = 0;
    private final Lock lock = new ReentrantLock();
    private final Condition portNotSet = lock.newCondition();

    public EmbeddedPortRetriever(final SpringApplication application) {
        application.addListeners(this);
    }

    @Override
    public void onApplicationEvent(final WebServerInitializedEvent event) {
        lock.lock();
        try {
            retrievedPort = event.getWebServer().getPort();
            portNotSet.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int getRetrievedPort() throws InterruptedException {
        lock.lock();
        try {
            var nanoseconds = TimeUnit.MILLISECONDS.toNanos(PORT_GUARD_WAIT_TIMEOUT);
            while (retrievedPort == 0) {
                if (nanoseconds <= 0L) {
                    throw new RuntimeException("Ephemeral server port was not set, EmbeddedServletContainerInitializedEvent was not triggered");
                }
                LOGGER.debug("Waiting for the EmbeddedServletContainerInitializedEvent to be triggered and set the port");
                nanoseconds = portNotSet.awaitNanos(nanoseconds);
            }
        } finally {
            lock.unlock();
        }
        return retrievedPort;
    }
}
