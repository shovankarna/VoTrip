package com.votrip.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Proves the Postgres connection at boot rather than at the first query.
 *
 * <p>There are no entities yet, so nothing else would touch the DataSource. Without this, a
 * misconfigured database would stay invisible until the first repository lands. Throwing here
 * fails startup loudly, which is the point.
 */
@Component
public class DatabaseStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupCheck.class);

    private final DataSource dataSource;

    public DatabaseStartupCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            log.info(
                    "Database connection OK - {} {} at {}",
                    metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion(),
                    metaData.getURL());
        }
    }
}
