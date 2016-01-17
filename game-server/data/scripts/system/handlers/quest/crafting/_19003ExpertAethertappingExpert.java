package quest.crafting;

import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.QuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;

/**
 * @author Gigi
 */
public class _19003ExpertAethertappingExpert extends QuestHandler {

	private final static int questId = 19003;

	public _19003ExpertAethertappingExpert() {
		super(questId);
	}

	@Override
	public void register() {
		qe.registerQuestNpc(203782).addOnQuestStart(questId);
		qe.registerQuestNpc(203782).addOnTalkEvent(questId);
		qe.registerQuestNpc(203700).addOnTalkEvent(questId);
	}

	@Override
	public boolean onDialogEvent(QuestEnv env) {
		final Player player = env.getPlayer();
		QuestState qs = player.getQuestStateList().getQuestState(questId);

		int targetId = 0;
		if (env.getVisibleObject() instanceof Npc)
			targetId = ((Npc) env.getVisibleObject()).getNpcId();

		if (qs == null || qs.getStatus() == QuestStatus.NONE) {
			if (targetId == 203782) {
				if (env.getDialog() == DialogAction.QUEST_SELECT) {
					if (giveQuestItem(env, 182206128, 1))
						return sendQuestDialog(env, 1011);
					else
						return true;
				}
				else
					return sendQuestStartDialog(env);
			}
		}

		if (qs == null)
			return false;

		if (qs != null && qs.getStatus() == QuestStatus.START) {
			switch (targetId) {
				case 203700: {
					switch (env.getDialog()) {
						case QUEST_SELECT:
							qs.setStatus(QuestStatus.REWARD);
							updateQuestStatus(env);
							return sendQuestDialog(env, 2375);
					}
				}
			}
		}
		else if (qs.getStatus() == QuestStatus.REWARD) {
			if (targetId == 203700) {
				if (env.getDialogId() == DialogAction.CHECK_USER_HAS_QUEST_ITEM.id())
					return sendQuestDialog(env, 5);
				else {
					player.getSkillList().addSkill(player, 30003, 400);
					removeQuestItem(env, 182206128, 1);
					return sendQuestEndDialog(env);
				}
			}
		}
		return false;
	}
}
