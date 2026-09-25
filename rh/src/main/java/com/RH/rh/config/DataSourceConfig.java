package com.RH.rh.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.sqlserver")
    public DataSource sqlServerDataSource() {
        return new HikariDataSource();
    }

    @Bean
    @ConfigurationProperties("app.datasource.turso")
    public DataSource tursoDataSource() {
        return new HikariDataSource();
    }

    @Bean
    @Primary
    public JdbcTemplate sqlServerJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("sqlServerDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    public JdbcTemplate tursoJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("tursoDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}