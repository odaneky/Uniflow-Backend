package com.university.lms.document.scan;

import com.university.lms.document.domain.DocumentStoreException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Scans upload bytes against a real ClamAV daemon over its {@code INSTREAM} wire protocol: a raw
 * TCP connection, the command {@code zINSTREAM\0}, the payload as 4-byte-big-endian-length-prefixed
 * chunks terminated by a zero-length chunk, then one response line ending {@code OK} (clean) or
 * {@code FOUND} (infected). A daemon that cannot be reached is a hard failure — {@link
 * DocumentStoreException}, not a silent {@code NOT_SCANNED} pass-through — because that would let
 * an unscanned file through exactly when the safety net is down.
 *
 * @see <a href="https://docs.clamav.net/manual/Usage/Scanning.html#clamd">ClamAV INSTREAM</a>
 */
@Component
@ConditionalOnProperty(name = "lms.virus-scan.enabled", havingValue = "true")
public class ClamAvVirusScanner implements VirusScanner {

    // ClamAV rejects a stream larger than its configured StreamMaxLength in one chunk anyway;
    // this is just how much of the payload we buffer into on the wire per frame.
    private static final int CHUNK_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final VirusScanProperties properties;

    public ClamAvVirusScanner(VirusScanProperties properties) {
        this.properties = properties;
    }

    @Override
    public ScanResult scan(byte[] content) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.host(), properties.port()), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            int offset = 0;
            while (offset < content.length) {
                int len = Math.min(CHUNK_SIZE, content.length - offset);
                out.write(lengthPrefix(len));
                out.write(content, offset, len);
                offset += len;
            }
            out.write(lengthPrefix(0));
            out.flush();

            String response;
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                response = reader.readLine();
            }
            return interpret(response);
        } catch (IOException ex) {
            throw new DocumentStoreException("Could not reach the virus scanner", ex);
        }
    }

    private static byte[] lengthPrefix(int length) {
        return ByteBuffer.allocate(4).putInt(length).array();
    }

    private static ScanResult interpret(String response) {
        if (response == null) {
            throw new DocumentStoreException("Virus scanner closed the connection without a response");
        }
        // The wire response is NUL-terminated, the same framing clamd uses for the 'z'-prefixed
        // command — not newline-terminated, though BufferedReader.readLine() still returns it
        // intact once the daemon closes the connection at EOF.
        String trimmed = response.replace("\0", "").strip();
        if (trimmed.endsWith("FOUND")) {
            return ScanResult.INFECTED;
        }
        if (trimmed.endsWith("OK")) {
            return ScanResult.CLEAN;
        }
        throw new DocumentStoreException("Virus scanner returned an unrecognised response: " + trimmed);
    }
}
