package me.wiefferink.areashop.handlers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Mask2D;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import me.wiefferink.areashop.AreaShop;
import me.wiefferink.areashop.interfaces.GeneralRegionInterface;
import me.wiefferink.areashop.interfaces.WorldEditSelection;
import me.wiefferink.areashop.tools.Utils;
import org.bukkit.entity.Player;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Everything AreaShop needs from WorldEdit, built against WorldEdit 7.
 */
public class WorldEditHandler {

	private final AreaShop plugin;

	public WorldEditHandler(AreaShop plugin) {
		this.plugin = plugin;
	}

	/**
	 * Get the selection of a player.
	 * @param player Player to get the selection for
	 * @return WorldEditSelection if the player has selected something, otherwise null
	 */
	public WorldEditSelection getPlayerSelection(Player player) {
		WorldEditPlugin worldEdit = plugin.getWorldEdit();
		if(worldEdit == null) {
			return null;
		}
		try {
			Region region = worldEdit.getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));
			return new WorldEditSelection(
					player.getWorld(),
					BukkitAdapter.adapt(player.getWorld(), region.getMinimumPoint()),
					BukkitAdapter.adapt(player.getWorld(), region.getMaximumPoint())
			);
		} catch(IncompleteRegionException e) {
			return null;
		}
	}

	/**
	 * How many blocks a save or restore is allowed to touch.
	 * @return The configured block limit
	 */
	private int blockLimit() {
		return plugin.getConfig().getInt("maximumBlocks");
	}

	/**
	 * Start an EditSession for a world.
	 * @param world The world to edit
	 * @return A new EditSession, which the caller has to close
	 */
	private EditSession newEditSession(com.sk89q.worldedit.world.World world) {
		return WorldEdit.getInstance().newEditSessionBuilder()
				.world(world)
				.maxBlocks(blockLimit())
				.build();
	}

	/**
	 * Find the world of a region as WorldEdit knows it.
	 * @param regionInterface The region to get the world of
	 * @return The world, or null when it is not loaded
	 */
	private com.sk89q.worldedit.world.World getWorld(GeneralRegionInterface regionInterface) {
		if(regionInterface.getWorld() == null) {
			return null;
		}
		return BukkitAdapter.adapt(regionInterface.getWorld());
	}

	/**
	 * Restore the blocks of a region from a schematic file.
	 * @param rawFile         File to restore from, without the file extension
	 * @param regionInterface Region to restore
	 * @return true when successful, otherwise false
	 */
	public boolean restoreRegionBlocks(File rawFile, GeneralRegionInterface regionInterface) {
		File file = null;
		ClipboardFormat format = null;
		for(ClipboardFormat formatOption : ClipboardFormats.getAll()) {
			for(String extension : formatOption.getFileExtensions()) {
				File fileOption = new File(rawFile.getAbsolutePath() + "." + extension);
				if(fileOption.exists()) {
					file = fileOption;
					format = formatOption;
				}
			}
		}
		if(file == null) {
			AreaShop.info("Did not restore region " + regionInterface.getName() + ", schematic file does not exist: " + rawFile.getAbsolutePath());
			return false;
		}
		AreaShop.debug("Trying to restore region", regionInterface.getName(), "from file", file.getAbsolutePath(), "with format", format.getName());

		com.sk89q.worldedit.world.World world = getWorld(regionInterface);
		if(world == null) {
			AreaShop.info("Did not restore region " + regionInterface.getName() + ", world not found: " + regionInterface.getWorldName());
			return false;
		}

		ProtectedRegion region = regionInterface.getRegion();
		BlockVector3 origin = region.getMinimumPoint();

		try(EditSession editSession = newEditSession(world);
			InputStream input = Files.newInputStream(file.toPath());
			BufferedInputStream buffered = new BufferedInputStream(input);
			ClipboardReader reader = format.getReader(buffered)) {

			Clipboard clipboard = reader.read();
			BlockVector3 dimensions = clipboard.getDimensions();
			if(dimensions.getY() != regionInterface.getHeight()
					|| dimensions.getX() != regionInterface.getWidth()
					|| dimensions.getZ() != regionInterface.getDepth()) {
				AreaShop.warn("Size of the region " + regionInterface.getName() + " is not the same as the schematic to restore!");
				AreaShop.debug("schematic|region, x:" + dimensions.getX() + "|" + regionInterface.getWidth()
						+ ", y:" + dimensions.getY() + "|" + regionInterface.getHeight()
						+ ", z:" + dimensions.getZ() + "|" + regionInterface.getDepth());
			}
			clipboard.setOrigin(clipboard.getMinimumPoint());
			ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);

			// Build operation
			BlockTransformExtent extent = new BlockTransformExtent(clipboardHolder.getClipboard(), clipboardHolder.getTransform());
			ForwardExtentCopy copy = new ForwardExtentCopy(extent, clipboard.getRegion(), clipboard.getOrigin(), editSession, origin);
			copy.setCopyingEntities(false);
			copy.setTransform(clipboardHolder.getTransform());
			// Mask to the region, so pasting into a polygon region does not spill outside of it
			if(region.getType() != RegionType.CUBOID) {
				copy.setSourceMask(new Mask() {
					@Override
					public boolean test(BlockVector3 vector) {
						return region.contains(vector);
					}

					@Override
					public Mask2D toMask2D() {
						return null;
					}
				});
			}
			Operations.complete(copy);
		} catch(MaxChangedBlocksException e) {
			AreaShop.warn("Exceeded the block limit while restoring the schematic of " + regionInterface.getName()
					+ ", limit in exception: " + e.getBlockLimit() + ", limit passed by AreaShop: " + blockLimit());
			return false;
		} catch(IOException e) {
			AreaShop.warn("An error occurred while restoring the schematic of " + regionInterface.getName() + ", enable debug to see the complete stacktrace");
			AreaShop.debug(Utils.getStackTrace(e));
			return false;
		} catch(Exception e) {
			AreaShop.warn("Crashed during the restore of " + regionInterface.getName());
			AreaShop.debug(Utils.getStackTrace(e));
			return false;
		}
		return true;
	}

	/**
	 * Save the blocks of a region to a schematic file.
	 * @param file            File to save to, without the file extension
	 * @param regionInterface Region to save
	 * @return true when successful, otherwise false
	 */
	public boolean saveRegionBlocks(File file, GeneralRegionInterface regionInterface) {
		ClipboardFormat format = ClipboardFormats.findByAlias("sponge");
		if(format == null) {
			// Sponge format does not exist, try to select another one
			for(ClipboardFormat otherFormat : ClipboardFormats.getAll()) {
				format = otherFormat;
			}
			if(format == null) {
				AreaShop.warn("Cannot find a format to save a schematic in, no available formats!");
				return false;
			}
		}

		File target = new File(file.getAbsolutePath() + "." + format.getPrimaryFileExtension());
		AreaShop.debug("Trying to save region", regionInterface.getName(), "to file", target.getAbsolutePath(), "with format", format.getName());

		com.sk89q.worldedit.world.World world = getWorld(regionInterface);
		if(world == null) {
			AreaShop.warn("Did not save region " + regionInterface.getName() + ", world not found: " + regionInterface.getWorldName());
			return false;
		}

		ProtectedRegion region = regionInterface.getRegion();
		CuboidRegion selection = new CuboidRegion(world, region.getMinimumPoint(), region.getMaximumPoint());
		BlockArrayClipboard clipboard = new BlockArrayClipboard(selection);
		clipboard.setOrigin(region.getMinimumPoint());

		try(EditSession editSession = newEditSession(world)) {
			ForwardExtentCopy copy = new ForwardExtentCopy(editSession, selection, clipboard, region.getMinimumPoint());
			copy.setCopyingEntities(false);
			Operations.complete(copy);
		} catch(MaxChangedBlocksException e) {
			AreaShop.warn("Exceeded the block limit while saving the schematic of " + regionInterface.getName()
					+ ", limit in exception: " + e.getBlockLimit() + ", limit passed by AreaShop: " + blockLimit());
			return false;
		} catch(Exception e) {
			AreaShop.warn("Crashed during the save of " + regionInterface.getName());
			AreaShop.debug(Utils.getStackTrace(e));
			return false;
		}

		try(OutputStream output = Files.newOutputStream(target.toPath());
			BufferedOutputStream buffered = new BufferedOutputStream(output);
			ClipboardWriter writer = format.getWriter(buffered)) {
			writer.write(clipboard);
		} catch(IOException e) {
			AreaShop.warn("An error occurred while saving the schematic of " + regionInterface.getName() + ", enable debug to see the complete stacktrace");
			AreaShop.debug(Utils.getStackTrace(e));
			return false;
		} catch(Exception e) {
			AreaShop.warn("Crashed during the save of " + regionInterface.getName());
			AreaShop.debug(Utils.getStackTrace(e));
			return false;
		}
		return true;
	}
}
