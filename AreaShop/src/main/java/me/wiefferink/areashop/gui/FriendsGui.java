package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The menu listing the players that may build in a shop, where they can be removed again.
 */
public class FriendsGui extends Gui {

	/** Rows used for the friends themselves, the last row holds the navigation. */
	private static final int FRIEND_ROWS = 4;

	/** How many friends fit on one page. */
	private static final int PAGE_SIZE = FRIEND_ROWS * ROW;

	private final GeneralRegion region;
	private final Gui parent;
	private int page;

	/**
	 * Construct the friends menu of one shop.
	 * @param player The player looking at it
	 * @param region The region to show the friends of
	 * @param parent The menu to go back to, may be null
	 */
	public FriendsGui(Player player, GeneralRegion region, Gui parent) {
		super(player);
		this.region = region;
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-friendsTitle").replacements(region).toComponent();
	}

	@Override
	protected int rows() {
		return FRIEND_ROWS + 1;
	}

	@Override
	protected void build() {
		if(region.isDeleted() || !region.isOwner(player)) {
			set(22, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			buildNavigation(1);
			return;
		}

		List<OfflinePlayer> friends = friends();
		int pages = Math.max(1, (friends.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, pages - 1));

		if(friends.isEmpty()) {
			set(13, Icon.of(Material.BARRIER)
					.name("panel-noFriendsName")
					.lore("panel-noFriendsLore")
					.build());
		} else {
			int first = page * PAGE_SIZE;
			for(int slot = 0; slot < PAGE_SIZE && first + slot < friends.size(); slot++) {
				OfflinePlayer friend = friends.get(first + slot);
				set(slot, buildFriendIcon(friend), click -> remove(friend));
			}
		}

		buildNavigation(pages);
	}

	/**
	 * Get the friends of the region, sorted by name.
	 * @return The friends of the region
	 */
	private List<OfflinePlayer> friends() {
		List<OfflinePlayer> result = new ArrayList<>();
		for(UUID friend : region.getFriendsFeature().getFriends()) {
			result.add(Bukkit.getOfflinePlayer(friend));
		}
		result.sort(Comparator.comparing(friend -> friend.getName() == null ? "" : friend.getName(), String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	/**
	 * Build the head shown for one friend.
	 * @param friend The friend to show
	 * @return The item
	 */
	private ItemStack buildFriendIcon(OfflinePlayer friend) {
		String name = friend.getName() == null ? friend.getUniqueId().toString() : friend.getName();
		Icon icon = Icon.head(friend).name("panel-friendName", name);
		if(player.hasPermission("areashop.delfriend")) {
			icon.blank().lore("panel-friendRemove");
		}
		return icon.build();
	}

	/**
	 * Take a friend off the region.
	 * @param friend The friend to remove
	 */
	private void remove(OfflinePlayer friend) {
		if(!player.hasPermission("areashop.delfriend")) {
			return;
		}
		if(region.getFriendsFeature().deleteFriend(friend.getUniqueId(), player)) {
			region.update();
			String name = friend.getName() == null ? friend.getUniqueId().toString() : friend.getName();
			plugin.message(player, "delfriend-success", name, region);
		}
		refresh();
	}

	/**
	 * Build the bottom row with the page buttons and the button to add someone.
	 * @param pages Total number of pages
	 */
	private void buildNavigation(int pages) {
		int row = FRIEND_ROWS * ROW;

		if(page > 0) {
			set(row, Icon.of(Material.ARROW).name("panel-previousPage", page, pages).build(), click -> {
				page--;
				refresh();
			});
		}

		if(parent != null) {
			set(row + 3, Icon.of(Material.ARROW).name("panel-back").build(), click -> parent.open());
		}

		if(player.hasPermission("areashop.addfriend")) {
			set(row + 5, Icon.of(Material.LIME_DYE)
					.name("panel-addFriendName")
					.lore("panel-addFriendLore")
					.build(), click -> new AddFriendGui(player, region, this).open());
		}

		if(page < pages - 1) {
			set(row + 8, Icon.of(Material.ARROW).name("panel-nextPage", page + 2, pages).build(), click -> {
				page++;
				refresh();
			});
		}

		fillRow(FRIEND_ROWS);
	}
}
