package ai.events;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.SkillEngine;

import ai.GeneralNpcAI;

/**
 * @author Estrayl
 */
@AIName("birthday_cake")
public class BirthdayCakeAI extends GeneralNpcAI {

	@Override
	public boolean onDialogSelect(Player player, int dialogId, int questId, int extendedRewardIndex) {
		switch (DialogAction.getByActionId(dialogId)) {
			case SETPRO1:
				SkillEngine.getInstance().getSkill(getOwner(), 10821, 1, player).useWithoutPropSkill();
				SkillEngine.getInstance().getSkill(getOwner(), 10822, 1, player).useWithoutPropSkill();
				return true;
		}
		return false;
	}
}