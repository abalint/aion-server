package com.aionemu.gameserver.services;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.configs.main.LegionConfig;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dao.ItemStoneListDAO;
import com.aionemu.gameserver.dao.LegionDAO;
import com.aionemu.gameserver.dao.LegionMemberDAO;
import com.aionemu.gameserver.model.DialogPage;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.DeniedStatus;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler;
import com.aionemu.gameserver.model.items.storage.IStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.team.legion.Legion;
import com.aionemu.gameserver.model.team.legion.LegionEmblem;
import com.aionemu.gameserver.model.team.legion.LegionEmblemType;
import com.aionemu.gameserver.model.team.legion.LegionHistory;
import com.aionemu.gameserver.model.team.legion.LegionHistoryType;
import com.aionemu.gameserver.model.team.legion.LegionMember;
import com.aionemu.gameserver.model.team.legion.LegionMemberEx;
import com.aionemu.gameserver.model.team.legion.LegionPermissionsMask;
import com.aionemu.gameserver.model.team.legion.LegionRank;
import com.aionemu.gameserver.model.team.legion.LegionWarehouse;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ICON_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_ADD_MEMBER;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_EDIT;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_LEAVE_MEMBER;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_MEMBERLIST;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_SEND_EMBLEM;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_SEND_EMBLEM_DATA;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_TABS;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_UPDATE_EMBLEM;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_UPDATE_MEMBER;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_UPDATE_NICKNAME;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_UPDATE_SELF_INTRO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_UPDATE_TITLE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_WAREHOUSE_INFO;
import com.aionemu.gameserver.services.abyss.AbyssRankingCache;
import com.aionemu.gameserver.services.abyss.GloryPointsService;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.services.trade.PricesService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.Util;
import com.aionemu.gameserver.utils.audit.AuditLogger;
import com.aionemu.gameserver.utils.collections.ListSplitter;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.container.LegionContainer;
import com.aionemu.gameserver.world.container.LegionMemberContainer;

/**
 * This class is designed to do all the work related with loading/storing legions and their members.
 *
 * @author Simple
 * @modified cura, Source, Neon
 */
public class LegionService {

	private static final Logger log = LoggerFactory.getLogger(LegionService.class);
	private final LegionContainer allCachedLegions = new LegionContainer();
	private final LegionMemberContainer allCachedLegionMembers = new LegionMemberContainer();
	/**
	 * Legion Permission variables
	 */
	private static final int MAX_LEGION_LEVEL = 8;

	/**
	 * Legion Restrictions
	 */
	private LegionRestrictions legionRestrictions = new LegionRestrictions();

	public static LegionService getInstance() {
		return SingletonHolder.instance;
	}

	private LegionService() {
	}

	/**
	 * Stores legion data into db
	 *
	 * @param legion
	 * @param newLegion
	 */
	private void storeLegion(Legion legion, boolean newLegion) {
		if (newLegion) {
			addCachedLegion(legion);
			DAOManager.getDAO(LegionDAO.class).saveNewLegion(legion);
		} else {
			DAOManager.getDAO(LegionDAO.class).storeLegion(legion);
			DAOManager.getDAO(LegionDAO.class).storeLegionEmblem(legion.getLegionId(), legion.getLegionEmblem());
		}
	}

	/**
	 * Stores newly created legion
	 *
	 * @param legion
	 *          legion to store
	 */
	private void storeLegion(Legion legion) {
		storeLegion(legion, false);
	}

	/**
	 * Stores legion member data into db or saves a new one
	 *
	 * @param legionMember
	 * @param newMember
	 */
	private void storeLegionMember(LegionMember legionMember, boolean newMember) {
		if (newMember) {
			addCachedLegionMember(legionMember);
			DAOManager.getDAO(LegionMemberDAO.class).saveNewLegionMember(legionMember);
		} else
			DAOManager.getDAO(LegionMemberDAO.class).storeLegionMember(legionMember.getObjectId(), legionMember);
	}

	/**
	 * Stores a legion member
	 *
	 * @param legionMember
	 *          legion member to store
	 */
	private void storeLegionMember(LegionMember legionMember) {
		storeLegionMember(legionMember, false);
	}

	/**
	 * Stores legion member data into database
	 *
	 * @param legionMemberEx
	 */
	private void storeLegionMemberExInCache(Player player) {
		if (this.allCachedLegionMembers.containsEx(player.getObjectId())) {
			LegionMemberEx legionMemberEx = allCachedLegionMembers.getMemberEx(player.getObjectId());
			legionMemberEx.setNickname(player.getLegionMember().getNickname());
			legionMemberEx.setSelfIntro(player.getLegionMember().getSelfIntro());
			legionMemberEx.setPlayerClass(player.getPlayerClass());
			legionMemberEx.setLevelByExp(player.getCommonData().getExp());
			legionMemberEx.setLastOnline(player.getCommonData().getLastOnline());
			legionMemberEx.setWorldId(player.getPosition().getMapId());
			legionMemberEx.setOnline(false);
		} else {
			LegionMemberEx legionMemberEx = new LegionMemberEx(player, player.getLegionMember(), false);
			addCachedLegionMemberEx(legionMemberEx);
		}
	}

	/**
	 * Gets a legion ONLY if he is in the cache
	 *
	 * @return Legion or null if not cached
	 */
	private Legion getCachedLegion(int legionId) {
		return this.allCachedLegions.get(legionId);
	}

	/**
	 * Gets a legion ONLY if he is in the cache
	 *
	 * @return Legion or null if not cached
	 */
	private Legion getCachedLegion(String legionName) {
		return this.allCachedLegions.get(legionName);
	}

	/**
	 * Returns all cached legions
	 */
	public LegionContainer getCachedLegions() {
		return this.allCachedLegions;
	}

	/**
	 * This method will add a new legion to the cache
	 *
	 * @param playerObjId
	 * @param legionMember
	 */
	private void addCachedLegion(Legion legion) {
		this.allCachedLegions.add(legion);
	}

	/**
	 * This method will add a new legion member to the cache
	 *
	 * @param playerObjId
	 * @param legionMember
	 */
	private void addCachedLegionMember(LegionMember legionMember) {
		this.allCachedLegionMembers.addMember(legionMember);
	}

	/**
	 * This method will add a new legion member to the cache
	 *
	 * @param playerObjId
	 * @param legionMemberEx
	 */
	private void addCachedLegionMemberEx(LegionMemberEx legionMemberEx) {
		this.allCachedLegionMembers.addMemberEx(legionMemberEx);
	}

	/**
	 * Completely removes legion from database and cache
	 *
	 * @param legionId
	 *          id of legion to delete from db
	 */
	private void deleteLegionFromDB(Legion legion) {
		this.allCachedLegions.remove(legion);
		DAOManager.getDAO(LegionDAO.class).deleteLegion(legion.getLegionId());
	}

	/**
	 * This method will remove the legion member from cache and the database
	 *
	 * @param playerObjId
	 */
	private void deleteLegionMemberFromDB(LegionMemberEx legionMember) {
		this.allCachedLegionMembers.remove(legionMember);
		DAOManager.getDAO(LegionMemberDAO.class).deleteLegionMember(legionMember.getObjectId());
		Legion legion = legionMember.getLegion();
		legion.deleteLegionMember(legionMember.getObjectId());
		addHistory(legion, legionMember.getName(), LegionHistoryType.KICK);
	}

	/**
	 * Returns the legion with given name
	 *
	 * @param legionName
	 *          Legion Name
	 * @return Legion or null if doesn't exists
	 */
	public Legion getLegion(String legionName) {
		/**
		 * First check if our legion already exists in our Cache
		 */
		if (allCachedLegions.contains(legionName)) {
			Legion legion = getCachedLegion(legionName);
			return legion;
		}

		/**
		 * Else load the legion information from the database
		 */
		Legion legion = DAOManager.getDAO(LegionDAO.class).loadLegion(legionName);

		/**
		 * This will handle the rest of the information that needs to be loaded
		 */
		loadLegionInfo(legion);

		/**
		 * Add our legion to the Cache
		 */
		addCachedLegion(legion);

		/**
		 * Return the legion
		 */
		return legion;
	}

	/**
	 * Returns the legion with given legionId (if such legion exists)
	 *
	 * @param legionId
	 * @return Legion
	 */
	public Legion getLegion(int legionId) {
		/**
		 * First check if our legion already exists in our Cache
		 */
		if (allCachedLegions.contains(legionId)) {
			Legion legion = getCachedLegion(legionId);
			return legion;
		}

		/**
		 * Else load the legion information from the database
		 */
		Legion legion = DAOManager.getDAO(LegionDAO.class).loadLegion(legionId);

		/**
		 * This will handle the rest of the information that needs to be loaded
		 */
		loadLegionInfo(legion);

		/**
		 * Add our legion to the Cache
		 */
		addCachedLegion(legion);

		/**
		 * Return the legion
		 */
		return legion;
	}

	/**
	 * This method will load the legion information
	 *
	 * @param legion
	 */
	private void loadLegionInfo(Legion legion) {
		if (legion == null)
			return;

		// Load and add the legion members to legion
		legion.setLegionMembers(DAOManager.getDAO(LegionMemberDAO.class).loadLegionMembers(legion.getLegionId()));

		// Load and set the announcement list
		legion.setAnnouncementList(DAOManager.getDAO(LegionDAO.class).loadAnnouncementList(legion.getLegionId()));

		// Load legion emblem
		legion.setLegionEmblem(DAOManager.getDAO(LegionDAO.class).loadLegionEmblem(legion.getLegionId()));

		// Load Legion Warehouse
		legion.setLegionWarehouse(DAOManager.getDAO(LegionDAO.class).loadLegionStorage(legion));
		ItemService.loadItemStones(legion.getLegionWarehouse().getItems());

		// Load Legion Rank
		legion.setLegionRank(AbyssRankingCache.getInstance().getLegionRank(legion));

		// Load Legion History
		DAOManager.getDAO(LegionDAO.class).loadLegionHistory(legion);
	}

	/**
	 * Returns the legion Brigade general with given legionId (if such legion exists)
	 *
	 * @param legionId
	 * @return LegionMember (Brigade General)
	 */
	public int getBrigadeGeneralOfLegion(int legionId) {
		Legion legion = getLegion(legionId);
		return legion == null ? 0 : legion.getBrigadeGeneral();
	}

	public List<Integer> getMembersByRank(int legionId, LegionRank rank) {
		Legion legion = getLegion(legionId);
		List<Integer> members = new ArrayList<>();
		for (int memberObjId : legion.getLegionMembers()) {
			LegionMember legionMember = getLegionMember(memberObjId);
			if (legionMember.getRank() == rank)
				members.add(memberObjId);
		}
		return members;
	}

	/**
	 * Returns the legion with given legionId (if such legion exists)
	 *
	 * @param playerObjId
	 * @return LegionMember
	 */
	public LegionMember getLegionMember(int playerObjId) {
		LegionMember legionMember = null;
		if (this.allCachedLegionMembers.contains(playerObjId))
			legionMember = this.allCachedLegionMembers.getMember(playerObjId);
		else {
			legionMember = DAOManager.getDAO(LegionMemberDAO.class).loadLegionMember(playerObjId);
			if (legionMember != null)
				addCachedLegionMember(legionMember);
		}

		if (legionMember != null)
			if (checkDisband(legionMember.getLegion()))
				return null;

		return legionMember;
	}

	/**
	 * Method that checks if a legion is disbanding
	 *
	 * @param legion
	 * @return true if it's time to be deleted
	 */
	private boolean checkDisband(Legion legion) {
		if (legion.isDisbanding()) {
			if ((System.currentTimeMillis() / 1000) > legion.getDisbandTime()) {
				disbandLegion(legion);
				return true;
			}
		}
		return false;
	}

	/**
	 * This method will disband a legion and update all members
	 */
	public void disbandLegion(Legion legion) {
		for (Integer memberObjId : legion.getLegionMembers()) {
			this.allCachedLegionMembers.remove(getLegionMemberEx(memberObjId));
		}
		SiegeService.getInstance().cleanLegionId(legion.getLegionId());
		updateAfterDisbandLegion(legion);
		deleteLegionFromDB(legion);
	}

	/**
	 * Returns the offline legion member with given playerId (if such member exists)
	 *
	 * @param playerObjId
	 * @return LegionMemberEx
	 */
	public LegionMemberEx getLegionMemberEx(int playerObjId) {
		if (this.allCachedLegionMembers.containsEx(playerObjId))
			return this.allCachedLegionMembers.getMemberEx(playerObjId);
		else {
			LegionMemberEx legionMember = DAOManager.getDAO(LegionMemberDAO.class).loadLegionMemberEx(playerObjId);
			addCachedLegionMemberEx(legionMember);
			return legionMember;
		}
	}

	/**
	 * Returns the offline legion member with given playerId (if such member exists)
	 *
	 * @param playerObjId
	 * @return LegionMemberEx
	 */
	private LegionMemberEx getLegionMemberEx(String playerName) {
		if (this.allCachedLegionMembers.containsEx(playerName))
			return this.allCachedLegionMembers.getMemberEx(playerName);
		else {
			LegionMemberEx legionMember = DAOManager.getDAO(LegionMemberDAO.class).loadLegionMemberEx(playerName);
			if (legionMember != null)
				addCachedLegionMemberEx(legionMember);
			return legionMember;
		}
	}

	/**
	 * This method will handle when disband request is called
	 *
	 * @param npc
	 * @param activePlayer
	 */
	public void requestDisbandLegion(Npc npc, Player activePlayer) {
		if (legionRestrictions.canDisbandLegion(activePlayer)) {
			RequestResponseHandler<Npc> disbandResponseHandler = new RequestResponseHandler<Npc>(npc) {

				@Override
				public void acceptRequest(Npc requester, Player responder) {
					Legion legion = responder.getLegion();
					int unixTime = (int) ((System.currentTimeMillis() / 1000) + LegionConfig.LEGION_DISBAND_TIME);
					legion.setDisbandTime(unixTime);
					updateMembersOfDisbandLegion(legion, unixTime);
				}
			};

			boolean disbandResult = activePlayer.getResponseRequester().putRequest(SM_QUESTION_WINDOW.STR_GUILD_DISPERSE_STAYMODE, disbandResponseHandler);
			if (disbandResult) {
				PacketSendUtility.sendPacket(activePlayer, new SM_QUESTION_WINDOW(SM_QUESTION_WINDOW.STR_GUILD_DISPERSE_STAYMODE, 0, 0));
			}
		}
	}

	/**
	 * This method will handle the creation of a legion
	 *
	 * @param activePlayer
	 * @param legionName
	 */
	public void createLegion(Player activePlayer, String legionName) {
		if (legionRestrictions.canCreateLegion(activePlayer, legionName)) {
			/**
			 * Create new legion and put originator as first member
			 */
			Legion legion = new Legion(IDFactory.getInstance().nextId(), legionName);
			legion.addLegionMember(activePlayer.getObjectId());

			activePlayer.getInventory().decreaseKinah(LegionConfig.LEGION_CREATE_REQUIRED_KINAH);

			/**
			 * Create a LegionMember, add it to the legion and bind it to a Player
			 */
			storeLegion(legion, true);
			Timestamp currentTime = new Timestamp(System.currentTimeMillis());
			storeNewAnnouncement(legion.getLegionId(), currentTime, "");
			legion.addAnnouncementToList(currentTime, "");
			addLegionMember(legion, activePlayer, LegionRank.BRIGADE_GENERAL);
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x05, (int) (System.currentTimeMillis() / 1000), ""));
			/**
			 * Add create and joined legion history and save it
			 */
			addHistory(legion, "", LegionHistoryType.CREATE);
			addHistory(legion, activePlayer.getName(), LegionHistoryType.JOIN);

			/**
			 * Send required packets
			 */
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CREATED(legion.getName()));
		}
	}

	public boolean directAddPlayer(Legion legion, Player player) {
		int playerObjId = player.getObjectId();
		if (legion.addLegionMember(playerObjId)) {
			// Bind LegionMember to Player
			addLegionMember(legion, player);

			// Display current announcement
			displayLegionMessage(player, legion.getCurrentAnnouncement());

			// Add to history of legion
			addHistory(legion, player.getName(), LegionHistoryType.JOIN);
			return true;
		} else {
			player.resetLegionMember();
			return false;
		}
	}

	/**
	 * Method that will handle a invitation to a legion
	 *
	 * @param activePlayer
	 * @param targetPlayer
	 */
	private void invitePlayerToLegion(final Player activePlayer, final Player targetPlayer) {
		if (legionRestrictions.canInvitePlayer(activePlayer, targetPlayer)) {
			final Legion legion = activePlayer.getLegion();

			RequestResponseHandler<Player> responseHandler = new RequestResponseHandler<Player>(activePlayer) {

				@Override
				public void acceptRequest(Player requester, Player responder) {
					if (!responder.isOnline()) {
						PacketSendUtility.sendPacket(requester, SM_SYSTEM_MESSAGE.STR_NO_SUCH_USER(responder.getName()));
					} else {
						int playerObjId = responder.getObjectId();
						if (legion.addLegionMember(playerObjId)) {
							// Bind LegionMember to Player
							addLegionMember(legion, responder);

							// Display current announcement
							displayLegionMessage(responder, legion.getCurrentAnnouncement());

							// Add to history of legion
							addHistory(legion, responder.getName(), LegionHistoryType.JOIN);
						} else {
							PacketSendUtility.sendPacket(requester, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_CAN_NOT_ADD_MEMBER_ANY_MORE());
							responder.resetLegionMember();
						}
					}

				}

				@Override
				public void denyRequest(Player requester, Player responder) {
					PacketSendUtility.sendPacket(requester, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_HE_REJECTED_INVITATION(responder.getName()));
				}
			};

			boolean requested = targetPlayer.getResponseRequester().putRequest(SM_QUESTION_WINDOW.STR_GUILD_INVITE_DO_YOU_ACCEPT_INVITATION,
				responseHandler);
			// If the player is busy and could not be asked
			if (!requested) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_OTHER_IS_BUSY());
			} else {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_SENT_INVITE_MSG_TO_HIM(targetPlayer.getName()));

				// Send question packet to buddy
				PacketSendUtility.sendPacket(targetPlayer, new SM_QUESTION_WINDOW(SM_QUESTION_WINDOW.STR_GUILD_INVITE_DO_YOU_ACCEPT_INVITATION, 0, 0,
					legion.getName(), legion.getLegionLevel() + "", activePlayer.getName()));
			}
		}
	}

	/**
	 * Displays current legion announcement
	 *
	 * @param targetPlayer
	 * @param currentAnnouncement
	 */
	private void displayLegionMessage(Player targetPlayer, Entry<Timestamp, String> currentAnnouncement) {
		if (currentAnnouncement != null) {
			PacketSendUtility.sendPacket(targetPlayer,
				SM_SYSTEM_MESSAGE.STR_GUILD_NOTICE(currentAnnouncement.getValue(), (int) (currentAnnouncement.getKey().getTime() / 1000)));
		}
	}

	private void startBrigadeGeneralChangeProcess(Player legionLeader, Player newLegionLeader) {
		RequestResponseHandler<Player> responseHandler = new RequestResponseHandler<Player>(newLegionLeader) {

			@Override
			public void acceptRequest(Player newBrigadeGeneral, Player responder) {
				appointBrigadeGeneral(responder, newBrigadeGeneral);
			}
		};
		boolean requested = legionLeader.getResponseRequester().putRequest(904979, responseHandler);
		if (requested) {
			PacketSendUtility.sendPacket(legionLeader, new SM_QUESTION_WINDOW(904979, 0, 0, newLegionLeader.getName()));
		}
	}

	/**
	 * This method will handle a new appointed legion leader
	 *
	 * @param activePlayer
	 * @param targetPlayer
	 */
	private void appointBrigadeGeneral(final Player activePlayer, final Player targetPlayer) {
		if (legionRestrictions.canAppointBrigadeGeneral(activePlayer, targetPlayer)) {
			final Legion legion = activePlayer.getLegion();
			RequestResponseHandler<Player> responseHandler = new RequestResponseHandler<Player>(activePlayer) {

				@Override
				public void acceptRequest(Player requester, Player responder) {
					if (!responder.isOnline()) {
						PacketSendUtility.sendPacket(requester, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_MASTER_NO_SUCH_USER());
					} else if (!legionRestrictions.canAppointBrigadeGeneral(requester, responder)) {
						AuditLogger.log(requester, "possibly tried to exploit legion leadership transfer");
					} else {
						LegionMember legionMember = responder.getLegionMember();
						if (legionMember.getRank().getRankId() > LegionRank.BRIGADE_GENERAL.getRankId()) { // Demote Brigade General to Centurion
							requester.getLegionMember().setRank(LegionRank.CENTURION);
							PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_UPDATE_MEMBER(requester, 0, ""));

							// Promote member to Brigade General
							legionMember.setRank(LegionRank.BRIGADE_GENERAL);
							PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_UPDATE_MEMBER(responder, 1300273, responder.getName()));
							PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x08));
							addHistory(legion, responder.getName(), LegionHistoryType.APPOINTED);
							// transfer gp to new Brigade General
							int gpToTransfer = requester.getLegion().getSiegeGloryPoints();
							GloryPointsService.decreaseGp(requester.getObjectId(), gpToTransfer);
							GloryPointsService.addGp(responder, gpToTransfer, false);
						}
					}
				}

				@Override
				public void denyRequest(Player requester, Player responder) {
					PacketSendUtility.sendPacket(requester, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_MASTER_HE_DECLINE_YOUR_OFFER(responder.getName()));
				}
			};

			boolean requested = targetPlayer.getResponseRequester().putRequest(SM_QUESTION_WINDOW.STR_GUILD_CHANGE_MASTER_DO_YOU_ACCEPT_OFFER,
				responseHandler);
			// If the player is busy and could not be asked
			if (!requested) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_MASTER_SENT_CANT_OFFER_WHEN_HE_IS_QUESTION_ASKED());
			} else {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_MASTER_SENT_OFFER_MSG_TO_HIM(targetPlayer.getName()));

				// Send question packet to buddy
				// TODO: Add char name parameter? Doesn't work?
				PacketSendUtility.sendPacket(targetPlayer, new SM_QUESTION_WINDOW(SM_QUESTION_WINDOW.STR_GUILD_CHANGE_MASTER_DO_YOU_ACCEPT_OFFER,
					activePlayer.getObjectId(), 0, activePlayer.getName()));
			}
		}
	}

	/**
	 * This method will handle the process when a member is demoted or promoted while offline.
	 *
	 * @param newCenturion
	 */
	private void appointRank(Player activePlayer, String charName, int rankId) {
		final LegionMemberEx LM = getLegionMemberEx(charName);
		if (LM == null) {
			log.error("Char name does not exist in legion member table: " + charName);
			return;
		}
		if (legionRestrictions.canAppointRank(activePlayer, LM.getObjectId())) {
			Legion legion = activePlayer.getLegion();
			LegionRank rank = LegionRank.values()[rankId];
			int msgId = 0;
			switch (rank) {
				case DEPUTY:
					msgId = 1400902;
					break;
				case LEGIONARY:
					msgId = 1300268;
					break;
				case CENTURION:
					msgId = 1300267;
					break;
				case VOLUNTEER:
					msgId = 1400903;
			}
			LegionMember legionMember = getLegionMember(LM.getObjectId());
			legionMember.setRank(rank);
			DAOManager.getDAO(LegionMemberDAO.class).storeLegionMember(legionMember.getObjectId(), legionMember);
			LM.setRank(rank);
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_UPDATE_MEMBER(LM, msgId, LM.getName()));
		}
	}

	/**
	 * This method will handle the process when a member is demoted or promoted.
	 *
	 * @param newCenturion
	 */
	private void appointRank(Player activePlayer, Player targetPlayer, int rankId) {
		if (legionRestrictions.canAppointRank(activePlayer, targetPlayer.getObjectId())) {
			Legion legion = activePlayer.getLegion();
			int msgId = 0;
			LegionRank rank = LegionRank.values()[rankId];
			LegionMember legionMember = targetPlayer.getLegionMember();
			switch (rank) {
				case DEPUTY:
					msgId = 1400902;
					break;
				case LEGIONARY:
					msgId = 1300268;
					break;
				case CENTURION:
					msgId = 1300267;
					break;
				case VOLUNTEER:
					msgId = 1400903;
			}
			legionMember.setRank(rank);
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_UPDATE_MEMBER(targetPlayer, msgId, targetPlayer.getName()));
		}
	}

	/**
	 * This method will handle the changement of a self intro
	 *
	 * @param activePlayer
	 * @param newSelfIntro
	 */
	private void changeSelfIntro(Player activePlayer, String newSelfIntro) {
		if (legionRestrictions.canChangeSelfIntro(activePlayer, newSelfIntro)) {
			LegionMember legionMember = activePlayer.getLegionMember();
			legionMember.setSelfIntro(newSelfIntro);
			PacketSendUtility.broadcastToLegion(legionMember.getLegion(), new SM_LEGION_UPDATE_SELF_INTRO(activePlayer.getObjectId(), newSelfIntro));
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_WRITE_INTRO_DONE());
		}
	}

	/**
	 * This method will handle the changement of permissions
	 *
	 * @param legion
	 */
	public void changePermissions(Legion legion, short deputyPermission, short centurionPermission, short legionarPermission,
		short volunteerPermission) {
		if (legion.setLegionPermissions(deputyPermission, centurionPermission, legionarPermission, volunteerPermission)) {
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x02, legion));
		}
	}

	/**
	 * This method will handle the leveling up of a legion
	 *
	 * @param LegionCommand
	 */
	private void requestChangeLevel(Player activePlayer) {
		if (legionRestrictions.canChangeLevel(activePlayer)) {
			Legion legion = activePlayer.getLegion();
			activePlayer.getInventory().decreaseKinah(legion.getKinahPrice());
			changeLevel(legion, legion.getLegionLevel() + 1, false);
			addHistory(legion, legion.getLegionLevel() + "", LegionHistoryType.LEVEL_UP);
		}
	}

	/**
	 * This method will change the legion level and send update to online members
	 *
	 * @param legion
	 */
	public void changeLevel(Legion legion, int newLevel, boolean save) {
		legion.setLegionLevel(newLevel);
		legion.getLegionWarehouse().setLimit(legion.getWarehouseSlots());
		PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x00, legion));
		PacketSendUtility.broadcastToLegion(legion, SM_SYSTEM_MESSAGE.STR_GUILD_EVENT_LEVELUP(newLevel));
		if (save)
			storeLegion(legion);
	}

	/**
	 * This method will handle the changement of a nickname
	 *
	 * @param playerObjId
	 * @param legionMember
	 */
	private void changeNickname(Player activePlayer, String charName, String newNickname) {
		Legion legion = activePlayer.getLegion();
		LegionMember legionMember;
		Player targetPlayer;
		if ((targetPlayer = World.getInstance().findPlayer(charName)) != null) {
			legionMember = targetPlayer.getLegionMember();
			if (targetPlayer.getLegion() != legion)
				return;
		} else {
			LegionMemberEx LM = getLegionMemberEx(charName);
			if (LM == null || LM.getLegion() != legion) {
				return;
			}
			legionMember = getLegionMember(LM.getObjectId());
		}
		if (legionRestrictions.canChangeNickname(legion, legionMember.getObjectId(), newNickname)) {
			legionMember.setNickname(newNickname);
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_UPDATE_NICKNAME(legionMember.getObjectId(), newNickname));
			if (targetPlayer == null)
				DAOManager.getDAO(LegionMemberDAO.class).storeLegionMember(legionMember.getObjectId(), legionMember);
		}
	}

	/**
	 * This method will remove legion from all legion members online after a legion has been disbanded
	 *
	 * @param legion
	 */
	private void updateAfterDisbandLegion(Legion legion) {
		for (Player onlineLegionMember : legion.getOnlineLegionMembers()) {
			PacketSendUtility.broadcastPacket(onlineLegionMember, new SM_LEGION_UPDATE_TITLE(onlineLegionMember.getObjectId(), 0, "", 0), true);
			PacketSendUtility.sendPacket(onlineLegionMember, new SM_LEGION_LEAVE_MEMBER(1300302, 0, legion.getName()));
			onlineLegionMember.resetLegionMember();
		}
	}

	/**
	 * This method will send a packet to every legion member
	 *
	 * @param legion
	 * @param emblemType
	 */
	private void updateMembersEmblem(Legion legion) {
		LegionEmblem legionEmblem = legion.getLegionEmblem();
		for (Player onlineLegionMember : legion.getOnlineLegionMembers()) {
			PacketSendUtility.broadcastPacket(onlineLegionMember, new SM_LEGION_UPDATE_EMBLEM(legion.getLegionId(), legionEmblem), true);
			if (legionEmblem.getEmblemType() == LegionEmblemType.CUSTOM)
				sendEmblemData(onlineLegionMember, legionEmblem, legion.getLegionId(), legion.getName());
		}
	}

	/**
	 * This method will send a packet to every legion member and update them about the disband
	 *
	 * @param legion
	 * @param unixTime
	 */
	private void updateMembersOfDisbandLegion(Legion legion, int unixTime) {
		for (Player onlineLegionMember : legion.getOnlineLegionMembers()) {
			PacketSendUtility.sendPacket(onlineLegionMember, new SM_LEGION_UPDATE_MEMBER(onlineLegionMember, 1300303, unixTime + ""));
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x06, unixTime));
		}
	}

	/**
	 * This method will send a packet to every legion member and update them about the disband
	 *
	 * @param legion
	 * @param unixTime
	 */
	private void updateMembersOfRecreateLegion(Legion legion) {
		for (Player onlineLegionMember : legion.getOnlineLegionMembers()) {
			PacketSendUtility.sendPacket(onlineLegionMember, new SM_LEGION_UPDATE_MEMBER(onlineLegionMember, 1300307, ""));
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x07));
		}
	}

	/**
	 * Stores the new legion emblem
	 *
	 * @param activePlayer
	 * @param legionId
	 * @param emblemId
	 * @param color_r
	 * @param color_g
	 * @param color_b
	 * @param emblemType
	 */
	public void storeLegionEmblem(Player activePlayer, int emblemId, int color_a, int color_r, int color_g, int color_b, LegionEmblemType emblemType) {
		if (legionRestrictions.canStoreLegionEmblem(activePlayer, emblemId)) {
			Legion legion = activePlayer.getLegion();
			addHistory(legion, "", LegionHistoryType.EMBLEM_MODIFIED);
			activePlayer.getInventory().decreaseKinah(PricesService.getPriceForService(LegionConfig.LEGION_EMBLEM_REQUIRED_KINAH, activePlayer.getRace()));
			legion.getLegionEmblem().setEmblem(emblemId, color_a, color_r, color_g, color_b, emblemType, null);
			updateMembersEmblem(legion);
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_EMBLEM());
		}
	}

	/**
	 * @param legion
	 */
	public List<LegionMemberEx> loadLegionMemberExList(Legion legion, Integer objExcluded) {
		List<LegionMemberEx> legionMembers = new ArrayList<>();
		for (Integer memberObjId : legion.getLegionMembers()) {
			LegionMemberEx legionMemberEx;
			if (objExcluded != null && objExcluded.equals(memberObjId)) {
				continue;
			}
			Player memberPlayer = World.getInstance().findPlayer(memberObjId);
			if (memberPlayer != null) {
				legionMemberEx = new LegionMemberEx(memberPlayer, memberPlayer.getLegionMember(), true);
			} else {
				legionMemberEx = getLegionMemberEx(memberObjId);
			}
			legionMembers.add(legionMemberEx);
		}
		return legionMembers;
	}

	/**
	 * @param activePlayer
	 */
	public void openLegionWarehouse(Player player, Npc npc) {
		if (legionRestrictions.canOpenWarehouse(player)) {
			LegionWhUpdate(player);
			PacketSendUtility.sendPacket(player, new SM_LEGION_EDIT(0x04, player.getLegion()));// kinah
			int whLvl = player.getLegion().getWarehouseLevel();
			List<Item> items = player.getLegion().getLegionWarehouse().getItems();
			int storageId = StorageType.LEGION_WAREHOUSE.getId();

			ListSplitter<Item> splitter = new ListSplitter<>(items, 10, false);
			while (splitter.hasMore()) {
				PacketSendUtility.sendPacket(player, new SM_WAREHOUSE_INFO(splitter.getNext(), storageId, whLvl, splitter.isFirst(), player));
			}
			PacketSendUtility.sendPacket(player, new SM_WAREHOUSE_INFO(null, storageId, whLvl, items.isEmpty(), player));
			PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(npc.getObjectId(), DialogPage.LEGION_WAREHOUSE.id()));
		}
	}

	/**
	 * @param npc
	 * @param player
	 */
	public void recreateLegion(Npc npc, Player activePlayer) {
		if (legionRestrictions.canRecreateLegion(activePlayer)) {
			RequestResponseHandler<Npc> disbandResponseHandler = new RequestResponseHandler<Npc>(npc) {

				@Override
				public void acceptRequest(Npc requester, Player responder) {
					Legion legion = responder.getLegion();
					legion.setDisbandTime(0);
					PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x07));
					updateMembersOfRecreateLegion(legion);
				}

			};

			boolean disbandResult = activePlayer.getResponseRequester().putRequest(SM_QUESTION_WINDOW.STR_GUILD_DISPERSE_STAYMODE_CANCEL,
				disbandResponseHandler);
			if (disbandResult) {
				PacketSendUtility.sendPacket(activePlayer, new SM_QUESTION_WINDOW(SM_QUESTION_WINDOW.STR_GUILD_DISPERSE_STAYMODE_CANCEL, 0, 0));
			}
		}
	}

	public void LegionWhUpdate(Player player) {
		Legion legion = player.getLegion();

		if (legion == null)
			return;

		List<Item> allItems = legion.getLegionWarehouse().getItemsWithKinah();
		allItems.addAll(legion.getLegionWarehouse().getDeletedItems());
		try {
			/**
			 * 1. save items first
			 */
			DAOManager.getDAO(InventoryDAO.class).store(allItems, player.getObjectId(), player.getAccount().getId(), legion.getLegionId());

			/**
			 * 2. save item stones
			 */
			DAOManager.getDAO(ItemStoneListDAO.class).save(allItems);
		} catch (Exception ex) {
			log.error("Exception during periodic saving of legion WH", ex);
		}
	}

	/**
	 * This method will update all players about the level/class change
	 *
	 * @param player
	 */
	public void updateMemberInfo(Player player) {
		PacketSendUtility.broadcastToLegion(player.getLegion(), new SM_LEGION_UPDATE_MEMBER(player, 0, ""));
	}

	/**
	 * This method will set the contribution points, specially for legion command
	 *
	 * @param legion
	 * @param newPoints
	 */
	public void setContributionPoints(Legion legion, long newPoints, boolean save) {
		legion.setContributionPoints(newPoints);
		PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x03, legion));
		if (save)
			storeLegion(legion);
	}

	public void uploadEmblemInfo(Player activePlayer, int totalSize, int color_a, int color_r, int color_g, int color_b, LegionEmblemType emblemType) {
		LegionEmblem legionEmblem = activePlayer.getLegion().getLegionEmblem();
		if (legionRestrictions.canUploadEmblem(activePlayer, true)) {
			legionEmblem.resetUploadSettings();
			legionEmblem.setEmblem(legionEmblem.getEmblemId(), color_a, color_r, color_g, color_b, emblemType, null);
			legionEmblem.setUploadSize(totalSize);
			legionEmblem.setUploading(true);
		} else {
			legionEmblem.resetUploadSettings();
		}
	}

	/**
	 * @param activePlayer
	 * @param size
	 * @param data
	 */
	public void uploadEmblemData(Player activePlayer, int size, byte[] data) {
		LegionEmblem legionEmblem = activePlayer.getLegion().getLegionEmblem();
		if (legionRestrictions.canUploadEmblem(activePlayer, false)) {
			legionEmblem.addUploadedSize(size);
			legionEmblem.addUploadData(data);

			if (legionEmblem.getUploadedSize() >= legionEmblem.getUploadSize()) {
				if (legionEmblem.getUploadedSize() == 0 || legionEmblem.getUploadedSize() > legionEmblem.getUploadSize()) {
					PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_WARN_CORRUPT_EMBLEM_FILE());
					return;
				}
				activePlayer.getInventory()
					.decreaseKinah(PricesService.getPriceForService(LegionConfig.LEGION_EMBLEM_REQUIRED_KINAH, activePlayer.getRace()));
				// Finished
				legionEmblem.setCustomEmblemData(legionEmblem.getUploadData());
				DAOManager.getDAO(LegionDAO.class).storeLegionEmblem(activePlayer.getLegion().getLegionId(), legionEmblem);
				addHistory(activePlayer.getLegion(), "", LegionHistoryType.EMBLEM_REGISTER);
				updateMembersEmblem(activePlayer.getLegion());
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_WARN_SUCCESS_UPLOAD_EMBLEM());
				legionEmblem.resetUploadSettings();
			}
		} else {
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_WARN_FAILURE_UPLOAD_EMBLEM());
			legionEmblem.resetUploadSettings();
		}
	}

	/**
	 * @param player
	 * @param legionEmblem
	 * @param legionId
	 * @param legionName
	 */
	public void sendEmblemData(Player player, LegionEmblem legionEmblem, int legionId, String legionName) {
		int dataLength = legionEmblem.getCustomEmblemData() == null ? 0 : legionEmblem.getCustomEmblemData().length;
		PacketSendUtility.sendPacket(player, new SM_LEGION_SEND_EMBLEM(legionId, legionEmblem, dataLength, legionName));
		if (dataLength > 0) {
			ByteBuffer buf = ByteBuffer.allocate(dataLength);
			buf.put(legionEmblem.getCustomEmblemData()).position(0);
			log.debug("legionEmblem size: " + buf.capacity() + " bytes");
			int maxSize = 7993;
			int currentSize;
			byte[] bytes;
			do {
				log.debug("legionEmblem data position: " + buf.position());
				currentSize = buf.capacity() - buf.position();
				log.debug("legionEmblem data remaining capacity: " + currentSize + " bytes");

				if (currentSize >= maxSize) {
					bytes = new byte[maxSize];
					for (int i = 0; i < maxSize; i++) {
						bytes[i] = buf.get();
					}
					log.debug("legionEmblem data send size: " + (bytes.length) + " bytes");
					PacketSendUtility.sendPacket(player, new SM_LEGION_SEND_EMBLEM_DATA(maxSize, bytes));
				} else {
					bytes = new byte[currentSize];
					for (int i = 0; i < currentSize; i++) {
						bytes[i] = buf.get();
					}
					log.debug("legionEmblem data send size: " + (bytes.length) + " bytes");
					PacketSendUtility.sendPacket(player, new SM_LEGION_SEND_EMBLEM_DATA(currentSize, bytes));
				}
			} while (buf.capacity() != buf.position());
		}
	}

	/**
	 * This will add a new announcement to the DB and change the current announcement
	 *
	 * @param legion
	 * @param unixTime
	 * @param message
	 */
	private void changeAnnouncement(Player activePlayer, String announcement) {
		if (legionRestrictions.canChangeAnnouncement(activePlayer.getLegionMember(), announcement)) {
			Legion legion = activePlayer.getLegion();

			Timestamp currentTime = new Timestamp(System.currentTimeMillis());
			storeNewAnnouncement(legion.getLegionId(), currentTime, announcement);
			legion.addAnnouncementToList(currentTime, announcement);
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_WRITE_NOTICE_DONE());
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x05, (int) (System.currentTimeMillis() / 1000), announcement));
		}
	}

	/**
	 * This method stores all legion announcements
	 *
	 * @param legion
	 */
	private void storeLegionAnnouncements(Legion legion) {
		for (int i = 0; i < (legion.getAnnouncementList().size() - 7); i++) {
			removeAnnouncement(legion.getLegionId(), legion.getAnnouncementList().firstEntry().getKey());
			legion.removeFirstEntry();
		}
	}

	/**
	 * Stores newly created announcement
	 *
	 * @param legionId
	 * @param currentTime
	 * @param message
	 * @return true if announcement was successful saved.
	 */
	private boolean storeNewAnnouncement(int legionId, Timestamp currentTime, String message) {
		return DAOManager.getDAO(LegionDAO.class).saveNewAnnouncement(legionId, currentTime, message);
	}

	/**
	 * @param legionId
	 * @param key
	 * @return true if succeeded
	 */
	private void removeAnnouncement(int legionId, Timestamp key) {
		DAOManager.getDAO(LegionDAO.class).removeAnnouncement(legionId, key);
	}

	private void addHistory(Legion legion, String text, LegionHistoryType legionHistoryType) {
		addHistory(legion, text, legionHistoryType, 0, StringUtils.EMPTY);
	}

	public void addRewardHistory(Legion legion, long kinahAmount, LegionHistoryType lht, int fortressId) {
		addHistory(legion, String.valueOf(kinahAmount), lht, 1, String.valueOf(fortressId));
	}

	/**
	 * This method will add a new history for a legion
	 *
	 * @param legion
	 * @param text
	 *          - in case of reward: kinah amount
	 * @param legionHistory
	 * @param description
	 *          - in case of reward: fortress id
	 */
	public void addHistory(Legion legion, String text, LegionHistoryType legionHistoryType, int tabId, String description) {
		LegionHistory legionHistory = new LegionHistory(legionHistoryType, text, new Timestamp(System.currentTimeMillis()), tabId, description);

		legion.addHistory(legionHistory);
		DAOManager.getDAO(LegionDAO.class).saveNewLegionHistory(legion.getLegionId(), legionHistory);

		PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_TABS(legion.getLegionHistoryByTabId(tabId), tabId));
	}

	/**
	 * This method will add a new legion member to a legion with VOLUNTEER rank
	 *
	 * @param legion
	 * @param player
	 */
	private void addLegionMember(Legion legion, Player player) {
		addLegionMember(legion, player, LegionRank.VOLUNTEER);
	}

	/**
	 * This method will add a new legion member to a legion with input rank
	 *
	 * @param legion
	 * @param player
	 * @param rank
	 */
	private void addLegionMember(Legion legion, Player player, LegionRank rank) {
		// Set legion member of player and save in the database
		player.setLegionMember(new LegionMember(player.getObjectId(), legion, rank));
		storeLegionMember(player.getLegionMember(), true);

		// Send the new legion member the required legion packets
		PacketSendUtility.sendPacket(player, new SM_LEGION_INFO(legion));
		List<LegionMemberEx> totalMembers = loadLegionMemberExList(legion, player.getObjectId());
		ListSplitter<LegionMemberEx> splits = new ListSplitter<>(totalMembers, 80, true);
		// Send the member list to the new legion member
		while (splits.hasMore()) {
			boolean isSplit = false;
			boolean isFirst = splits.isFirst();
			List<LegionMemberEx> curentMembers = splits.getNext();
			if (isFirst && curentMembers.size() < totalMembers.size()) {
				isSplit = true;
			}
			PacketSendUtility.sendPacket(player, new SM_LEGION_MEMBERLIST(curentMembers, isSplit, isFirst));
		}

		// Send legion member info to the members
		PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_ADD_MEMBER(player, false, 1300260, player.getName()), player.getObjectId());
		PacketSendUtility.sendPacket(player, new SM_LEGION_ADD_MEMBER(player, false, 0, ""));
		// Send legion emblem information
		LegionEmblem legionEmblem = legion.getLegionEmblem();
		PacketSendUtility.broadcastPacket(player, new SM_LEGION_UPDATE_EMBLEM(legion.getLegionId(), legionEmblem), true);

		// Send legion edit
		PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_EDIT(0x08));

		// Update legion member's appearance in game
		PacketSendUtility.broadcastPacket(player,
			new SM_LEGION_UPDATE_TITLE(player.getObjectId(), legion.getLegionId(), legion.getName(), player.getLegionMember().getRank().getRankId()), true);
		legion.addBonus();
	}

	/**
	 * This method will remove a legion member
	 *
	 * @param charName
	 * @return true if successful
	 */
	private boolean removeLegionMember(String charName, boolean kick, String playerName) {
		/**
		 * Get LegionMemberEx from cache or database if offline
		 */
		LegionMemberEx legionMember = getLegionMemberEx(charName);
		if (legionMember == null) {
			log.error("Char name does not exist in legion member table: " + charName);
			return false;
		}

		/**
		 * Delete legion member from database and cache
		 */
		deleteLegionMemberFromDB(legionMember);

		/**
		 * If player is online send packet and reset legion member
		 */
		Player player = World.getInstance().findPlayer(charName);
		if (player != null) {
			PacketSendUtility.broadcastPacket(player, new SM_LEGION_UPDATE_TITLE(player.getObjectId(), 0, "", 2), true);
		}
		Legion legion = legionMember.getLegion();
		/**
		 * Send packets to legion members
		 */
		if (kick) {
			PacketSendUtility.broadcastToLegion(legion,
				new SM_LEGION_LEAVE_MEMBER(1300247, legionMember.getObjectId(), playerName, legionMember.getName()));
		} else {
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_LEAVE_MEMBER(900699, legionMember.getObjectId(), charName));
		}
		legion.removeBonus();
		return true;
	}

	/**
	 * This method will handle legion stuff
	 *
	 * @param exOpcode
	 * @param activePlayer
	 * @param charName
	 * @param rank
	 */
	public void handleCharNameRequest(int exOpcode, Player activePlayer, String charName, String newNickname, int rank) {
		Legion legion = activePlayer.getLegion();

		charName = Util.convertName(charName);
		Player targetPlayer = World.getInstance().findPlayer(charName);

		switch (exOpcode) {
			/**
			 * Invite to legion
			 */
			case 0x01:
				if (targetPlayer != null) {
					if (targetPlayer.getPlayerSettings().isInDeniedStatus(DeniedStatus.GUILD)) {
						PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_MSG_REJECTED_INVITE_GUILD(charName));
						return;
					}
					invitePlayerToLegion(activePlayer, targetPlayer);
				} else {
					PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_NO_USER_TO_INVITE());
				}
				break;
			/**
			 * Kick member from legion *
			 */
			case 0x04:
				/**
				 * Check if player can be kicked
				 */
				if (legionRestrictions.canKickPlayer(activePlayer, charName)) {
					if (removeLegionMember(charName, true, activePlayer.getName())) {
						// send packet to members?
						if (targetPlayer != null) {
							PacketSendUtility.sendPacket(targetPlayer, new SM_LEGION_LEAVE_MEMBER(1300246, 0, legion.getName()));
							targetPlayer.resetLegionMember();
							if (legion.hasBonus()) {
								PacketSendUtility.sendPacket(activePlayer, new SM_ICON_INFO(1, false));
							}

						}
					}
				}
				break;
			/**
			 * Appoint a new Brigade General *
			 */
			case 0x05:
				if (targetPlayer != null) {
					startBrigadeGeneralChangeProcess(activePlayer, targetPlayer);
				} else {
					PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_NO_USER_TO_INVITE());
				}
				break;
			/**
			 * Appoint Centurion/Legionairy *
			 */
			case 0x06:
				if (targetPlayer != null)
					appointRank(activePlayer, targetPlayer, rank);
				else
					appointRank(activePlayer, charName, rank);
				break;
			/**
			 * Set nickname *
			 */
			case 0x0F:
				changeNickname(activePlayer, charName, newNickname);
				break;
		}
	}

	/**
	 * This method will handle announcement and self intro changement
	 *
	 * @param exOpcode
	 * @param activePlayer
	 * @param text
	 */
	public void handleLegionRequest(int exOpcode, Player activePlayer, String text) {
		switch (exOpcode) {
			/**
			 * Edit announcements
			 */
			case 0x09:
				changeAnnouncement(activePlayer, text);
				break;
			/**
			 * Change self introduction *
			 */
			case 0x0A:
				changeSelfIntro(activePlayer, text);
				break;
		}
	}

	/**
	 * @param exOpcode
	 * @param activePlayer
	 */
	public void handleLegionRequest(int exOpcode, Player activePlayer) {
		switch (exOpcode) {
			/**
			 * Leave legion
			 */
			case 0x02:
				if (legionRestrictions.canLeave(activePlayer)) {
					if (removeLegionMember(activePlayer.getName(), false, "")) {
						Legion legion = activePlayer.getLegion();
						PacketSendUtility.sendPacket(activePlayer, new SM_LEGION_LEAVE_MEMBER(1300241, 0, legion.getName()));
						activePlayer.resetLegionMember();
						if (legion.hasBonus()) {
							PacketSendUtility.sendPacket(activePlayer, new SM_ICON_INFO(1, false));
						}
					}
				}
				break;
			/**
			 * Level legion up *
			 */
			case 0x0E:
				requestChangeLevel(activePlayer);
				break;
		}
	}

	public boolean removePlayerFromLegionAsItself(Player player) {
		if (removeLegionMember(player.getName(), false, "")) {
			Legion legion = player.getLegion();
			PacketSendUtility.sendPacket(player, new SM_LEGION_LEAVE_MEMBER(1300241, 0, legion.getName()));
			player.resetLegionMember();
			if (legion.hasBonus()) {
				PacketSendUtility.sendPacket(player, new SM_ICON_INFO(1, false));
			}
			return true;
		} else
			return false;
	}

	/**
	 * @param player
	 */
	public void onLogin(Player activePlayer) {
		Legion legion = activePlayer.getLegion();

		// Tell all legion members player has come online
		PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_UPDATE_MEMBER(activePlayer, 0, ""), activePlayer.getObjectId());

		// Notify legion members player has logged in
		PacketSendUtility.broadcastToLegion(legion, SM_SYSTEM_MESSAGE.STR_MSG_NOTIFY_LOGIN_GUILD(activePlayer.getName()), activePlayer.getObjectId());

		// Send member add to player
		PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_ADD_MEMBER(activePlayer, true, 0, ""));

		// Send legion info packets
		PacketSendUtility.sendPacket(activePlayer, new SM_LEGION_INFO(legion));
		List<LegionMemberEx> totalMembers = loadLegionMemberExList(legion, null);
		// Send member list to player
		ListSplitter<LegionMemberEx> splits = new ListSplitter<>(totalMembers, 80, true);
		// Send the member list to the new legion member
		while (splits.hasMore()) {
			boolean isSplit = false;
			boolean isFirst = splits.isFirst();
			List<LegionMemberEx> curentMembers = splits.getNext();
			if (isFirst && curentMembers.size() < totalMembers.size()) {
				isSplit = true;
			}
			PacketSendUtility.sendPacket(activePlayer, new SM_LEGION_MEMBERLIST(curentMembers, isSplit, isFirst));
		}

		// Send current announcement to player
		displayLegionMessage(activePlayer, legion.getCurrentAnnouncement());

		if (legion.isDisbanding())
			PacketSendUtility.sendPacket(activePlayer, new SM_LEGION_EDIT(0x06, legion.getDisbandTime()));

		legion.increaseOnlineMembersCount();
		if (legion.hasBonus()) {
			PacketSendUtility.sendPacket(activePlayer, new SM_ICON_INFO(1, true));
		} else {
			legion.addBonus();
		}
	}

	/**
	 * @param player
	 */
	public void onLogout(Player player) {
		Legion legion = player.getLegion();
		LegionWarehouse lwh = player.getLegion().getLegionWarehouse();
		if (lwh.getWhUser() == player.getObjectId()) {
			lwh.setWhUser(0);
		}
		PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_UPDATE_MEMBER(player));
		storeLegion(legion);
		storeLegionMember(player.getLegionMember());
		storeLegionMemberExInCache(player);
		storeLegionAnnouncements(legion);
		legion.decreaseOnlineMembersCount();
		legion.removeBonus();
	}

	public void clearCaches() {
		allCachedLegions.clear();
		allCachedLegionMembers.clear();
	}

	/**
	 * This class contains all restrictions for legion features
	 *
	 * @author Simple
	 */
	private class LegionRestrictions {

		/**
		 * Static Emblem information *
		 */
		private static final int MIN_EMBLEM_ID = 0;
		private static final int MAX_EMBLEM_ID = 49;

		/**
		 * This method checks all restrictions for legion creation
		 *
		 * @param activePlayer
		 * @param legionName
		 * @return true if allow to create a legion
		 */
		private boolean canCreateLegion(Player activePlayer, String legionName) {
			/* Some reasons why legions can' be created */
			if (!NameRestrictionService.isValidLegionName(legionName) || NameRestrictionService.isForbidden(legionName)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CREATE_INVALID_GUILD_NAME());
				return false;
			} // STR_GUILD_CREATE_TOO_FAR_FROM_CREATOR_NPC TODO
			else if (!isFreeName(legionName)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CREATE_SAME_GUILD_EXIST());
				return false;
			} else if (activePlayer.isLegionMember()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CREATE_ALREADY_BELONGS_TO_GUILD());
				return false;
			} else if (activePlayer.getInventory().getKinah() < LegionConfig.LEGION_CREATE_REQUIRED_KINAH) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CREATE_NOT_ENOUGH_MONEY());
				return false;
			}
			return true;
		}

		/**
		 * This method checks all restrictions for invite player to legion
		 *
		 * @param activePlayer
		 * @param targetPlayer
		 * @return true if can invite player
		 */
		private boolean canInvitePlayer(Player activePlayer, Player targetPlayer) {
			Legion legion = activePlayer.getLegion();
			if (activePlayer.isDead()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_CANT_INVITE_WHEN_DEAD());
				return false;
			}
			if (activePlayer.equals(targetPlayer)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_CAN_NOT_INVITE_SELF());
				return false;
			} else if (targetPlayer.isLegionMember()) {
				if (legion.isMember(targetPlayer.getObjectId())) {
					PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_HE_IS_MY_GUILD_MEMBER(targetPlayer.getName()));
				} else {
					PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_INVITE_HE_IS_OTHER_GUILD_MEMBER(targetPlayer.getName()));
				}
				return false;
			} else if (!activePlayer.getLegionMember().hasRights(LegionPermissionsMask.INVITE)) {
				// No rights to invite
				return false;
			} else if (activePlayer.getRace() != targetPlayer.getRace() && !LegionConfig.LEGION_INVITEOTHERFACTION) {
				// Not Same Race
				return false;
			}
			return true;
		}

		/**
		 * This method checks all restrictions for kicking a player from a legion
		 *
		 * @param activePlayer
		 * @param charName
		 * @return true if can kick player
		 */
		private boolean canKickPlayer(Player activePlayer, String charName) {
			/**
			 * Get LegionMemberEx from cache or database if offline
			 */
			LegionMemberEx legionMember = getLegionMemberEx(charName);
			Legion legion = activePlayer.getLegion();

			// TODO: Can not kick during a war!! SM_SYSTEM_MESSAGE.STR_GUILD_BANISH_CANT_BAN_MEMBER_WHILE_WAR()
			if (legionMember == null || !legion.isMember(legionMember.getObjectId())) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_BANISH_HE_IS_NOT_MY_GUILD_MEMBER(charName));
				return false;
			} else if (activePlayer.getObjectId() == legionMember.getObjectId()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_BANISH_CANT_BANISH_SELF());
				return false;
			} else if (legionMember.isBrigadeGeneral()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_BANISH_CAN_BANISH_MASTER());
				return false;
			} else if (legionMember.getRank().getRankId() <= activePlayer.getLegionMember().getRank().getRankId()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_BANISH_CAN_NOT_BANISH_SAME_MEMBER_RANK());
				return false;
			} else if (!activePlayer.getLegionMember().hasRights(LegionPermissionsMask.KICK)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_BANISH_DONT_HAVE_RIGHT_TO_BANISH());
				return false;
			}
			return true;
		}

		/**
		 * This method checks all restrictions for appointing brigade general
		 *
		 * @param activePlayer
		 * @param targetPlayer
		 * @return true if can appoint brigade general
		 */
		private boolean canAppointBrigadeGeneral(Player activePlayer, Player targetPlayer) {
			Legion legion = activePlayer.getLegion();
			if (!isBrigadeGeneral(activePlayer)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_MEMBER_RANK_DONT_HAVE_RIGHT());
				return false;
			}
			if (activePlayer.equals(targetPlayer)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_MASTER_ERROR_SELF());
				return false;
			} else if (!legion.isMember(targetPlayer.getObjectId()))
				// not in same legion
				return false;
			return true;
		}

		/**
		 * This method checks all restrictions for appointing rank
		 *
		 * @param activePlayer
		 * @param targetPlayer
		 * @return true if can appoint rank
		 */
		private boolean canAppointRank(Player activePlayer, int targetObjId) {
			Legion legion = activePlayer.getLegion();
			if (!isBrigadeGeneral(activePlayer)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_MEMBER_RANK_DONT_HAVE_RIGHT());
				return false;
			}
			if (activePlayer.getObjectId() == targetObjId) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_MASTER_ERROR_SELF());
				return false;
			} else if (!legion.isMember(targetObjId)) {
				// not in same legion
				return false;
			}
			return true;
		}

		/**
		 * This method checks all restrictions for changing self intro
		 *
		 * @param activePlayer
		 * @param newSelfIntro
		 * @return true if allowed to change self intro
		 */
		private boolean canChangeSelfIntro(Player activePlayer, String newSelfIntro) {
			if (!isValidSelfIntro(newSelfIntro))
				return false;
			return true;
		}

		/**
		 * This method checks all restrictions for changing legion level
		 *
		 * @param activePlayer
		 * @param kinahAmount
		 * @return true if allowed to change legion level
		 */
		private boolean canChangeLevel(Player activePlayer) {
			Legion legion = activePlayer.getLegion();
			int levelContributionPrice = legion.getContributionPrice();

			if (legion.getLegionLevel() == MAX_LEGION_LEVEL) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_LEVEL_CANT_LEVEL_UP());
				return false;
			} else if (LegionConfig.ENABLE_GUILD_TASK_REQ && legion.getLegionLevel() >= 5) {
				if (!ChallengeTaskService.getInstance().canRaiseLegionLevel(legion.getLegionId(), legion.getLegionLevel())) {
					PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_LEVEL_UP_CHALLENGE_TASK(legion.getLegionLevel()));
					return false;
				}
			} else if (activePlayer.getInventory().getKinah() < legion.getKinahPrice()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_LEVEL_NOT_ENOUGH_MONEY());
				return false;
			} else if (!legion.hasRequiredMembers()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_LEVEL_NOT_ENOUGH_MEMBER());
				return false;
			} else if (legion.getContributionPoints() < levelContributionPrice) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_LEVEL_NOT_ENOUGH_POINT());
				return false;
			}
			return true;
		}

		/**
		 * This method will check all restrictions for changing nickname
		 *
		 * @param activePlayer
		 * @return true if allowed to change nickname of target player
		 */
		private boolean canChangeNickname(Legion legion, int targetObjectId, String newNickname) {
			if (!isValidNickname(newNickname)) {
				// invalid nickname
				return false;
			} else if (!legion.isMember(targetObjectId)) {
				// not in same legion
				return false;
			}
			return true;
		}

		/**
		 * This method checks all restrictions for changing announcements
		 *
		 * @param legionMember
		 * @param announcement
		 * @return true if can change announcement
		 */
		private boolean canChangeAnnouncement(LegionMember legionMember, String announcement) {
			return legionMember.hasRights(LegionPermissionsMask.EDIT) && (announcement.isEmpty() ? true : isValidAnnouncement(announcement));
		}

		/**
		 * This method checks all restrictions for disband legion
		 *
		 * @param activePlayer
		 * @param legion
		 * @return true if can disband legion
		 */
		private boolean canDisbandLegion(Player activePlayer) {
			Legion legion = activePlayer.getLegion();
			// TODO: Can't disband during a war!!
			// TODO: Can't disband legion with fortress or hideout!!
			if (legion == null) {
				return false;
			}
			if (!isBrigadeGeneral(activePlayer)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_DISPERSE_ONLY_MASTER_CAN_DISPERSE());
				return false;
			} else if (legion.getLegionWarehouse().size() > 0) {
				// TODO: Can't disband during using legion warehouse!!
				return false;
			} else if (legion.isDisbanding()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_DISPERSE_ALREADY_REQUESTED());
				return false;
			} else if (legion.getLegionWarehouse().size() > 0) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_DISPERSE_CANT_DISPERSE_GUILD_STORE_ITEM_IN_WAREHOUSE());
				return false;
			}
			return true;
		}

		/**
		 * This method checks all restrictions for leaving
		 *
		 * @param activePlayer
		 * @return true if allowed to leave
		 */
		private boolean canLeave(Player activePlayer) {
			if (isBrigadeGeneral(activePlayer)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_LEAVE_MASTER_CANT_LEAVE_BEFORE_CHANGE_MASTER());
				return false;
			}
			return true;
		}

		/**
		 * This method checks all restrictions for recreate legion
		 *
		 * @param activePlayer
		 * @param legion
		 * @return true if allowed to recreate legion
		 */
		private boolean canRecreateLegion(Player activePlayer) {
			if (!isBrigadeGeneral(activePlayer)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_DISPERSE_ONLY_MASTER_CAN_DISPERSE());
				return false;
			} else if (!activePlayer.getLegion().isDisbanding()) {
				// Legion is not disbanding
				return false;
			}
			return true;
		}

		/**
		 * This method checks all restrictions for uploading emblem
		 *
		 * @return true if allowed to upload emblem
		 */
		private boolean canUploadEmblem(Player activePlayer, boolean initUpload) {
			if (!canStoreLegionEmblem(activePlayer, MIN_EMBLEM_ID)) {
				return false;
			} else if (activePlayer.getLegion().getLegionLevel() < 3) {
				// Legion level isn't high enough
				return false;
			} else if (initUpload && activePlayer.getLegion().getLegionEmblem().isUploading()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_WARN_FAILURE_UPLOAD_EMBLEM());
				return false;
			} else if (!initUpload && !activePlayer.getLegion().getLegionEmblem().isUploading()) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_WARN_FAILURE_UPLOAD_EMBLEM());
				return false;
			}
			return true;
		}

		/**
		 * @param activePlayer
		 * @return
		 */
		public boolean canOpenWarehouse(Player player) {
			if (!player.isLegionMember()) {
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_NO_GUILD_TO_DEPOSIT());
				return false;
			}
			Legion legion = player.getLegion();
			LegionWarehouse legWh = legion.getLegionWarehouse();
			LegionMember lm = player.getLegionMember();
			int whUser = legWh.getWhUser();
			int playerId = player.getObjectId();
			if (legion.isDisbanding()) {
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GUILD_WAREHOUSE_CANT_USE_WHILE_DISPERSE());
				return false;
			} else if (!LegionConfig.LEGION_WAREHOUSE) {
				// Legion Warehouse not enabled
				return false;
			} else if (!lm.hasRights(LegionPermissionsMask.WH_DEPOSIT) && !lm.hasRights(LegionPermissionsMask.WH_WITHDRAWAL)) {
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GUILD_WAREHOUSE_NO_RIGHT());
				return false;
			} else if (whUser != playerId && legWh.getWhUser() != 0) {
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GUILD_WAREHOUSE_IN_USE());
				return false;
			}
			legWh.setWhUser(player.getObjectId());
			return true;
		}

		/**
		 * @param activePlayer
		 * @param emblemId
		 * @return
		 */
		public boolean canStoreLegionEmblem(Player activePlayer, int emblemId) {
			if (emblemId < MIN_EMBLEM_ID || emblemId > MAX_EMBLEM_ID) {
				// Not a valid emblemId
				return false;
			} else if (!isBrigadeGeneral(activePlayer)) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_GUILD_CHANGE_EMBLEM_DONT_HAVE_RIGHT());
				return false;
			} else if (activePlayer.getLegion().getLegionLevel() < 2) {
				// legion level not high enough
				return false;
			} else if (activePlayer.getInventory().getKinah() < PricesService.getPriceForService(LegionConfig.LEGION_EMBLEM_REQUIRED_KINAH,
				activePlayer.getRace())) {
				PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_MSG_NOT_ENOUGH_MONEY());
				return false;
			}
			return true;
		}

		/**
		 * Checks if player is brigade general and returns message if not
		 *
		 * @param player
		 * @param message
		 * @return
		 */
		private boolean isBrigadeGeneral(Player player) {
			return player.getLegionMember().isBrigadeGeneral();
		}

		/**
		 * Checks if name is already taken or not
		 *
		 * @param name
		 *          character name
		 * @return true if is free, false in other case
		 */
		private boolean isFreeName(String name) {
			return !DAOManager.getDAO(LegionDAO.class).isNameUsed(name);
		}

		/**
		 * Checks if a self intro is valid. It should contain only english letters
		 *
		 * @param name
		 *          character name
		 * @return true if name is valid, false overwise
		 */
		private boolean isValidSelfIntro(String name) {
			return LegionConfig.SELF_INTRO_PATTERN.matcher(name).matches();
		}

		/**
		 * Checks if a nickname is valid. It should contain only english letters
		 *
		 * @param name
		 *          character name
		 * @return true if name is valid, false overwise
		 */
		private boolean isValidNickname(String name) {
			return LegionConfig.NICKNAME_PATTERN.matcher(name).matches();
		}

		/**
		 * Checks if a announcement is valid. It should contain only english letters
		 *
		 * @param name
		 *          announcement
		 * @return true if name is valid, false overwise
		 */
		private boolean isValidAnnouncement(String name) {
			return LegionConfig.ANNOUNCEMENT_PATTERN.matcher(name.replaceAll("\\r\\n", "")).matches();
		}
	}

	public void addWHItemHistory(Player player, int itemId, long count, IStorage sourceStorage, IStorage destStorage) {
		Legion legion = player.getLegion();
		if (legion != null) {
			String description = Integer.toString(itemId) + ":" + Long.toString(count);
			if (sourceStorage.getStorageType() == StorageType.LEGION_WAREHOUSE) {
				LegionService.getInstance().addHistory(legion, player.getName(), LegionHistoryType.ITEM_WITHDRAW, 2, description);
			} else if (destStorage.getStorageType() == StorageType.LEGION_WAREHOUSE) {
				LegionService.getInstance().addHistory(legion, player.getName(), LegionHistoryType.ITEM_DEPOSIT, 2, description);
			}
		}
	}

	private static class SingletonHolder {

		protected static final LegionService instance = new LegionService();
	}

	/**
	 * @param legion
	 * @param player
	 * @return
	 */
	public boolean hasCenturionPermission(Legion legion, Player player) {
		for (int memberObjId : legion.getLegionMembers()) {
			LegionMember legionMember = LegionService.getInstance().getLegionMember(memberObjId);
			if (legionMember.getRank() == LegionRank.CENTURION && legionMember.getObjectId() == player.getObjectId())
				return true;
		}
		return false;
	}

	/**
	 * for name changes
	 */
	public void updateLegionMemberList(Player activePlayer) {
		// TODO FIX NULL POINTER BECAUSE OF GETLEGIONMEMBEREX
		if (activePlayer != null && activePlayer.getLegion() != null) {
			Legion legion = activePlayer.getLegion();
			List<LegionMemberEx> totalMembers = loadLegionMemberExList(legion, null);
			ListSplitter<LegionMemberEx> splits = new ListSplitter<>(totalMembers, 80, true);
			// Send the member list to all online members
			while (splits.hasMore()) {
				boolean isSplit = false;
				boolean isFirst = splits.isFirst();
				List<LegionMemberEx> curentMembers = splits.getNext();
				if (isFirst && curentMembers.size() < totalMembers.size()) {
					isSplit = true;
				}
				PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_MEMBERLIST(curentMembers, isSplit, isFirst));
			}
		}
	}

	/**
	 * for name changes
	 * 
	 * @param activePlayer
	 */
	public void removeFromCache(Player activePlayer) {
		if (activePlayer == null) {
			return;
		}
		if (allCachedLegionMembers.contains(activePlayer.getObjectId())) {
			allCachedLegionMembers.remove(activePlayer);
		}
	}

	/**
	 * for name changes
	 * 
	 * @param activePlayer
	 */
	public void addToCache(Player activePlayer) {
		if (activePlayer == null) {
			return;
		}
		if (!allCachedLegionMembers.contains(activePlayer.getObjectId())) {
			allCachedLegionMembers.add(activePlayer);
		}
	}

	public void joinLegionDominion(Player player, Legion legion, int locId) {
		if (legion.getCurrentLegionDominion() > 0) // already selected
			return;
		if (LegionDominionService.getInstance().join(legion.getLegionId(), locId)) {
			legion.setCurrentLegionDominion(locId);
			storeLegion(legion);
			PacketSendUtility.broadcastToLegion(legion, new SM_SYSTEM_MESSAGE(1402902, LegionDominionService.getInstance().getNameDesc(locId))); // applied
																																																																						// for
																																																																						// stonespear
			PacketSendUtility.broadcastToLegion(legion, new SM_LEGION_INFO(legion));
		}
	}

}
