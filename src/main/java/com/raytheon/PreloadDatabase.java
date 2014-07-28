package com.raytheon;

import org.apache.logging.log4j.LogManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

//CREATE TABLE ParameterDictionary
// (Scenario, ID, confluence, name, parameter_ids,
// temporal_parameter, parameters, Review_Status, SKIP);

//CREATE TABLE ParameterDefs
// (Scenario, confluence, Name, ID, HID, HID_Conflict,
// Parameter_Type, Value_Encoding, Code_Set, Unit_of_Measure,
// Fill_Value, Display_Name, Precision, visible, Parameter_Function_ID,
// Parameter_Function_Map, Lookup_Value, QC_Functions, Standard_Name,
// Data_Product_Identifier, Reference_URLS, Description,
// Review_Status, Review_Comment, Long_Name, SKIP);

public class PreloadDatabase {
    private Connection connection;
    private static org.apache.logging.log4j.Logger log = LogManager.getLogger();

    public PreloadDatabase(Connection conn) {
        this.connection = conn;
    }

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

    private List<String> lookupScenario(String scenario) {
        List<String> streams = new LinkedList<>();
        try (Statement stmt = connection.createStatement()) {
            String sql = String.format(
                    "SELECT name FROM parameterdictionary " +
                    "WHERE scenario='%s';", scenario);
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
                    log.debug("Getting parameter_id: {}", id);
                    String sql2 = String.format(
                            "SELECT name, parameter_type, value_encoding, " +
                            "parameter_function_id, parameter_function_map " +
                            "FROM parameterdefs " +
                            "WHERE id='%s';", id);

                    ResultSet rs2 = stmt2.executeQuery(sql2);
                    DataParameter dp = new DataParameter(
                            rs2.getString("name"),
                            rs2.getString("parameter_type"),
                            rs2.getString("value_encoding"),
                            rs2.getString("parameter_function_id"),
                            rs2.getString("parameter_function_map"));
                    log.debug("Created DataParameter: {}", dp);
                    ds.getParams().put(dp.getName(), dp);
                }
            }
        } catch (SQLException e) {
            log.debug("exception: " + e);
        }
        return ds;
    }
}