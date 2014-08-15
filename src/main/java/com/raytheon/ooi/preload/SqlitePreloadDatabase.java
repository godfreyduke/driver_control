package com.raytheon.ooi.preload;

import com.raytheon.ooi.driver_control.DriverConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Concrete implementation of the PreloadDatabase using SQLite.
 * This class will clone the parse_preload repo and execute the parse_preload script
 * to generate a preload database if one does not already exist.
 */
public class SqlitePreloadDatabase extends PreloadDatabase {

    private final static SqlitePreloadDatabase INSTANCE = new SqlitePreloadDatabase();
    private SqlitePreloadDatabase() {}

    private static final String git = "git clone https://github.com/petercable/parse_preload.git";
    private static final String parse = "parse_preload.py";
    
    public static SqlitePreloadDatabase getInstance() {
        return INSTANCE;
    }

    public void connect(DriverConfig config) throws Exception {
        if (!Files.exists(Paths.get(config.getTemp())))
            Files.createDirectory(Paths.get(config.getTemp()));
        if (!Files.exists(Paths.get(config.getDatabaseFile())))
            createDB(config);

        if (!Files.exists(Paths.get(config.getDatabaseFile())))
            throw new SQLException("Database does not exist!");

        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + config.getDatabaseFile());
    }

    public void createDB(DriverConfig config) throws IOException, InterruptedException {
        // do we have parse preload already?  If not, git it
        log.debug("Creating SQLite database with python script");
        String[] args = {};
        Path preloadDir = Paths.get(config.getTemp(), "parse_preload");
        Path preloadDb = Paths.get(preloadDir.toString(), "preload.db");
        if (!Files.exists(preloadDb)) {
            log.debug("Getting parse_preload utility with git");
            Runtime.getRuntime().exec(git, args, Paths.get(config.getTemp()).toFile()).waitFor();
        }
        // now run it... user must have already pip installed openpyxl and docopt...
        log.debug("Running preload.py");
        String path = String.format("PATH=%s/bin:%s", System.getenv("VIRTUAL_ENV"), System.getenv("PATH"));
        String python = String.format("%s/bin/python %s", System.getenv("VIRTUAL_ENV"), parse);
        Process p = Runtime.getRuntime().exec(python, new String[]{path}, preloadDir.toFile());
        p.waitFor();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        while (bufferedReader.ready()) {
            log.debug(bufferedReader.readLine());
        }

        log.debug("Return code: {}", p.exitValue());
        log.debug("Moving preload.db to target location");
        Files.move(preloadDb, Paths.get(config.getDatabaseFile()));
    }
}
