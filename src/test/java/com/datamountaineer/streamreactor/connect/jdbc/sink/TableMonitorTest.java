package com.datamountaineer.streamreactor.connect.jdbc.sink;

import com.datamountaineer.streamreactor.connect.jdbc.sink.config.ErrorPolicyEnum;
import com.datamountaineer.streamreactor.connect.jdbc.sink.config.FieldAlias;
import com.datamountaineer.streamreactor.connect.jdbc.sink.config.FieldsMappings;
import com.datamountaineer.streamreactor.connect.jdbc.sink.config.InsertModeEnum;
import com.datamountaineer.streamreactor.connect.jdbc.sink.config.JdbcSinkSettings;
import com.datamountaineer.streamreactor.connect.jdbc.sink.writer.DbTableInfoProvider;
import com.datamountaineer.streamreactor.connect.jdbc.sink.writer.JdbcDbWriter;
import com.datamountaineer.streamreactor.connect.jdbc.sink.writer.JdbcDbWriterTest;
import com.google.common.collect.Lists;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by andrew@datamountaineer.com on 15/05/16.
 * kafka-connect-jdbc
 */
public class TableMonitorTest {

  private static final String DB_FILE = "test_db_writer_sqllite.db";
  private static final String SQL_LITE_URI = "jdbc:sqlite:" + DB_FILE;

//  static {
//    try {
//      JdbcDriverLoader.load("org.sqlite.JDBC", Paths.get(JdbcDbWriterTest.class.getResource("/sqlite-jdbc-3.8.11.2.jar").toURI()).toFile());
//    } catch (URISyntaxException e) {
//      throw new RuntimeException(e);
//    }
//  }

  @Before
  public void setUp() {
    deleteSqlLiteFile();
  }

  @After
  public void tearDown() {
    deleteSqlLiteFile();
  }

  private void deleteSqlLiteFile() {
    new File(DB_FILE).delete();
  }

  @Test
  public void CollectMetaData() {
    List<FieldsMappings> fieldsMappingsList =
            Lists.newArrayList(new FieldsMappings("monitor_test", "incoming_topic", true, new HashMap<String, FieldAlias>()));

    JdbcSinkSettings settings = new JdbcSinkSettings(SQL_LITE_URI,
            null,
            null,
            fieldsMappingsList,
            true,
            ErrorPolicyEnum.NOOP,
            InsertModeEnum.INSERT,
            10);
    JdbcDbWriter writer = JdbcDbWriter.from(settings, new DbTableInfoProvider() {
      @Override
      public List<DbTable> getTables(String connectionUri, String user, String password) {
        return Lists.newArrayList(
                new DbTable("monitor_test", new HashMap<String, DbTableColumn>())
        );
      }
    });

    final HikariConfig config = new HikariConfig();
    config.setJdbcUrl(settings.getConnection());
    Connection conn;
    TableMonitor monitor = null;
    try {
      conn = new HikariDataSource(config).getConnection();
      String sql = "CREATE TABLE monitor_test " +
              "(id INTEGER not NULL, " +
              " first VARCHAR(255), " +
              " last VARCHAR(255), " +
              " age INTEGER, " +
              " PRIMARY KEY ( id ))";
      Statement stmt = conn.createStatement();
      stmt.execute(sql);
      monitor = new TableMonitor(settings);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }

    HashMap<String, HashMap<String, String>> tableMap = monitor.getCache();
    assertTrue(tableMap.get("monitor_test").containsKey("id"));
    assertTrue(tableMap.get("monitor_test").containsKey("first"));
    assertTrue(tableMap.get("monitor_test").containsKey("last"));
    assertTrue(tableMap.get("monitor_test").containsKey("age"));

    //no change
    Boolean reconfigure = monitor.doReconfigure("monitor_test", true);
    assertFalse(reconfigure);

    try {

      //sqllite doesn't handle drop cols so drop and recreate
      conn = new HikariDataSource(config).getConnection();
      String sql = "DROP TABLE monitor_test";
      Statement stmt = conn.createStatement();
      stmt.execute(sql);
      sql = "CREATE TABLE monitor_test " +
              "(id INTEGER not NULL, " +
              " first VARCHAR(255), " +
              " last VARCHAR(255), " +
              " PRIMARY KEY ( id ))";
      stmt.execute(sql);

    } catch (Exception e) {
      System.out.println(e.getMessage());
    }

    //column dropped
    reconfigure = monitor.doReconfigure("monitor_test", true);
    assertTrue(reconfigure);

    //column type change
    try {

      //sqllite doesn't handle drop cols so drop and recreate
      conn = new HikariDataSource(config).getConnection();
      String sql = "DROP TABLE monitor_test";
      Statement stmt = conn.createStatement();
      stmt.execute(sql);
      sql = "CREATE TABLE monitor_test " +
              "(id VARCHAR not NULL, " +
              " first VARCHAR(255), " +
              " last VARCHAR(255), " +
              " PRIMARY KEY ( id ))";
      stmt.execute(sql);

    } catch (Exception e) {
      System.out.println(e.getMessage());
    }

    reconfigure = monitor.doReconfigure("monitor_test", true);
    assertTrue(reconfigure);

    //drop table
    try {
      conn = new HikariDataSource(config).getConnection();
      String sql = "DROP TABLE monitor_test";
      Statement stmt = conn.createStatement();
      stmt.execute(sql);

    } catch (Exception e) {
      System.out.println(e.getMessage());
    }

    reconfigure = monitor.doReconfigure("monitor_test", true);
    assertTrue(reconfigure);
  }
}
