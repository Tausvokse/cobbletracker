package com.cobbletracker.common.tracker;

/**
 * What a tracker category watches for. {@link #BLOCK} is the seam for block tracking (loot chests and
 * the like) - config accepts it, but nothing implements it yet, so such a category never matches.
 */
public enum TargetKind {
    POKEMON,
    BLOCK
}
