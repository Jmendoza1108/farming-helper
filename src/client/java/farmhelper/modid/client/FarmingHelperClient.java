package farmhelper.modid.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import farmhelper.modid.FarmingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

public class FarmingHelperClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(FarmingHelper.id("farming_helper"));
	private static KeyMapping helperKey;

	private static boolean enabled = false;
	private static int ticks = 0;

	private static String lastNearbyCrop = "";
	private static String currentNearbyCrop = "";

	private static BlockPos lastPlayerPos = null;
	private static Direction lastMovementDirection  = null;
	private static Direction currentMovementDirection = null;
	private static int ticksSinceMovement = 0;
	private static boolean wasFarmingReady = false;
	private static Direction laneDirection = null;
	private static boolean wasPathBlocked = false;
	private static Double lastPlayerY = null;
	private static boolean waitingForDrop = false;

	@Override
	public void onInitializeClient() {

		helperKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(
						"key.farming-helper.helper",
						InputConstants.Type.KEYSYM,
						InputConstants.KEY_H,
						CATEGORY
				)
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (helperKey.consumeClick()) {
				if (client.player == null) return;

				enabled = !enabled;

				if (enabled) {
					client.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal("Farming Helper: ENABLED")
					);
				} else {

					client.options.keyAttack.setDown(false);
					wasFarmingReady = false;
					laneDirection = null;
					lastPlayerPos = null;
					wasPathBlocked = false;
					currentMovementDirection = null;
					lastMovementDirection = null;
					ticksSinceMovement = 0;
					lastPlayerY = null;
					waitingForDrop = false;


					int totalSeconds = ticks / 20;

					int hours = totalSeconds / 3600;
					int remainingSeconds = totalSeconds % 3600;

					int minutes = remainingSeconds / 60;
					int seconds = remainingSeconds % 60;

					String timeMessage = "Ran for: ";
					if (hours > 0) {
						if (hours == 1) {
							timeMessage += "1 hour";
						} else {
							timeMessage += hours + " hours";
						}
					}
					if (minutes > 0) {
						if (hours > 0) {
							timeMessage += ", ";
						}
						if (minutes == 1) {
							timeMessage += "1 minute";
						} else {
							timeMessage += minutes + " minutes";
						}
					}
					if (seconds > 0) {

						if (hours > 0 || minutes > 0) {
							timeMessage += ", ";
						}

						if (seconds == 1) {
							timeMessage += "1 second";
						} else {
							timeMessage += seconds + " seconds";
						}
					}
					client.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal(timeMessage)
					);

					client.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal("Farming Helper: DISABLED")
					);

					ticks = 0;
				}
			}
			if (enabled && client.player != null && client.level != null) {
				ticks++;
				BlockPos playerPos = client.player.blockPosition().above();
				Direction direction = client.player.getDirection();

				if (lastPlayerPos != null && !playerPos.equals(lastPlayerPos)) {

					ticksSinceMovement = 0;

					int deltaX = playerPos.getX() - lastPlayerPos.getX();
					int deltaZ = playerPos.getZ() - lastPlayerPos.getZ();

					Direction movementDirection = null;

					if (deltaX > 0) {
						movementDirection = Direction.EAST;
					} else if (deltaX < 0) {
						movementDirection = Direction.WEST;
					} else if (deltaZ > 0) {
						movementDirection = Direction.SOUTH;
					} else if (deltaZ < 0) {
						movementDirection = Direction.NORTH;
					}

					currentMovementDirection = movementDirection;

					if (movementDirection != lastMovementDirection) {
						client.player.sendSystemMessage(
								net.minecraft.network.chat.Component.literal(
										"Movement Direction: " + movementDirection
								)
						);
						lastMovementDirection = movementDirection;
					}
				}

				ticksSinceMovement++;

				if (ticksSinceMovement > 5) {
					currentMovementDirection = null;
					lastMovementDirection = null;
				}

				lastPlayerPos = playerPos;

				boolean foundCrop = false;

				currentNearbyCrop = "";

				for (int distance = 1; distance <= 4; distance++) {

					BlockPos checkPos = playerPos.relative(direction, distance);
					BlockState checkState = client.level.getBlockState(checkPos);

					String cropName = getCropName(checkState, client.level);

					if (!cropName.equals("")) {
						foundCrop = true;
						currentNearbyCrop = cropName;
					}

					if (!cropName.equals("") && !cropName.equals(lastNearbyCrop)) {

						client.player.sendSystemMessage(
								net.minecraft.network.chat.Component.literal(
										"Nearby crop: " + cropName
								)
						);

						lastNearbyCrop = cropName;
					}

				}

				if (!foundCrop) {
					lastNearbyCrop = "";
				}


				boolean movingSideways = false;

				if (direction == Direction.NORTH || direction == Direction.SOUTH) {
					movingSideways =
							currentMovementDirection == Direction.EAST
									|| currentMovementDirection == Direction.WEST;

				} else if (direction == Direction.EAST || direction == Direction.WEST) {
					movingSideways =
							currentMovementDirection == Direction.NORTH
									|| currentMovementDirection == Direction.SOUTH;
				}

				boolean validFarmingPosition =
						!currentNearbyCrop.equals("");

				boolean farmingReady =
						validFarmingPosition
						&& currentMovementDirection != null
						&& movingSideways;

				if (farmingReady && laneDirection == null) {
					laneDirection = currentMovementDirection;
				}

				boolean pathBlocked = isPathBlocked(
						client.player.blockPosition(),
						laneDirection,
						client.level
				);

				if(pathBlocked && !wasPathBlocked) {

					waitingForDrop = true;

					client.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal(
									"Lane End: WALL DETECTED - " + laneDirection
							)
					);
				} else if (!pathBlocked && wasPathBlocked) {
					client.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal(
									"Lane End: PATH CLEAR"
							)
					);
				}

				wasPathBlocked = pathBlocked;

				double currentPlayerY = client.player.getY();

				if(lastPlayerY != null) {

					double deltaY = currentPlayerY - lastPlayerY;

					if (waitingForDrop && deltaY < -.05) {

						client.player.sendSystemMessage(
								net.minecraft.network.chat.Component.literal(
										"Lane Transition: DROP DETECTED"
								)
						);

						waitingForDrop = false;

					}

				}

				lastPlayerY = currentPlayerY;

				if (farmingReady && !wasFarmingReady) {

					client.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal(
									"Farming State: READY - "
											+ currentNearbyCrop
									+ " | Farming Direction: "
									+ laneDirection
							)
					);

				} else if (!farmingReady && wasFarmingReady) {

					client.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal(
									"Farming State: NOT READY"
							)
					);
				}

				wasFarmingReady = farmingReady;

				client.options.keyAttack.setDown(farmingReady);

			}
		});
	}
	private static boolean isPathBlocked(
			BlockPos playerFeet,
			Direction laneDirection,
			net.minecraft.client.multiplayer.ClientLevel level
	) {
		if(laneDirection == null) {
			return false;
		}

		BlockPos nextPos = playerFeet.relative(laneDirection);

		BlockState feetState = level.getBlockState(nextPos);
		BlockState headState = level.getBlockState(nextPos.above());

		return !feetState.getCollisionShape(level, nextPos).isEmpty()
				|| !headState.getCollisionShape(level, nextPos.above()).isEmpty();

	}

	private static String getCropName(BlockState blockState, net.minecraft.client.multiplayer.ClientLevel level) {
		if (blockState.is(Blocks.WHEAT)) {
			return "Wheat";

		} else if (blockState.is(Blocks.CARROTS)) {
			return "Carrots";

		} else if (blockState.is(Blocks.POTATOES)) {
			return "Potatoes";

		} else if (blockState.is(Blocks.NETHER_WART)) {
			return "Nether Wart";

		} else if (blockState.is(Blocks.SUGAR_CANE)) {
			return "Sugar Cane";

		} else if (blockState.is(Blocks.COCOA)) {
			return "Cocoa Beans";

		} else if (blockState.is(Blocks.MELON)) {
			return "Melon";

		} else if (blockState.is(Blocks.PUMPKIN)) {
			return "Pumpkin";

		} else if (blockState.is(Blocks.CACTUS)) {
			return "Cactus";

		} else if (blockState.is(Blocks.ROSE_BUSH)) {
			return "Wild Rose";

		} else if (blockState.is(Blocks.SUNFLOWER)) {
			long time = level.getDefaultClockTime() % 24000;

			if (time >= 0 && time < 12000) {
				return "Sunflower";
			} else {
				return "Moonflower";
			}
		}
		return "";
	}
}