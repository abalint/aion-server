package ai.instance.unstableSplinterpath;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.LOG;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.TYPE;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author Ritsu, Luzien
 * @edit Cheatkiler
 */
@AIName("unstablerukril")
public class UnstableRukrilAI extends AggressiveNpcAI {

	private AtomicBoolean isHome = new AtomicBoolean(true);
	private Future<?> skillTask;

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		checkPercentage(getLifeStats().getHpPercentage());
		regen();
	}

	private void checkPercentage(int hpPercentage) {
		if (hpPercentage <= 95 && isHome.compareAndSet(true, false)) {
			startSkillTask();
		}
	}

	private void startSkillTask() {
		final Npc ebonsoul = getPosition().getWorldMapInstance().getNpc(219552);
		skillTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				if (isAlreadyDead())
					cancelTask();
				else {
					if (getPosition().getWorldMapInstance().getNpc(283204) == null) {
						SkillEngine.getInstance().getSkill(getOwner(), 19266, 55, getOwner()).useNoAnimationSkill();
						spawn(283204, getOwner().getX() + 2, getOwner().getY() - 2, getOwner().getZ(), (byte) 0);
					}
					if (ebonsoul != null && !ebonsoul.getLifeStats().isAlreadyDead()) {
						SkillEngine.getInstance().getSkill(ebonsoul, 19159, 55, ebonsoul).useNoAnimationSkill();
						spawn(283205, ebonsoul.getX() + 2, ebonsoul.getY() - 2, ebonsoul.getZ(), (byte) 0);
					}
				}
			}
		}, 5000, 70000);
	}

	private void cancelTask() {
		if (skillTask != null && !skillTask.isCancelled()) {
			skillTask.cancel(true);
		}
	}

	private void regen() {
		Npc ebonsoul = getPosition().getWorldMapInstance().getNpc(219552);
		if (ebonsoul != null && !ebonsoul.getLifeStats().isAlreadyDead() && MathUtil.isIn3dRange(getOwner(), ebonsoul, 5))
			if (!getOwner().getLifeStats().isFullyRestoredHp())
				getOwner().getLifeStats().increaseHp(TYPE.HP, 10000, 0, LOG.REGULAR);

	}

	@Override
	protected void handleDied() {
		super.handleDied();
		cancelTask();
	}

	@Override
	protected void handleBackHome() {
		super.handleBackHome();
		cancelTask();
		isHome.set(true);
		getEffectController().removeEffect(19266);
	}
}