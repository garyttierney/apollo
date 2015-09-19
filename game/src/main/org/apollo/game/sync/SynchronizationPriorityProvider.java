package org.apollo.game.sync;

import org.apollo.game.model.entity.Npc;
import org.apollo.game.model.entity.Player;
import org.apollo.game.model.entity.setting.PrivilegeLevel;

import java.util.List;

/**
 * An implementation of a class which provides a {@code priority} number for a mob during a synchronization task.
 *
 * @author sfix
 */
public final class SynchronizationPriorityProvider {

	/**
	 * Get the priority of the target player for the {@code Player}s update cycle.
	 *
	 * @param updating The player the synchronization task is running for.
	 * @param player The target {@code Mob} to return a {@code priority} for.
	 *
	 * @return A positive integer indicating the priority of the Mob during the update cycle, where a lower number
	 * 		   is considered first.
	 */
	public int getUpdatePriority(Player updating, Player player) {
		PrivilegeLevel playerPrivilegeLevel = player.getPrivilegeLevel();
		if (playerPrivilegeLevel == PrivilegeLevel.MODERATOR || playerPrivilegeLevel == PrivilegeLevel.ADMINISTRATOR) {
			return 0;
		}

		if (updating.getInteractingMob() == player) {
			return 1;
		}

		List<String> friendsUsernames = updating.getFriendUsernames();
		if (friendsUsernames.contains(player.getUsername())) {
			return 2;
		}

		//@todo - a better way of deciding on the priority
		//@todo - do scripts need to extend this?

		List<String> ignoresUsernames = updating.getIgnoredUsernames();
		if (ignoresUsernames.contains(player.getUsername())) {
			return 20;
		}

		return 10;
	}

	/**
	 * Get the priority of the target npc for the {@code Player}s update cycle.

	 * @param updating The player the synchronization task is running for.
	 * @param npc The target {@code Mob} to return a {@code priority} for.
	 *
	 * @return A positive integer indicating the priority of the Mob during the update cycle, where a lower number
	 * 		   is considered first.
	 */
	public int getUpdatePriority(Player updating, Npc npc) {
		return 10;
	}
}
