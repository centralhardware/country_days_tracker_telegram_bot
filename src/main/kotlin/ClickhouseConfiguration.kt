import com.clickhouse.jdbc.ClickHouseDataSource
import org.apache.ibatis.mapping.Environment
import org.apache.ibatis.session.SqlSessionFactory
import org.apache.ibatis.session.SqlSessionFactoryBuilder
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory
import java.sql.SQLException
import javax.sql.DataSource

object ClickhouseConfiguration {

    val dataSource: DataSource
        get() = try {
            ClickHouseDataSource(System.getenv("CLICKHOUSE_URL"))
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }

    val sqlSessionClickhouse: SqlSessionFactory
        get() {
            val clickhouse = Environment("clickhouse", JdbcTransactionFactory(), dataSource)
            val configurationClickhouse = org.apache.ibatis.session.Configuration(clickhouse)

            configurationClickhouse.addMapper(CountryDaysTrackerMapper::class.java)
            configurationClickhouse.isCacheEnabled = false

            return SqlSessionFactoryBuilder().build(configurationClickhouse)
        }
}