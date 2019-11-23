package com.gypsyengineer.tlsbunny;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;

import static com.gypsyengineer.tlsbunny.utils.WhatTheHell.whatTheHell;

public class SimpleJSSEHttpsServer implements Runnable, AutoCloseable {

    static {
        if (System.getProperty("javax.net.ssl.keyStore") == null) {
            System.setProperty("javax.net.ssl.keyStore", "certs/keystore");
        } else {
            throw whatTheHell("javax.net.ssl.keyStore has been already set!");
        }

        if (System.getProperty("javax.net.ssl.keyStorePassword") == null) {
            System.setProperty("javax.net.ssl.keyStorePassword", "passphrase");
        } else {
            throw whatTheHell("javax.net.ssl.keyStorePassword has been already set!");
        }
    }

    private static final String[] protocols = { "TLSv1.3" };
    private static final String[] cipher_suites = { "TLS_AES_128_GCM_SHA256" };
    private static final String message =
            "Like most of life's problems, this one can be solved with bending!";
    private static final byte[] http_response = String.format(
            "HTTP/1.1 200 OK\n" +
            "Content-Length: %d\n" +
            "Content-Type: text/html\n" +
            "Connection: Closed\n\n%s", message.length(), message).getBytes();

    private static final int free_port = 0;
    private final SSLServerSocket sslServerSocket;
    private boolean started = false;
    private boolean shouldStop = false;

    private SimpleJSSEHttpsServer(SSLServerSocket sslServerSocket) {
        this.sslServerSocket = sslServerSocket;
    }

    public int port() {
        return sslServerSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        if (sslServerSocket != null && !sslServerSocket.isClosed()) {
            sslServerSocket.close();
        }
    }

    @Override
    public void run() {
        System.out.printf("server started on port %d%n", port());

        while (true) {
            synchronized (this) {
                started = true;
                if (shouldStop) {
                    break;
                }
            }
            try (SSLSocket socket = (SSLSocket) sslServerSocket.accept()) {
                System.out.println("accepted");
                InputStream is = new BufferedInputStream(socket.getInputStream());
                OutputStream os = new BufferedOutputStream(socket.getOutputStream());
                byte[] data = new byte[2048];
                int len = is.read(data);
                if (len <= 0) {
                    throw new IOException("no data received");
                }
                System.out.printf("server received %d bytes: %s%n",
                        len, new String(data, 0, len));
                os.write(http_response, 0, len);
                os.flush();
            } catch (SSLException e) {
                System.out.printf("ssl exception: %s%n", e.getMessage());
                System.out.println("continue");
            } catch (IOException e) {
                System.out.println("i/o exception, continue");
                e.printStackTrace(System.out);
            }
        }
    }

    public boolean started() {
        synchronized (this) {
            return started;
        }
    }

    public void stop() {
        synchronized (this) {
            shouldStop = true;
        }
        if (!sslServerSocket.isClosed()) {
            try {
                sslServerSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public static SimpleJSSEHttpsServer start() throws IOException {
        return start(free_port);
    }

    public static SimpleJSSEHttpsServer start(int port) throws IOException {
        SSLServerSocket socket = (SSLServerSocket)
                SSLServerSocketFactory.getDefault().createServerSocket(port);
        socket.setEnabledProtocols(protocols);
        socket.setEnabledCipherSuites(cipher_suites);
        SimpleJSSEHttpsServer server = new SimpleJSSEHttpsServer(socket);
        new Thread(server).start();
        while (!server.started()) {
            TestUtils.sleep(1);
        }
        return server;
    }

}