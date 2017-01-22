package ai.instance.beshmundirTemple;

import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIALOG_WINDOW;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PacketSendUtility;

import ai.ActionItemNpcAI;

/**
 * @author Gigi
 */
@AIName("door2")
public class Door2AI extends ActionItemNpcAI {

	@Override
	protected void handleDialogStart(Player player) {
		QuestState qsneedasmo = player.getQuestStateList().getQuestState(30311);
		QuestState qsneedelyos = player.getQuestStateList().getQuestState(30211);
		if (player.getRace() == Race.ELYOS) {
			if (qsneedelyos != null && qsneedelyos.getStatus() != QuestStatus.NONE) {// TODO: Only one player in group has to // have this quest
				super.handleDialogStart(player);
			} else {
				PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 27));
			}
		} else {
			if (qsneedasmo != null && qsneedasmo.getStatus() != QuestStatus.NONE) { // TODO: Only one player in group has to // have this quest
				super.handleDialogStart(player);
			} else {
				PacketSendUtility.sendPacket(player, new SM_DIALOG_WINDOW(getObjectId(), 27));
			}
		}
	}

	@Override
	protected void handleUseItemFinish(Player player) {
		AIActions.deleteOwner(this);
	}
}