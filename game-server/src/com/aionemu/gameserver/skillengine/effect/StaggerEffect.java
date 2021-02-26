package com.aionemu.gameserver.skillengine.effect;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.skillengine.model.SubEffectType;
import com.aionemu.gameserver.skillengine.model.SpellStatus;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * @author ATracer
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StaggerEffect")
public class StaggerEffect extends EffectTemplate {

	@Override
	public void applyEffect(Effect effect) {
		effect.addToEffectedController();
	}

	@Override
	public void startEffect(Effect effect) {
		final Creature effected = effect.getEffected();
		effected.getController().cancelCurrentSkill(effect.getEffector());
		effected.getEffectController().removeParalyzeEffects();
		if (effected instanceof Player player) {
			player.getFlyController().onStopGliding();
			player.getMoveController().abortMove();
		}
		effect.getEffected().getEffectController().setAbnormal(AbnormalState.STAGGER);
		effect.setAbnormal(AbnormalState.STAGGER);
		World.getInstance().updatePosition(effected, effect.getTargetX(), effect.getTargetY(), effect.getTargetZ(), effected.getHeading());
	}

	@Override
	public void calculate(Effect effect) {
		if (effect.getEffected().getEffectController().isAbnormalSet(AbnormalState.PULLED)
			|| effect.getEffected().getEffectController().isAbnormalSet(AbnormalState.STAGGER)
			|| effect.getEffected().getEffectController().isAbnormalSet(AbnormalState.OPENAERIAL)
			|| effect.getEffected().getEffectController().isAbnormalSet(AbnormalState.STUMBLE))
			return;

		if (!super.calculate(effect, StatEnum.STAGGER_RESISTANCE, SpellStatus.STAGGER))
			return;
		if (effect.isSubEffect())
			effect.setSubEffectType(SubEffectType.STAGGER);
		final Creature effector = effect.getEffector();
		final Creature effected = effect.getEffected();
		// Move effected 3 meters backward as on retail
		double radian = Math.toRadians(PositionUtil.convertHeadingToAngle(effector.getHeading()));
		float x1 = (float) (Math.cos(radian) * 3);
		float y1 = (float) (Math.sin(radian) * 3);
		Vector3f closestCollision = GeoService.getInstance().getClosestCollision(effected, effected.getX() + x1, effected.getY() + y1, effected.getZ());
		effect.setTargetLoc(closestCollision.getX(), closestCollision.getY(), closestCollision.getZ());
	}

	@Override
	public void endEffect(Effect effect) {
		effect.getEffected().getEffectController().unsetAbnormal(AbnormalState.STAGGER);
	}
}
