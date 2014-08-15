package com.raytheon.ooi.preload;

import com.raytheon.ooi.driver_control.DriverConfig;
import org.apache.logging.log4j.LogManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 */
public abstract class PreloadDatabase {
    protected Connection connection;
    protected static org.apache.logging.log4j.Logger log = LogManager.getLogger(PreloadDatabase.class);

    public abstract void connect(DriverConfig config) throws Exception;

    public void getParameter(String id) {
        log.debug("getParameters: {}", id);
        try (Statement stmt = connection.createStatement()) {
            String sql = String.format("SELECT id FROM parameterdefs WHERE id='%s';", id);
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                log.debug(rs.getRow());
                log.debug(rs.getString(1));
            }
        } catch (SQLException e) {
            log.debug("exception: " + e);
        }
    }

    public String getEggUrl(String scenario) {
        String result = null;
        try (Statement stmt = connection.createStatement()) {
            String sql = String.format("SELECT ia_driver_uri FROM instrumentagent WHERE scenario='%s'", scenario);
            ResultSet rs = stmt.executeQuery(sql);
            rs.next();
            result = rs.getString("ia_driver_uri");
        } catch (SQLException e) {
            log.debug("exception getting EggUrl for scenario: {}, {}", scenario, e);
        }
        return result;
    }

    private List<String> lookupScenario(String scenario) {
        List<String> streams = new LinkedList<>();
        try (Statement stmt = connection.createStatement()) {
            String sql = String.format(
                    "SELECT name FROM parameterdictionary " +
                            "WHERE scenario like '%%%s%%';", scenario);
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String name = rs.getString("name");
                log.debug("Found stream name: {} for scenario: {}", name, scenario);
                streams.add(name);
            }
        } catch (SQLException e) {
            log.debug("exception: " + e);
        }
        return streams;
    }

    public Map<String, DataStream> getStreams(String scenario) {
        log.debug("getStreams - scenario: {}", scenario);
        List<String> streams = lookupScenario(scenario);
        Map<String, DataStream> streamMap = new HashMap<>();

        for (String name: streams) {
            streamMap.put(name, getStream(name));
        }
        return streamMap;
    }

    public DataStream getStream(String name) {
        DataStream ds = new DataStream(name);
        log.debug("Created DataStream: {}", ds);
        try (
                Statement stmt = connection.createStatement();
                Statement stmt2 = connection.createStatement()
        ) {
            String sql = String.format(
                    "SELECT parameter_ids FROM parameterdictionary " +
                            "WHERE name='%s';", name);

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String rawParams = rs.getString(1);
                log.debug("rawParams: {}", rawParams);
                String[] params = rawParams.split(",");
                for (String id: params) {
                    DataParameter dp = getParameterById(id);
                    dp.setStream(ds);
                    ds.getParams().put(dp.getName(), dp);
                }
            }
        } catch (SQLException e) {
            log.debug("exception: {}", e);
        }
        return ds;
    }

    public DataParameter getParameterById(String id) {
        try (Statement stmt = connection.createStatement()) {
            log.debug("Getting parameter_id: {}", id);
            String sql = String.format(
                    "SELECT name, parameter_type, value_encoding, " +
                            "parameter_function_id, parameter_function_map " +
                            "FROM parameterdefs " +
                            "WHERE id='%s';", id);

            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                DataParameter dp = new DataParameter(
                        id,
                        rs.getString("name"),
                        rs.getString("parameter_type"),
                        rs.getString("value_encoding"),
                        rs.getString("parameter_function_id"),
                        rs.getString("parameter_function_map"));
                log.trace("Created DataParameter: {}", dp);
                return dp;
            }
            return null;
        } catch (SQLException e) {
            log.debug("Exception: {}", e);
            return null;
        }
    }

    public DataFunction getParameterFunctionById(String id) {
        try (Statement stmt = connection.createStatement()) {
            log.trace("Getting parameter function: {}", id);
            String sql = String.format(
                    "SELECT name, function, owner, args " +
                            "FROM parameterfunctions " +
                            "WHERE id='%s';", id);

            ResultSet rs = stmt.executeQuery(sql);
            DataFunction df = new DataFunction(
                    id,
                    rs.getString("name"),
                    rs.getString("function"),
                    rs.getString("owner"),
                    rs.getString("args"));
            log.trace("Created DataFunction: {}", df);
            return df;
        } catch (SQLException e) {
            log.debug("Exception: {}", e);
            return null;
        }
    }
}
