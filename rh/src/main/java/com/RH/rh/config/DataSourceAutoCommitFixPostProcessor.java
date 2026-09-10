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

        if (bean instanceof DataSource
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