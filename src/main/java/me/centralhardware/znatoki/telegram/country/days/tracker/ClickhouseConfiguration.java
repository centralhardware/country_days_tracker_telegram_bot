package me.centralhardware.znatoki.telegram.country.days.tracker;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

public class ClickhouseConfiguration {

    public static DataSource getDataSource(){
        try {
            return new ClickHouseDataSource(System.getenv("CLICKHOUSE_URL"));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static SqlSessionFactory getSqlSessionClickhouse(){
        var clickhouse = new Environment("clickhouse", new JdbcTransactionFactory(), getDataSource());
        var configurationClickhouse = new org.apache.ibatis.session.Configuration(clickhouse);

        configurationClickhouse.addMapper(CountryDaysTrackerMapper.class);
        configurationClickhouse.setCacheEnabled(false);

        return new SqlSessionFactoryBuilder().build(configurationClickhouse);
    }

}
