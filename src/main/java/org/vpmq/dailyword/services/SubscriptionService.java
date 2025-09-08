package org.vpmq.dailyword.services;

import org.vpmq.dailyword.models.SubscriptionModel;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SubscriptionService {

    private static final String ADD_SUBSCRIPTION_QUERY =
        """
            INSERT INTO subscriptions(user_id, chat_id)
            VALUES (?, ?)
            """;

    private static final String GET_SUBSCRIPTIONS_QUERY =
        """
        SELECT *
        FROM
          subscriptions
        """;

    private static final String GET_SUBSCRIPTION_BY_USER_ID_QUERY =
        """
        SELECT *
        FROM
          subscriptions
        WHERE
          user_id = ?
        """;

    private static final String DELETE_SUBSCRIPTION_QUERY =
        """
        DELETE FROM subscriptions
        WHERE user_id = ?
        """;

    private static final String SET_LAST_SOUND_ID_QUERY =
        """
        UPDATE subscriptions
        SET
          last_sound_id = ?
        WHERE
          user_id = ?
        """;

    private static final String SET_LAST_MESSAGE_ID_QUERY =
        """
        UPDATE subscriptions
        SET
          last_msg_id = ?
        WHERE
          user_id = ?
        """;

    private final DataSource ds;

    public SubscriptionService(DataSource ds) {
        this.ds = ds;
    }

    public void addSubscription(long userId, long chatId) {
        //just to check
        int id = getUserSubscriptionId(userId);
        if (id == 0) {
            try (Connection conn = ds.getConnection();
                 PreparedStatement statement = conn.prepareStatement(ADD_SUBSCRIPTION_QUERY);
            ) {
                statement.setLong(1, userId);
                statement.setLong(2, chatId);
                statement.executeUpdate();
            } catch (SQLException ex) {
                ex.printStackTrace();
                throw new RuntimeException(ex);
            }
        }
    }

    public void updateLastSound(long userId, Integer lastSoundId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(SET_LAST_SOUND_ID_QUERY);
        ) {
            if (lastSoundId != null) {
                statement.setInt(1, lastSoundId);
            } else {
                statement.setNull(1, Types.INTEGER);
            }
            statement.setLong(2, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public void updateLastMessage(long userId, int messageId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(SET_LAST_MESSAGE_ID_QUERY);
        ) {
            statement.setLong(1, messageId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public void deleteSubscription(long userId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(DELETE_SUBSCRIPTION_QUERY)
        ) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public int getUserSubscriptionId(long userId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(GET_SUBSCRIPTION_BY_USER_ID_QUERY)
        ) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                    //throw new RuntimeException("no subscription found for user with id = " + userId);
                }
                return rs.getInt("id");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public List<SubscriptionModel> getSubscriptions() {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(GET_SUBSCRIPTIONS_QUERY)
        ) {
            List<SubscriptionModel> info = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    long userId = rs.getLong("user_id");
                    long chatId = rs.getLong("chat_id");
                    int lastMsgId = rs.getInt("last_msg_id");
                    int lastSoundId = rs.getInt("last_sound_id");

//                    System.out.println("lastMsgId" + lastMsgId);
//                    System.out.println("lastSoundId " + lastSoundId);
//                    System.out.println("lastChatId " + chatId);

                    info.add(new SubscriptionModel(id, userId, chatId, lastMsgId, lastSoundId));
                }
                return info;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

}
