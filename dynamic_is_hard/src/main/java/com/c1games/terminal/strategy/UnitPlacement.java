package com.c1games.terminal.strategy;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.units.UnitType;

public class UnitPlacement implements Comparable<UnitPlacement> {
    public final Coords coords;
    public final UnitType type;
    public final int quantity;

    public UnitPlacement(Coords coords, UnitType type) {
        this.coords = coords;
        this.type = type;
        this.quantity = 1;
    }

    public UnitPlacement(Coords coords, UnitType type, int quantity) {
        this.coords = coords;
        this.type = type;
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return Integer.toString(quantity) + ' ' + type.toString() + " at " + coords.toString();
    }

    public boolean equals(UnitPlacement other) {
        return compareTo(other) == 0;
    }

    @Override
    public int compareTo(UnitPlacement other) {
        int cmp1 = coords.compareTo(other.coords);
        int cmp2 = Integer.compare(type.ordinal(), other.type.ordinal());
        return cmp1 == 0 ? (cmp2 == 0 ? Integer.compare(quantity, other.quantity) : cmp2) : cmp1;
    }
}