package org.vpmq.dailyword.services;

import org.vpmq.dailyword.models.VocabularyModel;

import javax.sql.DataSource;
import java.sql.*;

public class WordService {

    private static final String GET_WORD_BY_ID_QUERY =
        """
        SELECT
          id,
          word,
          ru_translation
        FROM words
        WHERE
          id = ?
        """;

    private static final String GET_WORD_COUNT_QUERY =
        """
        SELECT
          COUNT(id) as word_count
        FROM
          words
        """;

    private final DataSource ds;

    public WordService(DataSource ds) {
        this.ds = ds;
    }

    public VocabularyModel getById(int id) {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(GET_WORD_BY_ID_QUERY)
        ) {
            statement.setLong(1, id);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("word with id = " + id + " doesn't exist");
                }
                String word = rs.getString("word");
                String ruTranslation = rs.getString("ru_translation");
                return new VocabularyModel(id, word, ruTranslation);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public int getWordCount() {
        try (Connection conn = ds.getConnection();
             PreparedStatement statement = conn.prepareStatement(GET_WORD_COUNT_QUERY);
             ResultSet rs = statement.executeQuery()
        ) {
            return rs.getInt("word_count");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
