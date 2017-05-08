package com.aionemu.gameserver.model.team.group.events;

import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.common.events.PlayerLeavedEvent;
import com.aionemu.gameserver.model.team.common.legacy.GroupEvent;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.model.team.group.PlayerGroupMember;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.network.aion.serverpackets.SM_GROUP_MEMBER_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEAVE_GROUP_MEMBER;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author ATracer
 */
public class PlayerGroupLeavedEvent extends PlayerLeavedEvent<PlayerGroupMember, PlayerGroup> {

	public PlayerGroupLeavedEvent(PlayerGroup alliance, Player player) {
		super(alliance, player);
	}

	public PlayerGroupLeavedEvent(PlayerGroup team, Player player, LeaveReson reason, String banPersonName) {
		super(team, player, reason, banPersonName);
	}

	public PlayerGroupLeavedEvent(PlayerGroup alliance, Player player, LeaveReson reason) {
		super(alliance, player, reason);
	}

	@Override
	public void handleEvent() {
		team.removeMember(leavedPlayer.getObjectId());

		if (leavedPlayer.isMentor()) {
			team.onEvent(new PlayerGroupStopMentoringEvent(team, leavedPlayer));
		}

		team.forEach(member -> {
			PacketSendUtility.sendPacket(member, new SM_GROUP_MEMBER_INFO(team, leavedPlayer, GroupEvent.LEAVE));

			switch (reason) {
				case LEAVE:
					PacketSendUtility.sendPacket(member, SM_SYSTEM_MESSAGE.STR_PARTY_HE_LEAVE_PARTY(leavedPlayer.getName()));
					break;
				case LEAVE_TIMEOUT:
					PacketSendUtility.sendPacket(member, SM_SYSTEM_MESSAGE.STR_PARTY_HE_BECOME_OFFLINE_TIMEOUT(leavedPlayer.getName()));
					break;
				case BAN:
					PacketSendUtility.sendPacket(member, SM_SYSTEM_MESSAGE.STR_PARTY_HE_IS_BANISHED(leavedPlayer.getName()));
					break;
				case DISBAND:
					PacketSendUtility.sendPacket(member, SM_SYSTEM_MESSAGE.STR_PARTY_IS_DISPERSED());
					break;
			}
		});

		switch (reason) {
			case BAN:
			case LEAVE:
				if (team.shouldDisband()) {
					PlayerGroupService.disband(team);
				} else {
					if (leavedPlayer.equals(team.getLeader().getObject())) {
						team.onEvent(new ChangeGroupLeaderEvent(team));
					}
				}
				if (reason == LeaveReson.BAN) {
					PacketSendUtility.sendPacket(leavedPlayer, SM_SYSTEM_MESSAGE.STR_PARTY_YOU_ARE_BANISHED());
				}
				break;
			case LEAVE_TIMEOUT:
				if (team.shouldDisband()) {
					PlayerGroupService.disband(team);
				}
				break;
			case DISBAND:
				PacketSendUtility.sendPacket(leavedPlayer, SM_SYSTEM_MESSAGE.STR_PARTY_IS_DISPERSED());
				break;
		}

		if (leavedPlayer.isOnline()) {
			PacketSendUtility.sendPacket(leavedPlayer, new SM_LEAVE_GROUP_MEMBER());
			if (team.equals(leavedPlayer.getPosition().getWorldMapInstance().getRegisteredTeam())) {
				PacketSendUtility.sendPacket(leavedPlayer, SM_SYSTEM_MESSAGE.STR_MSG_LEAVE_INSTANCE_NOT_PARTY());
				leavedPlayer.getController().addTask(TaskId.DESPAWN, ThreadPoolManager.getInstance().schedule(new Runnable() {

					@Override
					public void run() {
						if (leavedPlayer.getCurrentTeamId() != team.getObjectId()) {
							if (leavedPlayer.getPosition().getWorldMapInstance().getRegisteredTeam() != null)
								InstanceService.moveToExitPoint(leavedPlayer);
						}
					}
				}, 30000));
			}
		}
	}

}
