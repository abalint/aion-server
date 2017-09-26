package admincommands;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.chathandlers.AdminCommand;

/**
 * @author Hilgert
 * @reworked Estrayl
 */
public class Dispel extends AdminCommand {

	public Dispel() {
		super("dispel", "Removes all effects including transformations.");
	}

	@Override
	public void execute(Player admin, String... params) {
		VisibleObject target = admin.getTarget();
		if (target == null)
			target = admin;

		if (target instanceof Creature) {
			((Creature) target).getEffectController().removeAllEffects();
			((Creature) target).getEffectController().removeTransformEffects();
			PacketSendUtility.sendMessage(admin, "Removed all effects of " + target + ".");
		}
	}
}
