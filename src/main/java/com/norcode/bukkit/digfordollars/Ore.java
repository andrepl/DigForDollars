package com.norcode.bukkit.digfordollars;


import org.bukkit.Material;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;

public class Ore {

	private static HashMap<Material, Ore> byMaterial = new HashMap<Material, Ore>();
	private static HashMap<String, Ore> byName = new HashMap<String, Ore>();

	public Ore(String name, String displayName, String displayNamePlural, double value, boolean checkDrops, EnumSet<Material> materials) {
		this.checkDrops = checkDrops;
		this.name = name.toLowerCase();
		this.displayName = displayName;
		this.displayNamePlural = displayNamePlural;
		this.materials = materials;
		this.value = value;
		for (Material m: this.materials) {
			byMaterial.put(m, this);
		}
		byName.put(this.name, this);
	}

	public static Ore getByMaterial(Material mat) {
		return byMaterial.get(mat);
	}

	public static Ore getByName(String name) {
		return byName.get(name.toLowerCase());
	}

	private final String name;
	private final String displayName;
	private final String displayNamePlural;
	private final EnumSet<Material> materials;
	private final double value;
	private final boolean checkDrops;

	public static void reset() {
		byName.clear();
		byMaterial.clear();
	}

	public String toString() {
		return "<Ore: '" + this.displayName + "' $" + value + ">";
	}

	public static boolean containsName(String name) {
		return getByName(name) != null;
	}

	public String getRequiredPermission() {
		return "digfordollars.payfor." + this.name;
	}

	public String getDisplayName() {
		return displayName;
	}

	public double getValue() {
		return value;
	}

	public String getDisplayNamePlural() {
		return displayNamePlural;
	}

	public static Collection<Ore> values() {
		return byName.values();
	}

	public boolean isCheckDrops() {
		return checkDrops;
	}
}
