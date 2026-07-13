package org.vpmq.dailyword.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.vpmq.dailyword.models.StateModel;
import org.vpmq.dailyword.models.VocabularyModel;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StateService {

    private static final Logger LOGGER = LogManager.getLogger(StateService.class);

    private static final int DEFAULT_STATE_ID = 1;

    private static final String GET_STATE_BY_ID = """
        SELECT
          id, word_id 
        FROM
          state
        WHERE
          id = ?
        """;

    private static final String UPDATE_STATE_BY_ID = """
        UPDATE state
        SET
          word_id = ?
        WHERE
          id = ?
        """;

    private final DataSource ds;

    public StateService(DataSource ds) {
        this.ds = ds;
    }

    public StateModel get(int stateId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(GET_STATE_BY_ID)
        ) {
            statement.setLong(1, stateId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("state with id = " + stateId + " doesn't exist");
                }
                int id = rs.getInt("id");
                int wordId = rs.getInt("word_id");
                return new StateModel(id, wordId);
            }
        } catch (SQLException ex) {
            LOGGER.error("Failed to get state {}", stateId, ex);
            throw new RuntimeException(ex);
        }
    }

    public StateModel getDefault() {
        return get(DEFAULT_STATE_ID);
    }

    public void update(StateModel state) {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(UPDATE_STATE_BY_ID);
        ) {
            statement.setInt(1, state.getWordId());
            statement.setInt(2, state.getId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.error("Failed to update state {}", state.getId(), ex);
            throw new RuntimeException(ex);
        }
    }

}
