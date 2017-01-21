package instance;

import static com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE.STR_REBIRTH_MASSAGE_ME;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.Storage;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAY_MOVIE;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.zone.ZoneInstance;

import javolution.util.FastTable;

/**
 * @author xTz, Gigi
 * @reworked Pad
 */
@InstanceID(300230000)
public class KromedesTrialInstance extends GeneralInstanceHandler {

	private int skillId;
	private boolean isPlayerInManor = false;
	private AtomicBoolean isBossSpawned = new AtomicBoolean();
	private List<Integer> sentMovies = new FastTable<>();

	@Override
	public void onEnterInstance(Player player) {
		skillId = player.getRace() == Race.ASMODIANS ? 19270 : 19220;
		sendMovie(player, 453);
		SkillEngine.getInstance().applyEffectDirectly(skillId, player, player, 0);
	}

	@Override
	public void onPlayerLogOut(Player player) {
		player.getEffectController().removeEffect(skillId);
	}

	@Override
	public void onLeaveInstance(Player player) {
		player.getEffectController().removeEffect(skillId);
		removeInstanceItems(player);
	}

	@Override
	public void onEnterZone(Player player, ZoneInstance zone) {
		switch (zone.getAreaTemplate().getZoneName().toString()) {
			case "MANOR_ENTRANCE_300230000":
				sendMovie(player, 462);
				break;
			case "KALIGA_LIBRARY_300230000":
				PacketSendUtility.sendMonologue(player, 1111370); // The door to the Kaliga Treasury should be around here somewhere....
				break;
			case "KALIGA_TREASURY_300230000":
				if (isBossSpawned.compareAndSet(false, true)) {
					Npc wyr = instance.getNpc(217002);
					Npc angerr = instance.getNpc(217000);
					Npc hamam = instance.getNpc(216982);
					if (isDead(wyr) && isDead(angerr) && isDead(hamam)) {
						spawn(217005, 669.214f, 774.387f, 216.88f, (byte) 60);
						spawn(217001, 663.8805f, 779.1967f, 216.26213f, (byte) 60);
						spawn(217003, 663.0468f, 774.6116f, 216.26215f, (byte) 60);
						spawn(217004, 663.0468f, 770.03815f, 216.26212f, (byte) 60);
					} else {
						spawn(217006, 669.214f, 774.387f, 216.88f, (byte) 60);
					}
				}
				break;
		}
	}

	@Override
	public void onPlayMovieEnd(Player player, int movieId) {
		if (movieId == 454) {
			Npc magasPotion = instance.getNpc(730308);
			if (magasPotion != null && MathUtil.isIn3dRange(player, magasPotion, 20)) {
				int relicKeyId = 185000109;
				player.getInventory().decreaseByItemId(relicKeyId, player.getInventory().getItemCountByItemId(relicKeyId));
				TeleportService.teleportTo(player, mapId, instanceId, 687.56116f, 681.68225f, 200.28648f, (byte) 30);
			}
		}
	}

	@Override
	public void onDie(Npc npc) {
		switch (npc.getNpcId()) {
			case 216968: // Divine Hisen
				isPlayerInManor = true;
				break;
			case 282093: // Mana Relic
				removeKaligaBuff(19248);
				scheduleRespawn(npc);
				npc.getController().delete();
				break;
			case 282095: // Strength Relic
				removeKaligaBuff(19247);
				scheduleRespawn(npc);
				npc.getController().delete();
				break;
		}
	}

	@Override
	public boolean onDie(Player player, Creature lastAttacker) {
		PacketSendUtility.sendPacket(player, new SM_DIE(player.haveSelfRezEffect(), player.haveSelfRezItem(), 0, 8));
		return true;
	}

	@Override
	public boolean onReviveEvent(Player player) {
		PlayerReviveService.revive(player, 25, 25, true, 0);
		player.getGameStats().updateStatsAndSpeedVisually();
		PacketSendUtility.sendPacket(player, STR_REBIRTH_MASSAGE_ME());
		if (!isPlayerInManor)
			TeleportService.teleportTo(player, mapId, instanceId, 248, 244, 189);
		else
			TeleportService.teleportTo(player, mapId, instanceId, 686, 676, 201);
		SkillEngine.getInstance().applyEffectDirectly(skillId, player, player, 0);
		return true;
	}

	private void removeKaligaBuff(int skillId) {
		Npc kaliga = instance.getNpc(217006);
		if (kaliga != null && !kaliga.getLifeStats().isAlreadyDead()) {
			kaliga.getEffectController().removeEffect(skillId);
		}
	}

	private void scheduleRespawn(Npc npc) {
		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				Npc kaliga = instance.getNpc(217006);
				if (kaliga != null && !kaliga.getLifeStats().isAlreadyDead()) {
					SpawnTemplate npcST = npc.getSpawn();
					SpawnTemplate newST = SpawnEngine.addNewSingleTimeSpawn(npcST.getWorldId(), npc.getNpcId(), npcST.getX(), npcST.getY(), npcST.getZ(),
						npcST.getHeading());
					newST.setStaticId(npcST.getStaticId());
					SpawnEngine.spawnObject(newST, instanceId);
				}
			}
		}, 10000);
	}

	private boolean isDead(Npc npc) {
		return npc == null || npc.getLifeStats().isAlreadyDead();
	}

	private void sendMovie(Player player, int movieId) {
		if (!sentMovies.contains(movieId)) {
			sentMovies.add(movieId);
			PacketSendUtility.sendPacket(player, new SM_PLAY_MOVIE(0, movieId));
		}
	}

	private void removeInstanceItems(Player player) {
		List<Integer> items = FastTable.of(185000098, // Temple Vault Door Key
			185000099, // Dungeon Grate Key
			185000100, // Dungeon Door Key
			185000109 // Relic Key
		);
		Storage storage = player.getInventory();
		for (int item : items)
			storage.decreaseByItemId(item, storage.getItemCountByItemId(item));
	}
}
