/*
 * Copyright (c) 2018-present, easy-4-java (https://github.com/easy-4-java).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.easy4j.codex.appserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal in-process RFC 6455 WebSocket server emulating the Codex app-server
 * for end-to-end tests: performs the upgrade handshake, answers
 * {@code thread/start} / {@code thread/resume} with a fixed thread id and, on
 * {@code turn/start}, replays the scripted notification sequence
 * (unknown notification &rarr; filtered item &rarr; agent messages &rarr;
 * {@code turn/completed}).
 *
 * @since 3.0.0
 */
final class FakeCodexAppServer implements AutoCloseable {

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final JsonMapper MAPPER = new JsonMapper();

    private final ServerSocket serverSocket;
    private final List<String> receivedFrames = new CopyOnWriteArrayList<>();
    private final Thread acceptLoop;

    private volatile boolean authorizationSeen;
    private volatile boolean closed;

    FakeCodexAppServer() throws IOException {
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        this.acceptLoop = new Thread(this::acceptLoop, "fake-codex-app-server");
        this.acceptLoop.setDaemon(true);
        this.acceptLoop.start();
    }

    /** Base URL the client should connect to. */
    String baseUrl() {
        return "ws://127.0.0.1:" + serverSocket.getLocalPort();
    }

    /** All JSON-RPC frames received from clients, in arrival order. */
    List<String> receivedFrames() {
        return Collections.unmodifiableList(receivedFrames);
    }

    /** Whether any handshake carried {@code Authorization: Bearer test-token}. */
    boolean authorizationSeen() {
        return authorizationSeen;
    }

    private void acceptLoop() {
        while (!closed) {
            try (Socket socket = serverSocket.accept()) {
                handleConnection(socket);
            } catch (IOException ex) {
                if (!closed) {
                    // Accept failures on a listening socket mean it was closed; stop quietly.
                    return;
                }
            }
        }
    }

    private void handleConnection(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        String key = performHandshake(socket);
        if (key == null) {
            return;
        }
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        while (true) {
            String frame = readTextFrame(in);
            if (isNull(frame)) {
                return;
            }
            receivedFrames.add(frame);
            respondTo(out, frame);
        }
    }

    /** Performs the HTTP upgrade; returns the client {@code Sec-WebSocket-Key}, or {@code null} on a bad request. */
    private String performHandshake(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        StringBuilder headers = new StringBuilder();
        String key = null;
        boolean bearer = false;
        while (true) {
            int value = in.read();
            if (value < 0) {
                return null;
            }
            headers.append((char) value);
            if (headers.length() >= 4 && headers.substring(headers.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        for (String line : headers.toString().split("\r\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("sec-websocket-key:")) {
                key = line.substring(line.indexOf(':') + 1).trim();
            }
            if (lower.startsWith("authorization:") && line.contains("Bearer test-token")) {
                bearer = true;
            }
        }
        authorizationSeen = bearer;
        String accept = Base64.getEncoder().encodeToString(sha1(key + WS_MAGIC));
        byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
        socket.getOutputStream().write(response);
        socket.getOutputStream().flush();
        return key;
    }

    private void respondTo(OutputStream out, String frame) throws IOException {
        JsonNode node = MAPPER.readTree(frame);
        String method = node.path("method").asText("");
        long id = node.path("id").asLong(-1);
        if ("thread/start".equals(method) || "thread/resume".equals(method)) {
            sendText(out, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"threadId\":\"th_e2e\"}}");
            return;
        }
        if ("turn/start".equals(method)) {
            sendText(out, "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{}}");
            sendText(out, "{\"method\":\"thread/tokenUsage/updated\",\"params\":{\"tokens\":1}}");
            sendText(out, "{\"method\":\"item/completed\",\"params\":"
                    + "{\"item\":{\"type\":\"commandExecution\",\"text\":\"ignored\"}}}");
            sendText(out, "{\"method\":\"item/completed\",\"params\":"
                    + "{\"item\":{\"type\":\"agentMessage\",\"text\":\"你好\"}}}");
            sendText(out, "{\"method\":\"item/completed\",\"params\":"
                    + "{\"item\":{\"itemType\":\"agent_message\",\"content\":\"世界\"}}}");
            sendText(out, "{\"method\":\"turn/completed\",\"params\":"
                    + "{\"threadId\":\"th_e2e\",\"message\":\"fallback-unused\"}}");
        }
    }

    /** Reads one client frame; returns its text payload, or {@code null} on EOF / close frame. */
    private String readTextFrame(InputStream in) throws IOException {
        int first = in.read();
        if (first < 0) {
            return null;
        }
        int second = readByte(in);
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) readByte(in) << 8) | readByte(in);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte(in);
            }
        }
        byte[] maskKey = new byte[4];
        if (masked) {
            for (int i = 0; i < 4; i++) {
                maskKey[i] = (byte) readByte(in);
            }
        }
        byte[] payload = new byte[(int) length];
        int read = 0;
        while (read < payload.length) {
            int chunk = in.read(payload, read, payload.length - read);
            if (chunk < 0) {
                return null;
            }
            read += chunk;
        }
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }
        if (opcode == 0x8) {
            return null;
        }
        if (opcode != 0x1 && opcode != 0x0) {
            return readTextFrame(in);
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    /** Sends one unmasked text frame to the client. */
    private void sendText(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(0x81);
        if (payload.length < 126) {
            header.write(payload.length);
        } else if (payload.length < 65536) {
            header.write(126);
            header.write((payload.length >> 8) & 0xFF);
            header.write(payload.length & 0xFF);
        } else {
            header.write(127);
            for (int i = 7; i >= 0; i--) {
                header.write((payload.length >>> (i * 8)) & 0xFF);
            }
        }
        out.write(header.toByteArray());
        out.write(payload);
        out.flush();
    }

    private static boolean isNull(String value) {
        return value == null;
    }

    private static int readByte(InputStream in) throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new IOException("Fake Codex app-server: stream ended mid-frame");
        }
        return value;
    }

    private static byte[] sha1(String value) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        serverSocket.close();
    }
}
