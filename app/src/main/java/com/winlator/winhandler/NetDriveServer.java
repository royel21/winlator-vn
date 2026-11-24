package com.winlator.winhandler;

import android.content.Context;
import android.util.Log;

import com.winlator.core.TarCompressorUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;

/* Runs the bundled rclone as a local remote control server ("rclone rcd") for the
 * network drives of the file manager (WFM). WFM asks for it through WinHandler and
 * then talks to it over HTTP on 127.0.0.1; it browses and copies files on SMB shares. */
class NetDriveServer {
    private static final String TAG = "NetDriveServer";
    private static final String ASSET_NAME = "rclone.tzst";
    /* bump with a new rclone so the binary is unpacked again */
    private static final String VERSION = "1.76.0";
    private final Context context;
    private Process process;
    private int port;
    private String user;
    private String password;
    private String lastError = "";

    NetDriveServer(Context context) {
        this.context = context;
    }

    private File getDir() {
        return new File(context.getFilesDir(), "netdrive");
    }

    private File installBinary() throws IOException {
        File dir = getDir();
        File binary = new File(dir, "rclone");
        File versionFile = new File(dir, "version");

        if (binary.canExecute() && versionFile.isFile()) {
            byte[] data = java.nio.file.Files.readAllBytes(versionFile.toPath());
            if (new String(data).trim().equals(VERSION)) return binary;
        }

        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        versionFile.delete();
        binary.delete();
        if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, ASSET_NAME, dir) || !binary.isFile())
            throw new IOException("cannot unpack " + ASSET_NAME);
        if (!binary.setExecutable(true, true)) throw new IOException("cannot make " + binary + " executable");
        try (OutputStream out = new FileOutputStream(versionFile)) {
            out.write(VERSION.getBytes());
        }
        return binary;
    }

    private static String randomString() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static boolean isListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    /* why the last start failed, shown by WFM */
    synchronized String getLastError() {
        return lastError;
    }

    private String readOutput(File dir) {
        File out = new File(dir, "rclone.out");
        try {
            byte[] data = java.nio.file.Files.readAllBytes(out.toPath());
            String text = new String(data).trim();
            return text.length() > 300 ? text.substring(text.length() - 300) : text;
        }
        catch (IOException e) {
            return "";
        }
    }

    /* Starts rclone if needed and returns "127.0.0.1:<port> <user> <password>",
     * or an empty string when it can't be started (see getLastError()). */
    synchronized String start() {
        if (process != null && process.isAlive() && isListening(port))
            return "127.0.0.1:" + port + " " + user + " " + password;
        stop();

        try {
            File binary = installBinary();
            File dir = getDir();
            File cacheDir = new File(context.getCacheDir(), "rclone");

            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            user = randomString();
            password = randomString();

            ProcessBuilder builder = new ProcessBuilder(binary.getPath(), "rcd",
                "--rc-addr=127.0.0.1:" + port,
                "--rc-user=" + user,
                "--rc-pass=" + password,
                "--config=" + new File(dir, "rclone.conf").getPath(),
                "--cache-dir=" + cacheDir.getPath(),
                "--copy-links",
                "--log-level=NOTICE",
                "--log-file=" + new File(dir, "rclone.log").getPath());
            builder.environment().put("HOME", dir.getPath());
            builder.redirectErrorStream(true);
            builder.redirectOutput(new File(dir, "rclone.out"));
            process = builder.start();

            for (int i = 0; i < 100; i++) {
                if (isListening(port)) {
                    lastError = "";
                    return "127.0.0.1:" + port + " " + user + " " + password;
                }
                if (!process.isAlive()) break;
                Thread.sleep(100);
            }
            String output = readOutput(dir);
            lastError = process.isAlive() ? "rclone did not answer. " + output :
                "rclone exited (" + process.exitValue() + "). " + output;
            Log.e(TAG, lastError);
        }
        catch (IOException | InterruptedException | RuntimeException e) {
            lastError = e.toString();
            Log.e(TAG, "rclone failed to start", e);
        }
        stop();
        return "";
    }

    synchronized void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }
}
