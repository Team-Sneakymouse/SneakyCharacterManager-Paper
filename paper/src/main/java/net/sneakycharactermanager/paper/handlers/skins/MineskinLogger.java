package net.sneakycharactermanager.paper.handlers.skins;

import net.sneakycharactermanager.paper.SneakyCharacterManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MineskinLogger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static File logFile;

    public static void init() {
        if (logFile == null) {
            logFile = new File(SneakyCharacterManager.getInstance().getDataFolder(), "mineskin_responses.log");
        }
    }

    public static void log(String response) {
        log(response, null);
    }

    public static void log(String response, String headers) {
        if (!SneakyCharacterManager.getInstance().getConfig().getBoolean("mineskin.logging.enabled", true)) {
            return;
        }

        init();

        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            if (headers != null && !headers.isEmpty()) {
                pw.println("[" + timestamp + "] HEADERS: " + headers);
            }
            pw.println("[" + timestamp + "] BODY: " + response);
        } catch (IOException e) {
            SneakyCharacterManager.getInstance().getLogger().warning("Failed to log Mineskin response: " + e.getMessage());
        }
    }
}
