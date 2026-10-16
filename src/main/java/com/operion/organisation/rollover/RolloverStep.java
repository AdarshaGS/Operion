package com.operion.organisation.rollover;

import java.util.ArrayList;
import java.util.List;

/** What one contributor did (or, in a preview, would do): per kind of item how many were copied and how many were already there, plus anything left out and why. */
public record RolloverStep(String key, String label, List<Item> items, List<String> notes) {

	public record Item(String name, int copied, int alreadyThere) {
	}

	public int copiedTotal() {
		return items.stream().mapToInt(Item::copied).sum();
	}

	/** Tallies one item kind while a contributor works through its rows. */
	public static final class Tally {
		private final String name;
		private int copied;
		private int alreadyThere;

		public Tally(String name) {
			this.name = name;
		}

		public void copied() {
			copied++;
		}

		public void alreadyThere() {
			alreadyThere++;
		}

		Item toItem() {
			return new Item(name, copied, alreadyThere);
		}
	}

	public static RolloverStep of(RolloverContributor contributor, List<Tally> tallies, List<String> notes) {
		return new RolloverStep(contributor.key(), contributor.label(), tallies.stream().map(Tally::toItem).toList(), new ArrayList<>(notes));
	}
}
