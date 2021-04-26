package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.units.UnitType;

import java.util.List;
import java.util.ArrayList;

public class Structure implements GameUnit {

    public final UnitType type;
    private double health;
    public boolean upgraded = false;

    public Structure(UnitType type, double health) {
        this.type = type;
        this.health = health;
    }

    public double getTargetHealth() {
        return health;
    }

    public void takeDamage(double damage) {
        health -= damage;
    }

    public void takeSplashDamage(double damage) {
        health -= damage;
    }

    public static PlayerId getPlayer(int yCoord) {
        if (yCoord >= MapBounds.BOARD_SIZE / 2)
            return PlayerId.Player2;
        else if (yCoord <= MapBounds.BOARD_SIZE / 2 - 1)
            return PlayerId.Player1;
        else
            return PlayerId.Error;
    }

    public boolean isStructure() {
        return true;
    }

    // public List<GameUnit> getUnits() {
    // List<GameUnit> ret = new ArrayList<GameUnit>();
    // ret.add(this);
    // return ret;
    // }

}