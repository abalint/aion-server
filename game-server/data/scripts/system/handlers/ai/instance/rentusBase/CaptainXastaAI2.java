package ai.instance.rentusBase;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.ai2.AI2Actions;
import com.aionemu.gameserver.ai2.AIName;
import com.aionemu.gameserver.ai2.AIState;
import com.aionemu.gameserver.ai2.NpcAI2;
import com.aionemu.gameserver.ai2.event.AIEventType;
import com.aionemu.gameserver.ai2.manager.EmoteManager;
import com.aionemu.gameserver.ai2.manager.WalkManager;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.actions.NpcActions;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;

import ai.AggressiveNpcAI2;

/**
 * @author xTz
 */
@AIName("captain_xasta")
public class CaptainXastaAI2 extends AggressiveNpcAI2 {

	private boolean canThink = true;
	private Future<?> phaseTask;
	private AtomicBoolean isHome = new AtomicBoolean(true);

	@Override
	public boolean canThink() {
		return canThink;
	}

	@Override
	public void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (isHome.compareAndSet(true, false)) {
			if (getNpcId() == 217309) {
				PacketSendUtility.broadcastMessage(getOwner(), 1500388);
				startPhaseTask(this);
			} else {
				startPhase2Task();
			}
		}
	}

	private void cancelPhaseTask() {
		if (phaseTask != null && !phaseTask.isDone()) {
			phaseTask.cancel(true);
		}
	}

	private void startPhaseTask(final NpcAI2 ai) {
		phaseTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				if (isAlreadyDead()) {
					cancelPhaseTask();
				} else {
					canThink = false;
					EmoteManager.emoteStopAttacking(getOwner());
					getSpawnTemplate().setWalkerId("B186C8F43FF13FDD50FA9483B7D8C2BEABAE7F5C");
					WalkManager.startWalking(ai);
					startRun(getOwner());
					spawnHelpers(ai);
					startSanctuaryEvent();
				}
			}
		}, 28000, 28000);
	}

	@Override
	protected void handleMoveArrived() {
		super.handleMoveArrived();
		if (getSpawnTemplate().getWalkerId() != null) {
			getSpawnTemplate().setWalkerId(null);
			WalkManager.stopWalking(this);
		}
	}

	private void startPhase2Task() {
		phaseTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				if (isAlreadyDead()) {
					cancelPhaseTask();
				} else {
					SkillEngine.getInstance().getSkill(getOwner(), 19729, 60, getOwner()).useNoAnimationSkill();
					PacketSendUtility.broadcastMessage(getOwner(), 1500392);
				}
			}
		}, 30000, 30000);
	}

	private void startSanctuaryEvent() {
		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (!isAlreadyDead()) {
					canThink = true;
					Creature creature = getAggroList().getMostHated();
					if (creature == null || creature.getLifeStats().isAlreadyDead() || !getOwner().canSee(creature)) {
						setStateIfNot(AIState.FIGHT);
						getMoveController().recallPreviousStep();
						getMoveController().abortMove();
						onGeneralEvent(AIEventType.ATTACK_FINISH);
						onGeneralEvent(AIEventType.BACK_HOME);
					} else {
						getMoveController().abortMove();
						getOwner().setTarget(creature);
						getOwner().getGameStats().renewLastAttackTime();
						getOwner().getGameStats().renewLastAttackedTime();
						getOwner().getGameStats().renewLastChangeTargetTime();
						getOwner().getGameStats().renewLastSkillTime();
						setStateIfNot(AIState.FIGHT);
						handleMoveValidate();
						SkillEngine.getInstance().getSkill(getOwner(), 19657, 60, getOwner()).useNoAnimationSkill();
					}
				}
			}

		}, 23000);
	}

	private void startRun(Npc npc) {
		npc.setState(1);
		PacketSendUtility.broadcastPacket(npc, new SM_EMOTION(npc, EmotionType.START_EMOTE2, 0, npc.getObjectId()));
	}

	private void spawnHelpers(final NpcAI2 ai) {
		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (!isAlreadyDead()) {
					PacketSendUtility.broadcastMessage(getOwner(), 1500389);
					SkillEngine.getInstance().getSkill(getOwner(), 19968, 60, getOwner()).useNoAnimationSkill();
					Npc npc1 = (Npc) spawn(282604, 263f, 537f, 203f, (byte) 0);
					Npc npc2 = (Npc) spawn(282604, 186f, 555f, 203f, (byte) 0);
					npc1.getSpawn().setWalkerId("30028000014");
					WalkManager.startWalking((NpcAI2) npc1.getAi2());
					npc2.getSpawn().setWalkerId("30028000015");
					WalkManager.startWalking((NpcAI2) npc2.getAi2());
					startRun(npc1);
					startRun(npc2);
				}
			}

		}, 3000);
	}

	private void deleteHelpers() {
		WorldMapInstance instance = getPosition().getWorldMapInstance();
		if (instance != null) {
			deleteNpcs(instance.getNpcs(282604));
		}
	}

	private void deleteNpcs(List<Npc> npcs) {
		for (Npc npc : npcs) {
			if (npc != null) {
				npc.getController().delete();
			}
		}
	}

	@Override
	protected void handleDied() {
		cancelPhaseTask();
		if (getNpcId() == 217309) {
			PacketSendUtility.broadcastMessage(getOwner(), 1500390);
			spawn(217310, 238.160f, 598.624f, 178.480f, (byte) 0);
			deleteHelpers();
			AI2Actions.deleteOwner(this);
		} else {
			PacketSendUtility.broadcastMessage(getOwner(), 1500391);
			final WorldMapInstance instance = getPosition().getWorldMapInstance();
			if (instance != null) {
				final Npc ariana = instance.getNpc(799668);
				if (ariana != null) {
					ariana.getEffectController().removeEffect(19921);
					ThreadPoolManager.getInstance().schedule(new Runnable() {

						@Override
						public void run() {
							ariana.getSpawn().setWalkerId("30028000016");
							WalkManager.startWalking((NpcAI2) ariana.getAi2());
						}

					}, 1000);
					PacketSendUtility.broadcastMessage(ariana, 1500415, 4000);
					PacketSendUtility.broadcastMessage(ariana, 1500416, 13000);
					ThreadPoolManager.getInstance().schedule(new Runnable() {

						@Override
						public void run() {
							if (instance != null) {
								SkillEngine.getInstance().getSkill(ariana, 19358, 60, ariana).useNoAnimationSkill();
								instance.getDoors().get(145).setOpen(true);
								deleteNpcs(instance.getNpcs(701156));
								ThreadPoolManager.getInstance().schedule(new Runnable() {

									@Override
									public void run() {
										if (ariana != null && !NpcActions.isAlreadyDead(ariana)) {
											NpcActions.delete(ariana);
										}
									}

								}, 13000);
							}
						}

					}, 13000);
				}
			}
		}
		super.handleDied();
	}

	@Override
	protected void handleDespawned() {
		cancelPhaseTask();
		super.handleDespawned();
	}

	@Override
	protected void handleBackHome() {
		canThink = true;
		cancelPhaseTask();
		deleteHelpers();
		isHome.set(true);
		super.handleBackHome();
	}
}
