package eu.izmoqwy.parkourchallenge.database;

import eu.izmoqwy.parkourchallenge.ParkourChallenge;
import eu.izmoqwy.parkourchallenge.Procedure;
import lombok.Getter;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.function.Consumer;

public class RemoteDatabase {

    private final String connectionUrl;

    @Getter
    private Connection connection;
    private ParkourChallenge plugin;

    public RemoteDatabase(ParkourChallenge plugin, String host, int port, String database) {
        this.plugin = plugin;
        this.connectionUrl = "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    public void connect(String username, String password) throws SQLException {
        if (connection != null && !connection.isClosed())
            return;

        this.connection = DriverManager.getConnection(connectionUrl, username, password);
        connection.setAutoCommit(false);
    }

    public void disconnect() throws SQLException {
        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }
    }

    public void commit(Procedure callback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    connection.commit();
                    callback.invoke();
                }
                catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void asyncSelect(ParkourChallenge plugin, PreparedStatement preparedStatement, Consumer<ResultSet> resultSetConsumer, Consumer<Boolean> callback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    ResultSet resultSet = preparedStatement.executeQuery();
                    if (resultSetConsumer != null)
                        resultSetConsumer.accept(resultSet);
                    preparedStatement.close();
                    if (callback != null)
                        callback.accept(true);
                }
                catch (SQLException e) {
                    e.printStackTrace();
                    if (callback != null)
                        callback.accept(false);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

}