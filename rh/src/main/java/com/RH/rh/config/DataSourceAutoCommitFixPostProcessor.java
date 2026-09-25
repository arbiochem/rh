package com.RH.rh.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DataSourceAutoCommitFixPostProcessor
        implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(
            Object bean,
            String beanName) throws BeansException {

        // Le contournement auto-commit ne concerne que le pilote libSQL
        // (Turso). SQL Server doit garder de vraies transactions.
        if ("tursoDataSource".equals(beanName)
                && bean instanceof DataSource
                && !(bean instanceof AutoCommitSafeDataSource)) {

            System.out.println(
                ">>> AutoCommitSafeDataSource activé pour : "
                + beanName
            );

            return new AutoCommitSafeDataSource(
                (DataSource) bean
            );
        }

        return bean;
    }
}