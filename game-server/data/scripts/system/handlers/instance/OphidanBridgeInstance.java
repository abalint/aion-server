package instance;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author Yeats 25.03.2016
 */
@InstanceID(300590000)
public class OphidanBridgeInstance extends GeneralInstanceHandler {

	private AtomicBoolean isNormalMode = new AtomicBoolean(false); // instance starts always in hardmode
	private AtomicBoolean used = new AtomicBoolean(false);
	private byte bossKills = 0;
	private byte endBossKills = 0;
	private Future<?> task;

	@Override
	public void onInstanceDestroy() {
		if (task != null && !task.isCancelled()) {
			task.cancel(true);
		}
	}


	@Override
	public void onDie(Npc npc) {
		switch (npc.getNpcId()) {
			case 235764: // Escapee Asachin, triggers normal mode
				isNormalMode.set(true);
				break;
			case 235775:
			case 235774:
			case 235773:
			case 235772:
				if (!isNormalMode.get()) {
					Npc nc = instance.getNpc(235764);
					if (nc != null) {
						nc.getController().delete();
					}
				}
				bossKills++;
				if (bossKills == 3) {
					spawn(701644, 436.99045f, 496.55634f, 605.95203f, (byte) 2);// Bridge Control
					if (!isNormalMode.get()) {
						spawnRandomBosses();
					} else {
						spawn(235768, 323.016f, 489.295f, 607.645f, (byte) 0); // Spirited Velkur
					}
				}
				break;
			case 235759:
			case 235763:
			case 235767:
			case 235768:
			case 235769:
			case 235770:
			case 235771:
				endBossKills++;
				if (endBossKills == (isNormalMode.get() ? 1 : 2)) {
					spawn(730868, 315.85f, 488.65f, 607.6435f, (byte) 0); // Ophidan Bridge exit
				} else {
					scheduleEnrage();
				}
				break;
			case 235786: // Steel Wall
				npc.getController().delete();
				break;
		}
	}

	private void scheduleEnrage() {
		task = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				if (endBossKills < 2){
					spawn(856059, 322.4960f, 488.3117f, 656.4463f, (byte) 50);
				}
			}
		}, 10000);
	}

	private void spawnRandomBosses() {
		int npcId = 235759 + (Rnd.get(0, 2) * 4);
		spawn(npcId, 325.1554f, 483.4476f, 607.6434f, (byte) 0);
		switch (npcId) {
			case 235759:
				spawn(235769, 323.016f, 489.295f, 607.645f, (byte) 0); // Velkur Aetherknife
				break;
			case 235763:
				spawn(235770, 323.016f, 489.295f, 607.645f, (byte) 0); // Velkur Aetherpriest
				break;
			case 235767:
				spawn(235771, 323.016f, 489.295f, 607.645f, (byte) 0); // Velkur Aetherknife
				break;
		}
	}

	@Override
	public void handleUseItemFinish(Player player, Npc npc) {
		switch (npc.getNpcId()) {
			case 701644:
				if (used.compareAndSet(false, true)) {
					npc.getController().delete();
					spawn(731544, 436.36f, 496.45f, 604.8871f, (byte) 2);
				}
				break;
			case 731544:
				TeleportService.teleportTo(player, player.getWorldId(), 369.532f, 491.7861f, 605.696f, (byte) 60, TeleportAnimation.FADE_OUT_BEAM);
				break;
			case 730868:
				InstanceService.moveToExitPoint(player);
				break;
		}
	}

	@Override
	public boolean onReviveEvent(Player player) {
		PlayerReviveService.revive(player, 25, 25, true, 0);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REBIRTH_MASSAGE_ME());
		player.getGameStats().updateStatsAndSpeedVisually();
		TeleportService.teleportTo(player, mapId, instanceId, 755.21f, 559.292f, 572.9508f, (byte) 86);
		return true;
	}

	@Override
	public boolean onDie(final Player player, Creature lastAttacker) {
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_COMBAT_MY_DEATH());
		PacketSendUtility.sendPacket(player, new SM_DIE(player.haveSelfRezEffect(), player.haveSelfRezItem(), 0, 8));
		return true;
	}
}
