package instance;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.animations.TeleportAnimation;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.StaticDoor;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.services.teleport.TeleportService2;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;

import javolution.util.FastTable;

/**
 * After activating the start device one of three game events will be chosen.
 * The first one is a key collecting challenge with a time limit about 5
 * minutes. After that all monster and chests will despawn. It happens in
 * the first room, the way to the next one is blocked. After time expires
 * a portal to the prisons will be spawned, where you can open the doors
 * with your keys to kill the NPC behind them and get some ap relics.
 * 
 * The second one seems to be a hide&seek with a time limit about 5
 * minutes, too. The goal of this is actually not clear. After time expires
 * you get access to the last room with a "Test Subject ..." NPC, who drops
 * five rare manastone bundles. There is also a chest containing relics
 * behind the test subject.
 * 
 * The third one is a tower defense game, where wave of balaur spawn and
 * try to kill something. It seems to be finished after 5 minutes too with
 * the commander wave. Maybe he drops the missing ap relics or rare
 * manastone bundles. (written by Estrayl)
 * 
 * @author Yeats
 * TODO: Mini Game 2 & 3
 */
@InstanceID(300480000)
public class DanuarMysticariumInstance extends GeneralInstanceHandler {

	private Map<Integer, StaticDoor> doors;
	private List<Future<?>> tasks;

	@Override
	public void onInstanceCreate(WorldMapInstance instance) {
		super.onInstanceCreate(instance);
		doors = instance.getDoors();
	}

	@Override
	public void onOpenDoor(int door) {
		if (doors.containsKey(door)) {
			doors.remove(door);
			switch (door) {
				case 101:
					spawn(219963, 212.068f, 510.02f, 153.23f, (byte) 115);
					break;
				case 7:
					spawn(219963, 241.602f, 541.79f, 152.591f, (byte) 95);
					break;
				case 11:
					spawn(219963, 317.654f, 545.801f, 148.8f, (byte) 80);
					break;
				case 6:
					spawn(219964, 225.53f, 529.7f, 153.04f, (byte) 100);
					break;
				case 10:
					spawn(219964, 295.04f, 547.48f, 148.73f, (byte) 90);
					break;
				case 8:
					spawn(219965, 262.17f, 545.68f, 150.51f, (byte) 85);
					break;
				case 12:
					spawn(219965, 336.94f, 532.89f, 148.472f, (byte) 75);
					break;
				case 13:
					spawn(219969, 348.14f, 512.56f, 148.19f, (byte) 65);
					break;
			}
		}
	}

	@Override
	public void handleUseItemFinish(Player player, Npc npc) {
		switch (npc.getNpcId()) {
			case 731583:
				startTasks();
				doors.get(3).setOpen(true);
				sendMsg(1402801);
				TeleportService2.teleportTo(player, mapId, instanceId, 140.45f, 182.2f, 242f, (byte) 10, TeleportAnimation.FADE_OUT_BEAM);
				npc.getController().delete();
				break;
			case 702715:
				TeleportService2.teleportTo(player, mapId, instanceId, 236.1f, 488.86f, 152f, (byte) 25, TeleportAnimation.FADE_OUT_BEAM);
				break;
			case 702717:
				TeleportService2.moveToInstanceExit(player, mapId, player.getRace());
				break;
		}
	}

	private void startTasks() {
		tasks = new FastTable<>();
		tasks.add(ThreadPoolManager.getInstance().schedule((Runnable) () -> sendMsg(1402802), 125000));
		tasks.add(ThreadPoolManager.getInstance().schedule((Runnable) () -> sendMsg(1402803), 155000));
		tasks.add(ThreadPoolManager.getInstance().schedule((Runnable) () -> sendMsg(1402804), 175000));
		tasks.add(ThreadPoolManager.getInstance().schedule((Runnable) () -> {
			instance.getNpcs().stream().filter(npc -> npc != null && (npc.getNpcId() == 219958 || npc.getNpcId() == 219959 ||
					npc.getNpcId() == 702700 || npc.getNpcId() == 702701)).forEach(npc -> npc.getController().delete());
			spawn(702715, 169.366f, 208.93f, 188.02f, (byte) 0);
			sendMsg(1402805);
			sendMsg(1402806);
		}, 185000)); //3min 5sec
	}

	private void cancelTasks() {
		if (tasks != null) {
			tasks.stream().filter(future -> future != null && !future.isCancelled()).forEach(future -> future.cancel(true));
		}
	}

	@Override
	public void onInstanceDestroy() {
		cancelTasks();
		doors.clear();
	}

	@Override
	public boolean onDie(final Player player, Creature lastAttacker) {
		PacketSendUtility.sendPacket(player, new SM_DIE(player.haveSelfRezEffect(), player.haveSelfRezItem(), 0, 8));
		return true;
	}

	@Override
	public void onExitInstance(Player player) {
		// remove keys
		TeleportService2.moveToInstanceExit(player, mapId, player.getRace());
	}
}
