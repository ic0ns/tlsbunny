package com.gypsyengineer.tlsbunny.tls13.fuzzer;

import com.gypsyengineer.tlsbunny.tls13.connection.Engine;
import com.gypsyengineer.tlsbunny.tls13.connection.EngineException;
import com.gypsyengineer.tlsbunny.tls13.connection.EngineFactory;
import com.gypsyengineer.tlsbunny.tls13.connection.check.Check;
import com.gypsyengineer.tlsbunny.tls13.server.Server;
import com.gypsyengineer.tlsbunny.tls13.server.StopCondition;
import com.gypsyengineer.tlsbunny.tls13.struct.StructFactory;
import com.gypsyengineer.tlsbunny.utils.Connection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.gypsyengineer.tlsbunny.utils.WhatTheHell.whatTheHell;

public class MutatedServer implements Server {

    private static final Logger logger = LogManager.getLogger(MutatedServer.class);

    private static final int free_port = 0;

    private final ServerSocket ssocket;
    private final EngineFactory engineFactory;
    private final List<Engine> engines = Collections.synchronizedList(new ArrayList<>());

    // TODO: synchronization
    private Status status = Status.not_started;
    private boolean failed = false;

    private long test = 0;

    private String state;
    private FuzzyStructFactory fuzzer;
    private int total = 0;

    public static MutatedServer from(Server server) throws IOException {
        return mutatedServer(server);
    }

    public static MutatedServer mutatedServer(Server server) throws IOException {
        ServerSocket socket = new ServerSocket(free_port);
        socket.setReuseAddress(true);
        return new MutatedServer(socket, server);
    }

    private MutatedServer(ServerSocket ssocket, Server server) {
        this.engineFactory = server.engineFactory();
        this.ssocket = ssocket;
    }

    @Override
    public MutatedServer set(EngineFactory engineFactory) {
        throw whatTheHell("you can't set an engine factory for me!");
    }

    @Override
    public MutatedServer set(Check check) {
        throw whatTheHell("you can't set a check for me!");
    }

    @Override
    public MutatedServer stopWhen(StopCondition condition) {
        throw whatTheHell("I know when I should stop!");
    }

    @Override
    public MutatedServer stop() {
        if (!ssocket.isClosed()) {
            try {
                ssocket.close();
            } catch (IOException e) {
                logger.warn("exception occurred while stopping the server", e);
            }
        }

        return this;
    }

    @Override
    public int port() {
        return ssocket.getLocalPort();
    }

    @Override
    public Engine[] engines() {
        return engines.toArray(new Engine[0]);
    }

    @Override
    public boolean failed() {
        return failed;
    }

    @Override
    public EngineFactory engineFactory() {
        throw whatTheHell("no engine factories for you!");
    }

    @Override
    public Status status() {
        synchronized (this) {
            return status;
        }
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public void run() {
        if (fuzzer == null) {
            throw whatTheHell("fuzzer is null!");
        }
        engineFactory.set(fuzzer);

        if (total <= 0) {
            throw whatTheHell("total is not correct!");
        }

        if (state != null && !state.isEmpty()) {
            fuzzer.state(state);
        }

        logger.info("run fuzzer config:");
        logger.info("targets     = {}",
                Arrays.stream(fuzzer.targets())
                        .map(Object::toString)
                        .collect(Collectors.joining(", ")));
        logger.info("fuzzer      = {}",
                fuzzer.fuzzer() != null ? fuzzer.fuzzer().toString() : "null");
        logger.info("total tests = {}", total);
        logger.info("state       = {}",
                state != null ? state : "not specified");

        try {
            test = 0;
            logger.info("started on port {}", port());
            while (shouldRun(fuzzer)) {
                synchronized (this) {
                    status = Status.ready;
                }
                try (Connection connection = Connection.create(ssocket.accept())) {
                    synchronized (this) {
                        status = Status.accepted;
                    }
                    run(connection, fuzzer);
                } finally {
                    fuzzer.moveOn();
                    test++;
                }
            }
        } catch (Exception e) {
            logger.warn("what the hell? unexpected exception", e);
            failed = true;
        } finally {
            status = Status.done;
            logger.info("stopped");
        }
    }

    private void run(Connection connection, FuzzyStructFactory fuzzyStructFactory)
            throws EngineException {

        String message = String.format("test #%d (accepted), %s/%s, targets: [%s]",
                test,
                getClass().getSimpleName(),
                fuzzyStructFactory.fuzzer().getClass().getSimpleName(),
                Arrays.stream(fuzzyStructFactory.targets)
                        .map(Enum::toString)
                        .collect(Collectors.joining(", ")));
        logger.info(message);

        Engine engine = engineFactory.create()
                .set(connection)
                .connect();

        engines.add(engine);
    }

    private boolean shouldRun(FuzzyStructFactory mutatedStructFactory) {
        return mutatedStructFactory.canFuzz() && test < total;
    }
}
