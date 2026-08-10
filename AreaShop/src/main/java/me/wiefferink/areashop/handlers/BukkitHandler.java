package me.wiefferink.areashop.handlers;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.Sign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.block.data.type.WallSign;

/**
 * The bits of the Bukkit API AreaShop needs for placing and reading signs.
 */
public class BukkitHandler {

	/**
	 * Get the direction a sign is facing.
	 * @param block Sign block to get the facing from
	 * @return Direction the sign is facing, or null when the block is not a sign
	 */
	public BlockFace getSignFacing(Block block) {
		if(block == null) {
			return null;
		}

		BlockData blockData = block.getBlockData();
		if(blockData instanceof WallSign wallSign) {
			return wallSign.getFacing();
		} else if(blockData instanceof WallHangingSign wallHangingSign) {
			return wallHangingSign.getFacing();
		} else if(blockData instanceof Sign sign) {
			return sign.getRotation();
		} else if(blockData instanceof HangingSign hangingSign) {
			return hangingSign.getRotation();
		}

		return null;
	}

	/**
	 * Set the direction a sign is facing.
	 * @param block  Sign block to update
	 * @param facing Direction to let the sign face
	 * @return true when successful, otherwise false
	 */
	public boolean setSignFacing(Block block, BlockFace facing) {
		if(block == null || facing == null) {
			return false;
		}

		BlockData blockData = block.getBlockData();
		if(blockData instanceof WallSign || blockData instanceof WallHangingSign) {
			// A wall sign can only face the four compass directions
			if(facing.getModY() != 0) {
				return false;
			}
			((Directional)blockData).setFacing(facing);
		} else if(blockData instanceof Sign || blockData instanceof HangingSign) {
			((Rotatable)blockData).setRotation(facing);
		} else {
			return false;
		}

		block.setBlockData(blockData);
		return true;
	}

	/**
	 * Get the block a sign is attached to.
	 * @param block Sign block
	 * @return Block the sign is attached to, or null when not a sign
	 */
	public Block getSignAttachedTo(Block block) {
		if(block == null) {
			return null;
		}

		BlockData blockData = block.getBlockData();
		if(blockData instanceof WallSign wallSign) {
			return block.getRelative(wallSign.getFacing().getOppositeFace());
		} else if(blockData instanceof WallHangingSign wallHangingSign) {
			return block.getRelative(wallHangingSign.getFacing().getOppositeFace());
		} else if(blockData instanceof Sign) {
			return block.getRelative(BlockFace.DOWN);
		} else if(blockData instanceof HangingSign) {
			return block.getRelative(BlockFace.UP);
		}

		return null;
	}
}
