package com.raytheon.ooi.driver_control;

import com.raytheon.ooi.preload.PreloadDatabase;
import com.raytheon.ooi.preload.SqlitePreloadDatabase;
import org.apache.logging.log4j.LogManager;
import org.apache.poi.util.IOUtils;
import org.controlsfx.dialog.Dialogs;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DriverLauncher {
    private static org.apache.logging.log4j.Logger log = LogManager.getLogger();

    private DriverLauncher() {
    }

    public static Process launchDriver(DriverConfig config) throws IOException, InterruptedException {
        PreloadDatabase db = SqlitePreloadDatabase.getInstance();
        String scenarioPath = String.join("/", config.getTemp(), config.getScenario());
        String eggUrl = db.getEggUrl(config.getScenario());
        String eggName = eggUrl.substring(eggUrl.lastIndexOf('/') + 1);
        Path eggPath = Paths.get(config.getTemp(), eggName);

        getEgg(eggUrl, eggPath);
        unzipDriver(eggPath.toString(), scenarioPath);
        return runDriver(scenarioPath, config.getCommandPortFile(), config.getEventPortFile());
    }

    public static void getEgg(String eggUrl, Path eggPath) throws IOException {
        log.debug("Checking to see if we need to download the egg for this scenario...");
        if (Files.exists(eggPath)) {
            log.debug("Egg already exists...");
        } else {
            try (InputStream is = new URL(eggUrl).openStream(); OutputStream os = new FileOutputStream(eggPath.toFile())) {
                log.debug("Downloading egg from {}...", eggUrl);
                IOUtils.copy(is, os);
                log.debug("Downloaded egg...");
            }
        }
    }

    public static Map<String, String> getEnv(String scenarioPath) {
        log.debug("Building ENV: scenarioPath: {}", scenarioPath);

        List<String> eggs = getResourceList("eggs.txt");
        List<String> externs = getResourceList("externs.txt");

        // verify required environmental variables are already set
        String virtualEnv = System.getenv("VIRTUAL_ENV");
        String workingPath = System.getenv("TEST_BASE");
        if (virtualEnv == null || workingPath == null)
        {
            missingEnvironmentVariable(String.format("VIRTUAL_ENV: %s TEST_BASE: %s", virtualEnv, workingPath));
            return null;
        }

        // Build the python path
        StringJoiner joiner = new StringJoiner(":");
        for (String egg: eggs) joiner.add(String.format("%s/eggs/%s", workingPath, egg));
        for (String extern: externs) joiner.add(String.format("%s/extern/%s", workingPath, extern));

        String path = String.format("%s/bin:%s", System.getenv("VIRTUAL_ENV"), System.getenv("PATH"));
        String pythonPath = String.format("%s:%s", scenarioPath, joiner.toString());

        Map<String, String> env = new HashMap<>();
        env.put("PATH", path);
        env.put("PYTHONPATH", pythonPath);
        log.trace("Built ENV: {}", env);
        return env;
    }

    public static void missingEnvironmentVariable(String envVar) {
        Dialogs.create()
                .owner(null)
                .title("Environment")
                .message("Missing required environment variable " + envVar)
                .showError();
    }

    public static List<String> getResourceList(String resourceName) {
        log.trace("Getting resource list: {}", resourceName);
        List<String> resources = new LinkedList<>();
        InputStream is = DriverLauncher.class.getClass().getResourceAsStream("/" + resourceName);
        Scanner scanner = new Scanner(is);
        while (scanner.hasNextLine()) {
            resources.add(scanner.nextLine());
        }
        return resources;
    }

    public static void patch_zmq_driver(String scenarioPath) throws IOException, InterruptedException {
    /*    Patch an unpacked egg to use JSON instead of PYOBJ.
          assumes we are already in the directory containing the egg to be patched */
        log.debug("Patching zmq_driver to use JSON");
        String[] commands = {
                String.format("sed -i .bak s/pyobj/json/g %s/mi/core/instrument/zmq_driver_process.py", scenarioPath),
                String.format("sed -i .bak 's/if isinstance(addr, str) and//g' %s/mi/core/instrument/instrument_driver.py", scenarioPath),
                String.format("sed -i .bak s/INFO/DEBUG/g %s/res/config/mi-logging.yml", scenarioPath),
                String.format("sed -i .bak 's/except IndexError:\n                    " +
                        "time.sleep(.1)/except Exception as e:\n                    " +
                        "time.sleep(.1)\n                    " +
                        "log.debug(\"Exception: %%s\", e)/g' " +
                        "%s/mi/core/instrument/zmq_driver_process.py", scenarioPath),
                String.format("cp %s/res/config/mi-logging.yml %s/mi/mi-logging.yml", scenarioPath, scenarioPath)
        };
        for (String command: commands) {
            Runtime.getRuntime().exec(command).waitFor();
        }
    }

    public static void unzipDriver(String eggPath, String scenarioPath)
            throws IOException, InterruptedException {
        if (Files.exists(Paths.get(scenarioPath))) {
            log.debug("Driver already unpacked, skipping unzip...");
            return;
        }
        log.debug("Unzipping driver");
        Runtime.getRuntime().exec( new String[]{"unzip", "-o", eggPath, "-d", scenarioPath }).waitFor();
        patch_zmq_driver(scenarioPath);
    }

    public static Process runDriver(String scenarioPath, String command, String event) throws IOException {
        String[] args = {"python", "mi/main.py", "--command_port", command, "--event_port", event };
        log.debug("Launching driver: {}", String.join(" ", args));

        ProcessBuilder pb = new ProcessBuilder(args);
        Map<String, String> environment = pb.environment();
        environment.putAll(getEnv(scenarioPath));
        log.debug(environment);
        pb.directory(new File(scenarioPath));
        return pb.inheritIO().start();
    }
}
