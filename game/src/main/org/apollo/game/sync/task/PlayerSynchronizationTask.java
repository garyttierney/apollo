package org.apollo.game.sync.task;

import org.apollo.game.message.impl.PlayerSynchronizationMessage;
import org.apollo.game.model.Position;
import org.apollo.game.model.entity.MobRepository;
import org.apollo.game.model.entity.Player;
import org.apollo.game.sync.SynchronizationPriorityProvider;
import org.apollo.game.sync.block.AppearanceBlock;
import org.apollo.game.sync.block.ChatBlock;
import org.apollo.game.sync.block.SynchronizationBlock;
import org.apollo.game.sync.block.SynchronizationBlockSet;
import org.apollo.game.sync.seg.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A {@link SynchronizationTask} which synchronizes the specified {@link Player} .
 *
 * @author Graham
 */
public final class PlayerSynchronizationTask extends SynchronizationTask {

	/**
	 * The maximum amount of local players.
	 */
	private static final int MAXIMUM_LOCAL_PLAYERS = 255;

	/**
	 * The maximum number of players to load per cycle. This prevents the update packet from becoming too large (the
	 * client uses a 5000 byte buffer) and also stops old spec PCs from crashing when they login or teleport.
	 */
	private static final int NEW_PLAYERS_PER_CYCLE = 20;

	/**
	 * The Player.
	 */
	private final Player player;

	/**
	 * The synchronization prioritiy provider.
	 */
	private final SynchronizationPriorityProvider priorityProvider = new SynchronizationPriorityProvider();

	/**
	 * A comparator which compares players given their synchronization priority to the current player.
	 */
	private final Comparator<Player> playerPriorityComparator = Comparator.comparingInt(this::getPriority);

	/**
	 * Creates the {@link PlayerSynchronizationTask} for the specified {@link Player}.
	 *
	 * @param player The Player.
	 */
	public PlayerSynchronizationTask(Player player) {
		this.player = player;
	}

	@Override
	public void run() {
		Position lastKnownRegion = player.getLastKnownRegion();
		boolean regionChanged = player.hasRegionChanged();

		SynchronizationBlockSet blockSet = player.getBlockSet();

		if (blockSet.contains(ChatBlock.class)) {
			blockSet = blockSet.clone();
			blockSet.remove(ChatBlock.class);
		}

		Position position = player.getPosition();

		SynchronizationSegment segment = (player.isTeleporting() || player.hasRegionChanged()) ?
				new TeleportSegment(blockSet, position) : new MovementSegment(blockSet, player.getDirections());

		List<Player> localPlayers = player.getLocalPlayerList();
		int oldCount = localPlayers.size();

		List<SynchronizationSegment> segments = new ArrayList<>();
		int distance = player.getViewingDistance();

		for (Iterator<Player> iterator = localPlayers.iterator(); iterator.hasNext(); ) {
			Player other = iterator.next();

			if (removeable(position, distance, other)) {
				iterator.remove();
				segments.add(new RemoveMobSegment());
			} else {
				segments.add(new MovementSegment(other.getBlockSet(), other.getDirections()));
			}
		}

		int added = 0, count = localPlayers.size(), maxNewLocalPlayers = MAXIMUM_LOCAL_PLAYERS - count;
		MobRepository<Player> playerRepository = player.getWorld().getPlayerRepository();

		List<Player> nearbyPlayers = StreamSupport.stream(playerRepository.spliterator(), false)
				.filter(this::considerLocalPlayer)
				.sorted(playerPriorityComparator)
				.limit(maxNewLocalPlayers)
				.collect(Collectors.toList());

		for (Player other : nearbyPlayers) {
			if (count >= MAXIMUM_LOCAL_PLAYERS) {
				player.flagExcessivePlayers();
				break;
			} else if (added >= NEW_PLAYERS_PER_CYCLE) {
				break;
			}

			Position local = other.getPosition();

			if (other != player && local.isWithinDistance(position, distance) && !localPlayers.contains(other)) {
				localPlayers.add(other);
				count++;
				added++;

				blockSet = other.getBlockSet();
				if (!blockSet.contains(AppearanceBlock.class)) { // TODO check if client has cached appearance
					blockSet = blockSet.clone();
					blockSet.add(SynchronizationBlock.createAppearanceBlock(other));
				}

				segments.add(new AddPlayerSegment(blockSet, other.getIndex(), local));
			}
		}

		PlayerSynchronizationMessage message = new PlayerSynchronizationMessage(lastKnownRegion, position,
				regionChanged, segment, oldCount, segments);
		player.send(message);
	}

	/**
	 * Consider the {@code other} player to be added to the local player list.
	 *
	 * @param other The player to consider for the local player list.
	 * @return true if the player should be added to the local player list.
	 */
	private boolean considerLocalPlayer(Player other) {
		Position position = player.getPosition();
		int distance = player.getViewingDistance();
		List<Player> localPlayerList = player.getLocalPlayerList();

		return other != player && !localPlayerList.contains(other) && other.getPosition()
				.isWithinDistance(position, distance);
	}

	private int getPriority(Player other) {
		return priorityProvider.getUpdatePriority(player, other);
	}

	/**
	 * Returns whether or not the specified {@link Player} should be removed.
	 *
	 * @param position The {@link Position} of the Player being updated.
	 * @param other The Player being tested.
	 * @return {@code true} iff the specified Player should be removed.
	 */
	private boolean removeable(Position position, int distance, Player other) {
		if (other.isTeleporting() || !other.isActive()) {
			return true;
		}

		Position otherPosition = other.getPosition();
		return otherPosition.getLongestDelta(position) > distance || !otherPosition.isWithinDistance(position, distance);
	}

}