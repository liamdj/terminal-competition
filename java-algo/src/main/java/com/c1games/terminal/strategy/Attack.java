package com.c1games.terminal.strategy;

import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.action.ActionSimulator;
import com.c1games.terminal.algo.action.GameUnit;
import com.c1games.terminal.algo.action.Structure;
import com.c1games.terminal.algo.units.UnitType;

import java.util.List;
import java.util.ArrayList;

public class Attack {

    public List<UnitPlacement> spawns;
    public int turnsToWait;

    public Attack(int turnsToWait) {
        spawns = new ArrayList<>();
        this.turnsToWait = turnsToWait;
    }

    public boolean add(UnitPlacement placement) {
        return spawns.add(placement);
    }

    private static double attackValue(int turnsWaited, double coresDestroyed, int scores, int p2Health) {
        if (scores >= p2Health)
            return 30 - turnsWaited + 0.33 * (scores - p2Health);
        else
            return (coresDestroyed + 3.5 * scores) / turnsWaited;
    }

    public double test(Config config, List<GameUnit> units, int sinceLastAttack, int p2Health) {
        ActionSimulator sim = new ActionSimulator(config, units);
        for (UnitPlacement placement : spawns)
            sim.spawnUnits(placement.coords, placement.type, placement.quantity);
        sim.run();

        return attackValue(turnsToWait + sinceLastAttack, sim.p2CoresLost, sim.p2LivesLost, p2Health);
    }
}