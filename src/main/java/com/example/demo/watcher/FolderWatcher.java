package com.example.demo.watcher;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.*;

@Component
public class FolderWatcher {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${watcher.base-path}")
    private String basePath;

    @PostConstruct
    public void watch() {

        new Thread(() -> {

            try {

                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path folder = Paths.get(basePath, "CRO");

                folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

                System.out.println("Watching : " + folder);

                // 🔥 RELOAD FILES au démarrage
                reloadExistingFiles(folder);

                while (true) {

                    WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents()) {

                        String fileName = event.context().toString();

                        processFile(fileName);
                    }

                    key.reset();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }

    // ============================
    // 🔁 RELOAD AU DEMARRAGE
    // ============================
    private void reloadExistingFiles(Path folder) {

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {

            for (Path file : stream) {
                processFile(file.getFileName().toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================
    // 🧠 TRAITEMENT CENTRAL
    // ============================
    private void processFile(String fileName) {

        try {

            // 🚫 anti doublon
            if (isAlreadyProcessed(fileName)) {
                System.out.println("SKIPPED (duplicate): " + fileName);
                return;
            }

            String[] parts = fileName.split("\\.");

            if (parts.length < 4) return;

            int operations = Integer.parseInt(parts[0]);
            String type = parts[3];
            String source = "CRO";

            if (fileName.toLowerCase().endsWith(".cro")) {

                updateStats(type, source, operations, 0, 0);
                saveProcessed(fileName, "CRO");

            } else if (fileName.toLowerCase().endsWith(".done")) {

                updateStats(type, source, 0, operations, 0);
                saveProcessed(fileName, "DONE");

            } else if (fileName.toLowerCase().endsWith(".erreur")) {

                updateStats(type, source, 0, 0, operations);
                saveProcessed(fileName, "ERREUR");
            }

            System.out.println("Processed: " + fileName);

        } catch (Exception e) {
            System.out.println("ERROR file: " + fileName);
            e.printStackTrace();
        }
    }

    // ============================
    // 🚫 CHECK DOUBLON
    // ============================
    private boolean isAlreadyProcessed(String fileName) {

        String sql = "SELECT COUNT(*) FROM processed_files WHERE file_name=?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fileName);

        return count != null && count > 0;
    }

    // ============================
    // 💾 SAVE FILE
    // ============================
    private void saveProcessed(String fileName, String status) {

        jdbcTemplate.update(
                "INSERT INTO processed_files(file_name, status) VALUES (?, ?)",
                fileName, status
        );
    }

    // ============================
    // 📊 UPDATE STATS
    // ============================
    private void updateStats(String type, String source, int nonEffectue, int done, int erreur) {

        String check = "SELECT COUNT(*) FROM operations WHERE type_operation=? AND source=?";
        Integer count = jdbcTemplate.queryForObject(check, Integer.class, type, source);

        if (count != null && count > 0) {

            jdbcTemplate.update(
                    "UPDATE operations SET non_effectue=non_effectue+?, done=done+?, erreur=erreur+?, total=total+?+?+? WHERE type_operation=? AND source=?",
                    nonEffectue, done, erreur,
                    nonEffectue, done, erreur,
                    type, source
            );

        } else {

            jdbcTemplate.update(
                    "INSERT INTO operations(type_operation, source, non_effectue, done, erreur, total) VALUES (?, ?, ?, ?, ?, ?)",
                    type, source,
                    nonEffectue, done, erreur,
                    nonEffectue + done + erreur
            );
        }
    }
}