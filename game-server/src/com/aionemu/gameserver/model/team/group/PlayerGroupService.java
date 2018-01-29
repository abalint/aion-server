package com.aionemu.gameserver.model.team.group;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.callbacks.metadata.GlobalCallback;
import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.common.events.PlayerLeavedEvent.LeaveReson;
import com.aionemu.gameserver.model.team.common.events.ShowBrandEvent;
import com.aionemu.gameserver.model.team.common.events.TeamKinahDistributionEvent;
import com.aionemu.gameserver.model.team.common.legacy.GroupEvent;
import com.aionemu.gameserver.model.team.common.legacy.LootGroupRules;
import com.aionemu.gameserver.model.team.group.callback.AddPlayerToGroupCallback;
import com.aionemu.gameserver.model.team.group.callback.PlayerGroupCreateCallback;
import com.aionemu.gameserver.model.team.group.callback.PlayerGroupDisbandCallback;
import com.aionemu.gameserver.model.team.group.events.ChangeGroupLeaderEvent;
import com.aionemu.gameserver.model.team.group.events.ChangeGroupLootRulesEvent;
import com.aionemu.gameserver.model.team.group.events.GroupDisbandEvent;
import com.aionemu.gameserver.model.team.group.events.PlayerConnectedEvent;
import com.aionemu.gameserver.model.team.group.events.PlayerDisconnectedEvent;
import com.aionemu.gameserver.model.team.group.events.PlayerEnteredEvent;
import com.aionemu.gameserver.model.team.group.events.PlayerGroupInvite;
import com.aionemu.gameserver.model.team.group.events.PlayerGroupLeavedEvent;
import com.aionemu.gameserver.model.team.group.events.PlayerGroupStopMentoringEvent;
import com.aionemu.gameserver.model.team.group.events.PlayerGroupUpdateEvent;
import com.aionemu.gameserver.model.team.group.events.PlayerStartMentoringEvent;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.restrictions.RestrictionsManager;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.TimeUtil;

/**
 * @author ATracer
 */
public class PlayerGroupService {

	private static final Logger log = LoggerFactory.getLogger(PlayerGroupService.class);

	private static final Map<Integer, PlayerGroup> groups = new ConcurrentHashMap<>();
	private static final AtomicBoolean offlineCheckStarted = new AtomicBoolean();

	public static final void inviteToGroup(final Player inviter, final Player invited) {
		if (RestrictionsManager.canInviteToGroup(inviter, invited)) {
			PlayerGroupInvite invite = new PlayerGroupInvite(inviter);
			if (invited.getResponseRequester().putRequest(SM_QUESTION_WINDOW.STR_PARTY_DO_YOU_ACCEPT_INVITATION, invite)) {
				PacketSendUtility.sendPacket(invited, new SM_QUESTION_WINDOW(SM_QUESTION_WINDOW.STR_PARTY_DO_YOU_ACCEPT_INVITATION, 0, 0, inviter.getName()));
			}
		}
	}

	@GlobalCallback(PlayerGroupCreateCallback.class)
	public static final PlayerGroup createGroup(Player leader, Player invited, TeamType type, int id) {
		PlayerGroup newGroup = new PlayerGroup(new PlayerGroupMember(leader), type, id);
		groups.put(newGroup.getTeamId(), newGroup);
		addPlayer(newGroup, leader);
		addPlayer(newGroup, invited);
		if (offlineCheckStarted.compareAndSet(false, true)) {
			initializeOfflineCheck();
		}
		return newGroup;
	}

	private static void initializeOfflineCheck() {
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new OfflinePlayerChecker(), 1000, 30 * 1000);
	}

	@GlobalCallback(AddPlayerToGroupCallback.class)
	public static final void addPlayerToGroup(PlayerGroup group, Player invited) {
		group.addMember(new PlayerGroupMember(invited));
	}

	/**
	 * Change group's loot rules and notify team members
	 */
	public static final void changeGroupRules(PlayerGroup group, LootGroupRules lootRules) {
		group.onEvent(new ChangeGroupLootRulesEvent(group, lootRules));
	}

	/**
	 * Player entered world - search for non expired group
	 */
	public static final void onPlayerLogin(Player player) {
		for (PlayerGroup group : groups.values()) {
			PlayerGroupMember member = group.getMember(player.getObjectId());
			if (member != null) {
				group.onEvent(new PlayerConnectedEvent(group, player));
			}
		}
	}

	/**
	 * Player leaved world - set last online on member
	 */
	public static final void onPlayerLogout(Player player) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			PlayerGroupMember member = group.getMember(player.getObjectId());
			member.updateLastOnlineTime();
			group.onEvent(new PlayerDisconnectedEvent(group, player));
		}
	}

	/**
	 * Update group members to some event of player
	 */
	public static final void updateGroup(Player player, GroupEvent groupEvent) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			group.onEvent(new PlayerGroupUpdateEvent(group, player, groupEvent));
		}
	}

	public static final void updateGroupEffects(Player player, int slot) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			group.onEvent(new PlayerGroupUpdateEvent(group, player, GroupEvent.UPDATE_EFFECTS, slot));
		}
	}

	/**
	 * Add player to group
	 */
	public static final void addPlayer(PlayerGroup group, Player player) {
		Objects.requireNonNull(group, "Group should not be null");
		group.onEvent(new PlayerEnteredEvent(group, player));
	}

	/**
	 * Remove player from group (normal leave, or kick offline player)
	 */
	public static final void removePlayer(Player player) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			group.onEvent(new PlayerGroupLeavedEvent(group, player));
		}
	}

	/**
	 * Remove player from group (ban)
	 */
	public static final void banPlayer(Player bannedPlayer, Player banGiver) {
		Objects.requireNonNull(bannedPlayer, "Banned player should not be null");
		Objects.requireNonNull(banGiver, "Bangiver player should not be null");
		PlayerGroup group = banGiver.getPlayerGroup();
		if (group != null) {
			if (banGiver.equals(bannedPlayer))
				PacketSendUtility.sendPacket(banGiver, SM_SYSTEM_MESSAGE.STR_PARTY_CANT_BAN_SELF());
			else if (group.hasMember(bannedPlayer.getObjectId()))
				group.onEvent(new PlayerGroupLeavedEvent(group, bannedPlayer, LeaveReson.BAN, banGiver.getName()));
			else
				log.warn("TEAM: banning {} not in group {}", bannedPlayer, group.getMembers());
		}
	}

	/**
	 * Disband group by removing all players one by one
	 */
	@GlobalCallback(PlayerGroupDisbandCallback.class)
	public static void disband(PlayerGroup group) {
		groups.remove(group.getTeamId());
		group.onEvent(new GroupDisbandEvent(group));
	}

	/**
	 * Share specific amount of kinah between group members
	 */
	public static void distributeKinah(Player player, long kinah) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			group.onEvent(new TeamKinahDistributionEvent<>(group, player, kinah));
		}
	}

	/**
	 * Show specific mark on top of player
	 */
	public static void showBrand(Player player, int targetObjId, int brandId) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			group.onEvent(new ShowBrandEvent<>(group, targetObjId, brandId));
		}
	}

	public static void changeLeader(Player player) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			group.onEvent(new ChangeGroupLeaderEvent(group, player));
		}
	}

	/**
	 * Start mentoring in group
	 */
	public static void startMentoring(Player player) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			group.onEvent(new PlayerStartMentoringEvent(group, player));
		}
	}

	/**
	 * Stop mentoring in group
	 */
	public static void stopMentoring(Player player) {
		PlayerGroup group = player.getPlayerGroup();
		if (group != null) {
			group.onEvent(new PlayerGroupStopMentoringEvent(group, player));
		}
	}

	public static final void cleanup() {
		log.info(getServiceStatus());
		groups.clear();
	}

	public static final String getServiceStatus() {
		return "Number of groups: " + groups.size();
	}

	public static final PlayerGroup searchGroup(int playerObjId) {
		for (PlayerGroup group : groups.values()) {
			if (group.hasMember(playerObjId)) {
				return group;
			}
		}
		return null;
	}

	public static class OfflinePlayerChecker implements Runnable {

		@Override
		public void run() {
			for (PlayerGroup group : groups.values()) {
				group.forEachTeamMember(member -> {
					if (!member.isOnline() && TimeUtil.isExpired(member.getLastOnlineTime() + GroupConfig.GROUP_REMOVE_TIME * 1000)) {
						group.onEvent(new PlayerGroupLeavedEvent(group, member.getObject(), LeaveReson.LEAVE_TIMEOUT));
					}
				});
			}
		}
	}

}
