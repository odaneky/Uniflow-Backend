package com.university.lms.document.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.university.lms.document.domain.DocumentStoreException;
import com.university.lms.document.scan.VirusScanner.ScanResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * No real ClamAV binary in this environment — same shape as {@code StripePaymentGatewayTest}
 * pinning pure logic without a live account. Here a hand-rolled {@link ServerSocket} plays clamd's
 * side of the real {@code INSTREAM} wire protocol, so {@link ClamAvVirusScanner}'s actual socket
 * and framing code runs, not a mocked-away substitute for it.
 */
class ClamAvVirusScannerTest {

    @Test
    void reportsCleanWhenTheDaemonRespondsOk() throws IOException {
        FakeClamd fakeClamd = FakeClamd.respondingWith("stream: OK\0");
        ClamAvVirusScanner scanner =
                new ClamAvVirusScanner(new VirusScanProperties(true, "localhost", fakeClamd.port()));

        ScanResult result = scanner.scan("harmless content".getBytes(StandardCharsets.UTF_8));

        assertThat(result).isEqualTo(ScanResult.CLEAN);
        assertThat(fakeClamd.receivedCommand()).isEqualTo("zINSTREAM\0");
        assertThat(fakeClamd.receivedPayload()).isEqualTo("harmless content".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void reportsInfectedWhenTheDaemonRespondsFound() throws IOException {
        FakeClamd fakeClamd = FakeClamd.respondingWith("stream: Eicar-Test-Signature FOUND\0");
        ClamAvVirusScanner scanner =
                new ClamAvVirusScanner(new VirusScanProperties(true, "localhost", fakeClamd.port()));

        ScanResult result = scanner.scan("fake payload".getBytes(StandardCharsets.UTF_8));

        assertThat(result).isEqualTo(ScanResult.INFECTED);
    }

    @Test
    void framesAPayloadLargerThanOneChunkCorrectly() throws IOException {
        FakeClamd fakeClamd = FakeClamd.respondingWith("stream: OK\0");
        ClamAvVirusScanner scanner =
                new ClamAvVirusScanner(new VirusScanProperties(true, "localhost", fakeClamd.port()));
        byte[] large = new byte[20_000];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) (i % 256);
        }

        ScanResult result = scanner.scan(large);

        assertThat(result).isEqualTo(ScanResult.CLEAN);
        assertThat(fakeClamd.receivedPayload()).isEqualTo(large);
    }

    @Test
    void throwsRatherThanSilentlyPassingWhenTheDaemonIsUnreachable() throws IOException {
        // Bind then immediately release the port so nothing is listening on it.
        int unreachablePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            unreachablePort = probe.getLocalPort();
        }
        ClamAvVirusScanner scanner =
                new ClamAvVirusScanner(new VirusScanProperties(true, "localhost", unreachablePort));

        assertThatThrownBy(() -> scanner.scan("content".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentStoreException.class);
    }

    @Test
    void throwsOnAnUnrecognisedResponse() throws IOException {
        FakeClamd fakeClamd = FakeClamd.respondingWith("garbage\0");
        ClamAvVirusScanner scanner =
                new ClamAvVirusScanner(new VirusScanProperties(true, "localhost", fakeClamd.port()));

        assertThatThrownBy(() -> scanner.scan("content".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentStoreException.class);
    }

    /** Plays clamd's side of {@code INSTREAM} just well enough to exercise the real client code. */
    private static final class FakeClamd {

        private final ServerSocket serverSocket;
        private volatile String receivedCommand;
        private volatile byte[] receivedPayload;

        private FakeClamd(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        static FakeClamd respondingWith(String response) throws IOException {
            ServerSocket serverSocket = new ServerSocket(0);
            FakeClamd fakeClamd = new FakeClamd(serverSocket);
            Thread thread = new Thread(() -> fakeClamd.serveOnce(response));
            thread.setDaemon(true);
            thread.start();
            return fakeClamd;
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String receivedCommand() {
            return receivedCommand;
        }

        byte[] receivedPayload() {
            return receivedPayload;
        }

        private void serveOnce(String response) {
            try (Socket socket = serverSocket.accept()) {
                InputStream in = socket.getInputStream();
                receivedCommand = new String(in.readNBytes(10), StandardCharsets.US_ASCII);
                java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
                while (true) {
                    byte[] lengthBytes = in.readNBytes(4);
                    if (lengthBytes.length < 4) {
                        break;
                    }
                    int length = ByteBuffer.wrap(lengthBytes).getInt();
                    if (length == 0) {
                        break;
                    }
                    payload.write(in.readNBytes(length));
                }
                receivedPayload = payload.toByteArray();
                OutputStream out = socket.getOutputStream();
                out.write(response.getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException ignored) {
                // Test failure surfaces as the client-side assertion timing out or failing instead.
            } finally {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                    // Nothing more to do — the socket is going away either way.
                }
            }
        }
    }
}
