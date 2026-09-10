package com.RH.rh.config;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

public class AutoCommitSafeDataSource implements DataSource {

    private final DataSource delegate;

    public AutoCommitSafeDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {

        Connection connection = delegate.getConnection();

        InvocationHandler handler = (proxy, method, args) -> {

            String methodName = method.getName();

            // =====================================================
            // Le driver DBeaver LibSQL ne supporte pas
            // setAutoCommit()
            // =====================================================

            if ("setAutoCommit".equals(methodName)) {
                return null;
            }

            // =====================================================
            // Hibernate peut appeler commit()
            // =====================================================

            if ("commit".equals(methodName)) {
                return null;
            }

            // =====================================================
            // Hibernate peut appeler rollback()
            // =====================================================

            if ("rollback".equals(methodName)) {
                return null;
            }

            // =====================================================
            // Hibernate peut demander l'état AutoCommit
            // =====================================================

            if ("getAutoCommit".equals(methodName)) {
                return true;
            }

            // =====================================================
            // Fermer normalement la connexion
            // =====================================================

            if ("close".equals(methodName)) {
                return method.invoke(connection, args);
            }

            // =====================================================
            // isClosed()
            // =====================================================

            if ("isClosed".equals(methodName)) {
                return method.invoke(connection, args);
            }

            // =====================================================
            // Wrapper JDBC
            // =====================================================

            if ("isWrapperFor".equals(methodName)) {
                return false;
            }

            if ("unwrap".equals(methodName)) {
                throw new SQLException(
                    "Unwrap non supporté par AutoCommitSafeDataSource"
                );
            }

            // =====================================================
            // Tous les autres appels sont transmis au driver
            // =====================================================

            try {
                return method.invoke(connection, args);
            }
            catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        };

        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            handler
        );
    }

    @Override
    public Connection getConnection(String username, String password)
            throws SQLException {

        return getConnection();
    }

    @Override
    public java.io.PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(java.io.PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() {
        try {
            return delegate.getParentLogger();
        }
        catch (Exception e) {
            return java.util.logging.Logger.getGlobal();
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(delegate)) {
            return iface.cast(delegate);
        }

        throw new SQLException(
            "DataSource ne peut pas être unwrap vers " + iface.getName()
        );
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(delegate);
    }
}