package com.raytheon.ooi.driver_control;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.StringJoiner;

public class DriverConfig {
    private static Logger log = LogManager.getLogger();
    private JSONObject portAgentConfig;
    private JSONObject startupConfig;
    private String scenario;

    private final String host = "localhost";
    private final String temp = "/tmp/driver_control";
    private final String commandPortFile = String.join("/", temp, "command_port");
    private final String eventPortFile = String.join("/", temp, "event_port");
    private final String databaseFile = String.join("/", temp, "preload.db");


    public DriverConfig(File file) throws IOException {
        // open the file, parse the config
        Path path = Paths.get(file.toURI());
        Yaml yaml = new Yaml();
        Map map = (Map) yaml.load(Files.newInputStream(path));
        JSONObject config = new JSONObject(map);

        portAgentConfig = new JSONObject((Map)config.get("port_agent_config"));
        startupConfig = new JSONObject((Map)config.get("startup_config"));
        JSONObject driverConfig = new JSONObject((Map)config.get("driver_config"));
        scenario = (String) driverConfig.get("scenario");
    }

    public String getPortAgentConfig() {
        return portAgentConfig.toString();
    }

    public String getStartupConfig() {
        return startupConfig.toString();
    }

    public String getCommandPortFile() {
        return commandPortFile;
    }

    public String getEventPortFile() {
        return eventPortFile;
    }

    public String toString() {
        StringJoiner joiner = new StringJoiner("\n\n");
        joiner.add("PORT AGENT CONFIG");
        joiner.add(portAgentConfig.toString());
        joiner.add("STARTUP CONFIG");
        joiner.add(startupConfig.toString());
        joiner.add("COMMAND PORT FILE: " + commandPortFile);
        joiner.add("EVENT PORT FILE: " + eventPortFile);
        return joiner.toString();
    }

    public String getHost() {
        return host;
    }

    public String getDatabaseFile() {
        return databaseFile;
    }

    public String getScenario() {
        return scenario;
    }

    public String getTemp() {
        return temp;
    }

    private int getPort(String filename) throws IOException {
        Path path = Paths.get(filename);
        String contents = new String(Files.readAllBytes(path));
        return Integer.parseInt(contents.trim());
    }

    public int getCommandPort() throws IOException {
        return getPort(commandPortFile);
    }

    public int getEventPort() throws IOException {
        return getPort(eventPortFile);
    }
}
