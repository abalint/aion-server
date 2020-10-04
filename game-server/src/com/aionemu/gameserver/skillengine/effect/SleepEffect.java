package com.aionemu.gameserver.skillengine.effect;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.skillengine.model.Effect;

/**
 * @author ATracer
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SleepEffect")
public class SleepEffect extends EffectTemplate {

	@Override
	public void applyEffect(Effect effect) {
		effect.addToEffectedController();
	}

	@Override
	public void calculate(Effect effect) {
		super.calculate(effect, StatEnum.SLEEP_RESISTANCE, null);

	}

	@Override
	public void startEffect(final Effect effect) {
		final Creature effected = effect.getEffected();
		effected.getController().cancelCurrentSkill(effect.getEffector());
		if (effected instanceof Player)
			((Player) effected).getFlyController().onStopGliding();
		effect.setAbnormal(AbnormalState.SLEEP);
		effected.getEffectController().setAbnormal(AbnormalState.SLEEP);
		effect.setCancelOnDmg(true);
	}

	@Override
	public void endEffect(Effect effect) {
		if (effect.getEffected() instanceof Player effected && effect.getEffector().getMaster() instanceof Player)
			effected.incrementSleepCount();
		effect.getEffected().getEffectController().unsetAbnormal(AbnormalState.SLEEP);
	}
}
