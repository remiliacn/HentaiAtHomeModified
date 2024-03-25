/*

Copyright 2008-2023 E-Hentai.org
https://forums.e-hentai.org/
tenboro@e-hentai.org

This file is part of Hentai@Home.

Hentai@Home is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home.  If not, see <http://www.gnu.org/licenses/>.

*/

package hath.base;

import static hath.base.Tools.humanReadableByteCountBin;

import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class HTTPSession implements Runnable {
    private final SSLSocket socket;
    private final HTTPServer httpServer;
    private final int connId;
    private final boolean localNetworkAccess;
    private final long sessionStartTime;
    private long lastPacketSend;
    private HTTPResponse httpResponse;

    private static final String CRLF = "\r\n";
    private static final DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("#.##");
    private static final Pattern getheadPattern =
            Pattern.compile("^((GET)|(HEAD)).*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss")
                    .localizedBy(Locale.US)
                    .withZone(ZoneId.of("UTC"));
    private static final int SOCKET_TIMEOUT_MILLISECONDS = 20_000;
    private static final int ONE_SECOND = 1000;
    private static final int THIRTY_SECONDS = 30_000;
    private static final int THREE_MINUTES = 180_000;
    private static final int THIRTY_MINUTES = 1_800_000;

    public HTTPSession(final SSLSocket socket, final int connId, final boolean localNetworkAccess, final HTTPServer httpServer) {
        sessionStartTime = System.currentTimeMillis();
        this.socket = socket;
        this.connId = connId;
        this.localNetworkAccess = localNetworkAccess;
        this.httpServer = httpServer;
    }

    public void handleSession() {
        Thread myThread = new Thread(this);
        myThread.start();
    }

    private void connectionFinished() {
        if (httpResponse != null) {
            httpResponse.requestCompleted();
        }

        httpServer.removeHTTPSession(this);
    }

    public void run() {
        // why are we back to input/output streams? because java has no SSLSocketChannel, using them with SSLEngine is stupidly complex,
        // and all the middleware libraries for SSL over channels are either broken, outdated, or require a major code rewrite
        // may switch back to channels in the future if a decent library materializes, or I can be arsed to learn SSLEngine and
        // implementing it does not require a major rewrite
        BufferedReader reader = null;
        DataOutputStream writer = null;
        HTTPResponseProcessor httpResponseProcessor = null;
        String info = this + "\t";

        try {
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLISECONDS);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new DataOutputStream(socket.getOutputStream());
            // read the header and parse the request - this will also update the response code and initialize the proper response processor
            String request = null;
            // ignore every single line except for the request one. we SSL now, so if there is no end-of-line, just wait for the timeout
            do {
                String read = reader.readLine();
                if (read != null) {
                    if (getheadPattern.matcher(read).matches()) {
                        request = read.substring(0, Math.min(1000, read.length()));
                    } else if (read.isEmpty()) {
                        break;
                    }
                } else {
                    break;
                }
            } while (true);

            httpResponse = new HTTPResponse(this);
            httpResponse.parseRequest(request);

            // get the status code and response processor - in case of an error, this will be a text type with the error message
            httpResponseProcessor = httpResponse.getHTTPResponseProcessor();
            int statusCode = httpResponse.getResponseStatusCode();
            int contentLength = httpResponseProcessor.getContentLength();

            // build the header
            StringBuilder header = new StringBuilder(300);
            header.append(getHTTPStatusHeader(statusCode));
            header.append(httpResponseProcessor.getHeader());
            header.append("Date: ").append(DATE_TIME_FORMATTER.format((new Date()).toInstant()))
                    .append(" GMT")
                    .append(CRLF);
            header.append("Server: Genetic Lifeform and Distributed Open Server " + Settings.CLIENT_VERSION + CRLF);
            header.append("Connection: close" + CRLF);
            header.append("Content-Type: ").append(httpResponseProcessor.getContentType()).append(CRLF);

            if (contentLength > 0) {
                header.append("Cache-Control: public, max-age=31536000" + CRLF);
                header.append("Content-Length: ").append(contentLength).append(CRLF);
            }

            header.append(CRLF);

            // write the header to the socket
            byte[] headerBytes = header.toString().getBytes(StandardCharsets.ISO_8859_1);
            if (request != null && contentLength > 0) {
                try {
                    // buffer size might be limited by OS. for linux, check net.core.wmem_max
                    int bufferSize = (int) Math.min(contentLength + headerBytes.length + 32,
                            Math.min(Settings.isUseLessMemory() ? 131072 : 524288, Math.round(0.2 * Settings.getThrottleBytesPerSec())));
                    socket.setSendBufferSize(bufferSize);
                } catch (Exception e) {
                    Out.info(e.getMessage());
                }
            }

            HTTPBandwidthMonitor bwm = httpServer.getBandwidthMonitor();
            if (bwm != null && !localNetworkAccess) {
                bwm.waitForQuota(headerBytes.length);
            }
            writer.write(headerBytes, 0, headerBytes.length);
            if (!localNetworkAccess) {
                Stats.bytesSent(headerBytes.length);
            }

            if (httpResponse.isRequestHeadOnly()) {
                // if this is a HEAD request, we are done
                writer.flush();
                info += "Code=" + statusCode + " ";
                Out.info(info + (request == null ? "Invalid Request" : request));
            } else {
                // if this is a GET request, process the body if we have one
                info += "Code=" + statusCode + " Size=" + humanReadableByteCountBin(contentLength) + "\t";
                if (request != null) {
                    // skip the startup message for error requests
                    Out.info(info + request);
                }

                long startTime = System.currentTimeMillis();
                if (contentLength > 0) {
                    int writtenBytes = 0;
                    int lastWriteLen;

                    // bytebuffers returned by getPreparedTCPBuffer should never have a remaining() larger than Settings.TCP_PACKET_SIZE.
                    // if that happens due to some bug, we will hit an IndexOutOfBounds exception during the get below
                    byte[] buffer = new byte[Settings.TCP_PACKET_SIZE];

                    while (writtenBytes < contentLength) {
                        lastPacketSend = System.currentTimeMillis();
                        ByteBuffer tcpBuffer = httpResponseProcessor.getPreparedTCPBuffer();
                        lastWriteLen = tcpBuffer.remaining();

                        if (bwm != null && !localNetworkAccess) {
                            bwm.waitForQuota(lastWriteLen);
                        }
                        tcpBuffer.get(buffer, 0, lastWriteLen);
                        writer.write(buffer, 0, lastWriteLen);
                        writtenBytes += lastWriteLen;
                        if (!localNetworkAccess) {
                            Stats.bytesSent(lastWriteLen);
                        }
                    }
                }

                writer.flush();

                // while the outputstream is flushed and empty, the bytes may not have made it further than the OS network buffers,
                // so the time calculated here is approximate at best and widely misleading at worst, especially if the BWM is disabled
                long sendTime = System.currentTimeMillis() - startTime;
                Out.info(info + "Finished processing request in "
                        + DECIMAL_FORMATTER.format(sendTime / 1000.0) + " seconds"
                        + (sendTime >= 10 ? "("
                        + DECIMAL_FORMATTER.format((double) contentLength / sendTime / 1024.0 * 1000.0)
                        + " KB/s)" : ""));
            }
        } catch (SocketException e) {
            Out.info(info + "Socket exception: " + e.getMessage());
            Out.debug(e.getMessage());
        } catch (IOException e) {
            Out.debug(info + "IO Exception.");
            Out.debug(e.getMessage());
        } catch (Exception e) {
            Out.info(info + " WTF??? " + e.getMessage());
        } finally {
            if (httpResponseProcessor != null) {
                httpResponseProcessor.cleanup();
            }

            try {
                if (reader != null) {
                    reader.close();
                }
                writer.close();
            } catch (IOException ignored) {
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        connectionFinished();
    }

    private String getHTTPStatusHeader(int statuscode) {
        return switch (statuscode) {
            case 200 -> "HTTP/1.1 200 OK" + CRLF;
            case 301 -> "HTTP/1.1 301 Moved Permanently" + CRLF;
            case 400 -> "HTTP/1.1 400 Bad Request" + CRLF;
            case 403 -> "HTTP/1.1 403 Permission Denied" + CRLF;
            case 404 -> "HTTP/1.1 404 Not Found" + CRLF;
            case 405 -> "HTTP/1.1 405 Method Not Allowed" + CRLF;
            case 418 -> "HTTP/1.1 418 I'm a teapot" + CRLF;
            case 501 -> "HTTP/1.1 501 Not Implemented" + CRLF;
            case 502 -> "HTTP/1.1 502 Bad Gateway" + CRLF;
            default -> "HTTP/1.1 500 Internal Server Error" + CRLF;
        };
    }

    public boolean doTimeoutCheck() {
        long nowtime = System.currentTimeMillis();

        if (lastPacketSend < nowtime - ONE_SECOND && socket.isClosed()) {
            // the connecion was already closed and should be removed by the HTTPServer instance.
            // the lastPacketSend check was added to prevent spurious "Killing stuck session" errors
            return true;
        }

        int startTimeout = httpResponse != null ? (httpResponse.isServercmd() ? THIRTY_MINUTES : THREE_MINUTES) : THIRTY_SECONDS;
        return (sessionStartTime > 0 && sessionStartTime < nowtime - startTimeout)
                || (lastPacketSend > 0 && lastPacketSend < nowtime - THIRTY_SECONDS);
    }

    public void forceCloseSocket() {
        try {
            if (!socket.isClosed()) {
                Out.debug("Closing socket for session " + connId);
                socket.close();
                Out.debug("Closed socket for session " + connId);
            }
        } catch (Exception e) {
            Out.debug(e.toString());
        }
    }

    // accessors
    public HTTPServer getHTTPServer() {
        return httpServer;
    }

    public InetAddress getSocketInetAddress() {
        return socket.getInetAddress();
    }

    public String toString() {
        return "{" + connId + String.format("%1$-17s", getSocketInetAddress().toString() + "}");
    }
}
