package ai.instance.dragonLordsRefuge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.skill.QueuedNpcSkillEntry;
import com.aionemu.gameserver.model.templates.item.ItemAttackType;
import com.aionemu.gameserver.model.templates.npcskill.QueuedNpcSkillTemplate;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;

import ai.AggressiveNpcAI;

/**
 * @author Cheatkiller
 * @reworked Luzien, Estrayl March 8th, 2018
 */
@AIName("tiamat_weakened_dragon")
public class TiamatWeakenedDragonAI extends AggressiveNpcAI {

	private AtomicBoolean hasAggro = new AtomicBoolean();
	private List<Future<?>> spawnTasks = new ArrayList<>();
	private List<Integer> percents = new ArrayList<>();

	public TiamatWeakenedDragonAI(Npc owner) {
		super(owner);
	}

	@Override
	public ItemAttackType modifyAttackType(ItemAttackType type) {
		return ItemAttackType.MAGICAL_FIRE;
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		addPercent();
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (hasAggro.compareAndSet(false, true)) {
			offerAtrocityEvent();
			scheduleSinkingSandEvent();
		}
		checkPercentage(getLifeStats().getHpPercentage());
	}

	private synchronized void checkPercentage(int hpPercentage) {
		for (Integer percent : percents) {
			if (hpPercentage <= percent) {
				percents.remove(percent);
				switch (percent) {
					case 50:
						scheduleDivisiveCreations(60000);
						break;
					case 25:
						scheduleInfinitePain();
						spawnGravityCrusher();
						break;
					case 15:
					case 5:
						spawnGravityCrusher();
						break;
				}
				break;
			}
		}
	}

	private void offerAtrocityEvent() {
		if (!isDead() && hasAggro.get()) {
			int skillId = 20922 + (Rnd.get(3) * 2); // 20922/20924/20926, left,central,right
			if (getLifeStats().getHpPercentage() <= 50)
				getOwner().getQueuedSkills().offer(new QueuedNpcSkillEntry(new QueuedNpcSkillTemplate(20921, 1, 100)));
			getOwner().getQueuedSkills().offer(new QueuedNpcSkillEntry(new QueuedNpcSkillTemplate(skillId, 1, 100)));
		}
	}

	@Override
	public void onStartUseSkill(SkillTemplate skillTemplate) {
		switch (skillTemplate.getSkillId()) {
			case 20922: // Ultimate Atrocity
				spawn(283237, 445.0f, 550.70f, 417.41f, (byte) 0);
				spawn(283241, 469.8f, 543.01f, 417.41f, (byte) 0);
				break;
			case 20924: // Ultimate Atrocity
				spawn(283240, 457.8f, 514.6f, 417.41f, (byte) 0);
				spawn(283240, 462.3f, 514.6f, 417.41f, (byte) 0);
				spawn(283240, 469.7f, 514.6f, 417.41f, (byte) 0);
				spawn(283240, 466.6f, 514.6f, 417.41f, (byte) 0);
				spawn(283240, 473.6f, 514.6f, 417.41f, (byte) 0);
				spawn(283240, 479.3f, 514.6f, 417.41f, (byte) 0);
				spawn(283241, 475.8f, 514.6f, 417.41f, (byte) 0);
				spawn(283241, 491.2f, 514.6f, 417.41f, (byte) 0);
				spawn(283241, 482.7f, 514.6f, 417.41f, (byte) 0);
				spawn(283241, 485.2f, 514.6f, 417.41f, (byte) 0);
				spawn(283241, 488.1f, 514.6f, 417.41f, (byte) 0);
				break;
			case 20926: // Ultimate Atrocity
				spawn(283244, 458.67f, 480.76f, 417.41f, (byte) 0);
				break;
		}
	}

	@Override
	public void onEndUseSkill(SkillTemplate skillTemplate) {
		switch (skillTemplate.getSkillId()) {
			case 20922: // Ultimate Atrocity
			case 20924:
			case 20926:
				offerAtrocityEvent();
				break;
		}
	}

	private void scheduleSinkingSandEvent() {
		spawnTasks.add(ThreadPoolManager.getInstance().schedule(() -> {
			if (!isDead()) {
				int delay = 0;
				for (float degree = -25.0f; degree <= 25.0f; degree += 4) {
					double radian = Math.toRadians(degree);
					spawnTasks.add(ThreadPoolManager.getInstance().schedule(() -> spawnSinkingSandEvent(radian), delay += 1800));
				}
				scheduleSinkingSandEvent();
			}
		}, 120000));
	}

	private void spawnSinkingSandEvent(double radian) {
		float dist = 25.0f;
		for (int i = 0; i < 7; i++) {
			dist += 2.5f;
			float x = (float) (Math.cos(radian) * dist);
			float y = (float) (Math.sin(radian) * dist);
			spawn(283135, getPosition().getX() + x, getPosition().getY() + y, getPosition().getZ(), (byte) 0);
		}
	}

	/**
	 * Limited to 3 times.
	 */
	private void scheduleDivisiveCreations(int delay) {
		spawnTasks.add(ThreadPoolManager.getInstance().schedule(() -> {
			if (hasAggro.get() && !isDead() && delay > 30000) {
				spawn(283139, 464.24f, 462.26f, 417.4f, (byte) 18);
				spawn(283139, 542.79f, 465.03f, 417.4f, (byte) 43);
				spawn(283139, 541.79f, 563.71f, 417.4f, (byte) 74);
				spawn(283139, 465.79f, 565.43f, 417.4f, (byte) 100);
				scheduleDivisiveCreations(delay - 10000);
			}
		}, delay));
	}

	private void spawnGravityCrusher() {
		spawn(283141, 464.24f, 462.26f, 417.4f, (byte) 18);
		spawn(283141, 542.79f, 465.03f, 417.4f, (byte) 43);
		spawn(283141, 541.79f, 563.71f, 417.4f, (byte) 74);
		spawn(283141, 465.79f, 565.43f, 417.4f, (byte) 100);
	}

	private void scheduleInfinitePain() {
		spawnTasks.add(ThreadPoolManager.getInstance().schedule(() -> {
			if (hasAggro.get() && !isDead()) {
				spawn(283143, 508.32f, 515.18f, 417.4f, (byte) 0);
				spawn(283144, 508.32f, 515.18f, 417.4f, (byte) 0);
				scheduleInfinitePain();
			}
		}, 90000));
	}

	private void addPercent() {
		percents.clear();
		Collections.addAll(percents, new Integer[] { 50, 25, 15, 5 });
	}

	private void despawnAdds() {
		WorldMapInstance instance = getPosition().getWorldMapInstance();
		deleteNpcs(instance.getNpcs(283141));
		deleteNpcs(instance.getNpcs(283139));
		deleteNpcs(instance.getNpcs(283140));
	}

	private void deleteNpcs(List<Npc> npcs) {
		for (Npc npc : npcs)
			if (npc != null)
				npc.getController().delete();
	}

	private void cancelTasks() {
		for (Future<?> task : spawnTasks)
			if (task != null && !task.isCancelled())
				task.cancel(true);
	}

	@Override
	protected void handleDied() {
		cancelTasks();
		despawnAdds();
		super.handleDied();
	}

	@Override
	protected void handleDespawned() {
		super.handleDespawned();
		percents.clear();
		despawnAdds();
		cancelTasks();
	}

	@Override
	protected void handleBackHome() {
		cancelTasks();
		hasAggro.set(false);
		addPercent();
		despawnAdds();
		super.handleBackHome();
	}

	@Override
	public boolean ask(AIQuestion question) {
		switch (question) {
			case SHOULD_REWARD:
				return false;
			default:
				return super.ask(question);
		}
	}
}