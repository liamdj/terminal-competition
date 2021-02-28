package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.units.UnitType;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Deque;

public class MobileUnits extends GameUnit {

    public final UnitType type;
    // back half contains dead units, front contains undamaged units
    public final List<Double> healths;
    public final Set<Coords> shieldsFrom;
    public Coords coords;
    public final int targetEdge;
    public Deque<Coords> path;

    public MobileUnits(UnitType type, int quantity, double startHealth, Coords coords, int targetEdge) {
        this.type = type;
        this.healths = new ArrayList<Double>();
        for (int i = 0; i < quantity; i++) {
            healths.add(startHealth);
        }
        this.coords = coords;
        this.targetEdge = targetEdge;
        this.shieldsFrom = new TreeSet<Coords>();
    }

    public double getTargetHealth() {
        for (int i = healths.size() - 1; i >= 1; i--)
            if (healths.get(i) > 0)
                return healths.get(i);
        return healths.get(0);
    }

    public void takeDamage(double damage) {
        for (int i = healths.size() - 1; i >= 0; i--)
            if (healths.get(i) > 0) {
                healths.set(i, healths.get(i) - damage);
                return;
            }
    }

    public void takeSplashDamage(double damage) {
        for (int i = 0; i < healths.size(); i++) {
            healths.set(i, healths.get(i) - damage);
        }
    }

    public void removeDead() {
        for (int i = healths.size() - 1; i >= 0; i--)
            if (healths.get(i) <= 0)
                healths.remove(i);
            else
                return;
    }

    public PlayerId getPlayer() {
        if (targetEdge == MapBounds.EDGE_BOTTOM_LEFT || targetEdge == MapBounds.EDGE_BOTTOM_RIGHT)
            return PlayerId.Player2;
        else if (targetEdge == MapBounds.EDGE_TOP_LEFT || targetEdge == MapBounds.EDGE_TOP_RIGHT)
            return PlayerId.Player1;
        else
            return PlayerId.Error;
    }

    public boolean isStructure() {
        return false;
    }
}
