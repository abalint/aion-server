package mysql5;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DB;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.database.IUStH;
import com.aionemu.gameserver.dao.MySQL5DAOUtils;
import com.aionemu.gameserver.dao.PlayerSettingsDAO;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerSettings;

/**
 * @author ATracer
 */
public class MySQL5PlayerSettingsDAO extends PlayerSettingsDAO {

	private static final Logger log = LoggerFactory.getLogger(MySQL5PlayerSettingsDAO.class);

	/**
	 * TODO 1) analyze possibility to zip settings 2) insert/update instead of replace 0 - uisettings 1 - shortcuts 2 - display 3 - deny
	 */
	@Override
	public PlayerSettings loadSettings(int playerId) {
		final PlayerSettings playerSettings = new PlayerSettings();
		try {
			try (Connection con = DatabaseFactory.getConnection();
				PreparedStatement stmt = con.prepareStatement("SELECT * FROM player_settings WHERE player_id = ?")) {
				stmt.setInt(1, playerId);
				try (ResultSet resultSet = stmt.executeQuery()) {
					while (resultSet.next()) {
						int type = resultSet.getInt("settings_type");
						switch (type) {
							case 0:
								playerSettings.setUiSettings(resultSet.getBytes("settings"));
								break;
							case 1:
								playerSettings.setShortcuts(resultSet.getBytes("settings"));
								break;
							case 2:
								playerSettings.setHouseBuddies(resultSet.getBytes("settings"));
								break;
							case -1:
								playerSettings.setDisplay(resultSet.getInt("settings"));
								break;
							case -2:
								playerSettings.setDeny(resultSet.getInt("settings"));
								break;
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("Could not restore PlayerSettings data for player " + playerId + " from DB: " + e.getMessage(), e);
		}
		playerSettings.setPersistentState(PersistentState.UPDATED);
		return playerSettings;
	}

	@Override
	public void saveSettings(final Player player) {
		final int playerId = player.getObjectId();

		PlayerSettings playerSettings = player.getPlayerSettings();
		if (playerSettings.getPersistentState() == PersistentState.UPDATED)
			return;

		final byte[] uiSettings = playerSettings.getUiSettings();
		final byte[] shortcuts = playerSettings.getShortcuts();
		final byte[] houseBuddies = playerSettings.getHouseBuddies();
		final int display = playerSettings.getDisplay();
		final int deny = playerSettings.getDeny();

		if (uiSettings != null) {
			DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

				@Override
				public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
					stmt.setInt(1, playerId);
					stmt.setInt(2, 0);
					stmt.setBytes(3, uiSettings);
					stmt.execute();
				}
			});
		}

		if (shortcuts != null) {
			DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

				@Override
				public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
					stmt.setInt(1, playerId);
					stmt.setInt(2, 1);
					stmt.setBytes(3, shortcuts);
					stmt.execute();
				}
			});
		}

		if (houseBuddies != null) {
			DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

				@Override
				public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
					stmt.setInt(1, playerId);
					stmt.setInt(2, 2);
					stmt.setBytes(3, houseBuddies);
					stmt.execute();
				}
			});
		}

		DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

			@Override
			public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
				stmt.setInt(1, playerId);
				stmt.setInt(2, -1);
				stmt.setInt(3, display);
				stmt.execute();
			}
		});

		DB.insertUpdate("REPLACE INTO player_settings values (?, ?, ?)", new IUStH() {

			@Override
			public void handleInsertUpdate(PreparedStatement stmt) throws SQLException {
				stmt.setInt(1, playerId);
				stmt.setInt(2, -2);
				stmt.setInt(3, deny);
				stmt.execute();
			}
		});

	}

	@Override
	public boolean supports(String databaseName, int majorVersion, int minorVersion) {
		return MySQL5DAOUtils.supports(databaseName, majorVersion, minorVersion);
	}
}