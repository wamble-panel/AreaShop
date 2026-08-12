package me.wiefferink.areashop.gui;

import me.wiefferink.areashop.messages.Message;
import me.wiefferink.areashop.regions.GeneralRegion;
import me.wiefferink.areashop.tools.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The menu that picks a player to give access to a shop.
 *
 * <p>Only players that are online show up, which keeps it to one click and avoids having to type a
 * name. Someone who is offline can still be added with {@code /as addfriend}.
 */
public class AddFriendGui extends Gui {

	/** Rows used for the players, the last row holds the navigation. */
	private static final int PLAYER_ROWS = 4;

	/** How many players fit on one page. */
	private static final int PAGE_SIZE = PLAYER_ROWS * ROW;

	private final GeneralRegion region;
	private final Gui parent;
	private int page;

	/**
	 * Construct the menu that adds a friend to a shop.
	 * @param player The player looking at it
	 * @param region The region to add a friend to
	 * @param parent The menu to go back to, may be null
	 */
	public AddFriendGui(Player player, GeneralRegion region, Gui parent) {
		super(player);
		this.region = region;
		this.parent = parent;
	}

	@Override
	protected Component title() {
		return Message.fromKey("panel-addFriendTitle").replacements(region).toComponent();
	}

	@Override
	protected int rows() {
		return PLAYER_ROWS + 1;
	}

	@Override
	protected void build() {
		if(region.isDeleted() || !region.isOwner(player) || !player.hasPermission("areashop.addfriend")) {
			set(22, Icon.of(Material.BARRIER).name("panel-shopGoneName").lore("panel-shopGoneLore").build());
			buildNavigation(1);
			return;
		}

		List<Player> candidates = candidates();
		int pages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, pages - 1));

		if(candidates.isEmpty()) {
			set(13, Icon.of(Material.BARRIER)
					.name("panel-noPlayersName")
					.lore("panel-noPlayersLore")
					.build());
		} else {
			int first = page * PAGE_SIZE;
			for(int slot = 0; slot < PAGE_SIZE && first + slot < candidates.size(); slot++) {
				Player candidate = candidates.get(first + slot);
				set(slot, buildPlayerIcon(candidate), click -> add(candidate));
			}
		}

		buildNavigation(pages);
	}

	/**
	 * Get the online players that can still be added to this region.
	 * @return The players that are not the owner and not already a friend
	 */
	private List<Player> candidates() {
		Set<UUID> friends = region.getFriendsFeature().getFriends();
		List<Player> result = new ArrayList<>();
		for(Player online : Utils.getOnlinePlayers()) {
			if(friends.contains(online.getUniqueId()) || region.isOwner(online)) {
				continue;
			}
			// Do not reveal players that this one cannot see anyway
			if(!player.canSee(online)) {
				continue;
			}
			result.add(online);
		}
		result.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	/**
	 * Build the head shown for one player.
	 * @param candidate The player to show
	 * @return The item
	 */
	private ItemStack buildPlayerIcon(Player candidate) {
		return Icon.head(candidate)
				.name("panel-addFriendPlayerName", candidate.getName())
				.blank()
				.lore("panel-addFriendPlayerAdd")
				.build();
	}

	/**
	 * Give a player access to the region.
	 * @param candidate The player to add
	 */
	private void add(Player candidate) {
		if(!player.hasPermission("areashop.addfriend")) {
			return;
		}
		if(region.getFriendsFeature().addFriend(candidate.getUniqueId(), player)) {
			region.update();
			plugin.message(player, "addfriend-success", candidate.getName(), region);
		}
		if(parent == null) {
			refresh();
		} else {
			parent.open();
		}
	}

	/**
	 * Build the bottom row with the page buttons.
	 * @param pages Total number of pages
	 */
	private void buildNavigation(int pages) {
		int row = PLAYER_ROWS * ROW;

		if(page > 0) {
			set(row, Icon.of(Material.ARROW).name("panel-previousPage", page, pages).build(), click -> {
				page--;
				refresh();
			});
		}

		if(parent != null) {
			set(row + 4, Icon.of(Material.ARROW).name("panel-back").build(), click -> parent.open());
		}

		if(page < pages - 1) {
			set(row + 8, Icon.of(Material.ARROW).name("panel-nextPage", page + 2, pages).build(), click -> {
				page++;
				refresh();
			});
		}

		fillRow(PLAYER_ROWS);
	}
}
