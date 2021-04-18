package com.c1games.terminal.strategy;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.action.ActionSimulator;
import com.c1games.terminal.algo.map.CanSpawn;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.units.UnitType;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

public class Defense {

    // fraction of health at which turrets are removed (to be replaced)
    private static final double HEALTH_TO_REPLACE_TURRET_RATIO = 0.5;
    // fraced of health at which walls are removed (to be replaced)
    private static final double HEALTH_TO_REPLACE_WALL_RATIO = 0.75;
    private static final double CORES_PER_LIFE = 3;
    private static final double WALL_WEIGHT = 3;
    private static final double ADJ_WEIGHT = 1;
    private static final double DIAG_WEIGHT = 0.67;
    private static final double TWO_RING_WEIGHT = 0.33;
    private static final double TOP_ROW_WEIGHT = 0.5;
    private static final double SECOND_ROW_WEIGHT = 1;
    private static final double THIRD_ROW_WEIGHT = 0.25;
    private static final List<Integer> emptyCols = new ArrayList<>(Arrays.asList(3, 10, 17, 24));

    private Map<UnitPlacement, Double> placements;

    private final GameState move;

    public Defense(GameState move) {
        this.move = move;
        placements = new TreeMap<>();
    }

    public void checkAttack(Attack attack) {
        ActionSimulator sim = new ActionSimulator(move, true);
        for (UnitPlacement units : attack.spawns)
            sim.spawnUnits(units.coords, units.type, units.quantity);
        sim.run();

        double attackWeight = Math.pow(sim.p1CoresLost + sim.p1LivesLost * CORES_PER_LIFE, 2);
        if (attackWeight <= Math.ulp(1))
            return;

        for (Coords coords : sim.unitPath) {
            for (Coords adj : adjacent(coords.x, coords.y)) {
                updatePriority(new UnitPlacement(adj, UnitType.Turret), attackWeight * weight(adj) * ADJ_WEIGHT);
            }
            for (Coords diag : diagonalAdjacent(coords.x, coords.y)) {
                updatePriority(new UnitPlacement(diag, UnitType.Turret), attackWeight * weight(diag) * DIAG_WEIGHT);
            }
            for (Coords ring : twoRing(coords.x, coords.y)) {
                updatePriority(new UnitPlacement(ring, UnitType.Turret), attackWeight * weight(ring) * TWO_RING_WEIGHT);
            }
            updatePriority(new UnitPlacement(coords, UnitType.Wall), attackWeight * weight(coords) * WALL_WEIGHT);
        }
    }

    private void updatePriority(UnitPlacement placement, double weight) {
        if (weight > 0) {
            double curPrior = placements.getOrDefault(placement, 0.0);
            placements.put(placement, curPrior + weight);
        }
    }

    private double weight(Coords coords) {
        if (emptyCols.contains(coords.y) || !MapBounds.inArena(coords))
            return 0;
        if (coords.y == 13)
            return (coords.x == 0 || coords.x == 27) ? SECOND_ROW_WEIGHT : TOP_ROW_WEIGHT;
        if (coords.y == 12)
            return SECOND_ROW_WEIGHT;
        if (coords.y == 11)
            return THIRD_ROW_WEIGHT;
        else
            return 0;
    }

    private List<Coords> adjacent(int x, int y) {
        List<Coords> ret = new ArrayList<>(4);
        ret.add(new Coords(x + 1, y));
        ret.add(new Coords(x, y + 1));
        ret.add(new Coords(x - 1, y));
        ret.add(new Coords(x, y - 1));
        return ret;
    }

    private List<Coords> diagonalAdjacent(int x, int y) {
        List<Coords> ret = new ArrayList<>(4);
        ret.add(new Coords(x + 1, y + 1));
        ret.add(new Coords(x - 1, y + 1));
        ret.add(new Coords(x + 1, y - 1));
        ret.add(new Coords(x - 1, y - 1));
        return ret;
    }

    private List<Coords> twoRing(int x, int y) {
        List<Coords> ret = new ArrayList<>(12);
        ret.add(new Coords(x + 2, y - 1));
        ret.add(new Coords(x + 2, y));
        ret.add(new Coords(x + 2, y + 1));
        ret.add(new Coords(x - 2, y - 1));
        ret.add(new Coords(x - 2, y));
        ret.add(new Coords(x - 2, y + 1));
        ret.add(new Coords(x - 1, y + 2));
        ret.add(new Coords(x, y + 2));
        ret.add(new Coords(x + 1, y + 2));
        ret.add(new Coords(x - 1, y - 2));
        ret.add(new Coords(x, y - 2));
        ret.add(new Coords(x + 1, y - 2));
        return ret;
    }

    public void spendCores(double coreBudget) {
        float myCores = move.data.p1Stats.cores;
        move.data.p1Stats.cores = (float) coreBudget;

        List<Entry<UnitPlacement, Double>> list = new ArrayList<>(placements.entrySet());
        list.sort(Entry.comparingByValue());
        for (Entry<UnitPlacement, Double> entry : list) {
            if (!attemptBuild(move, entry.getKey()))
                break;
        }

        move.data.p1Stats.cores += myCores - (float) coreBudget;
    }

    // Returns false if cannot afford unit and true otherwise
    private boolean attemptBuild(GameState move, UnitPlacement placement) {
        Unit there = move.getWallAt(placement.coords);
        UnitType type = (there != null && placement.type == there.type) ? UnitType.Upgrade : placement.type;
        CanSpawn spawnable = move.canSpawn(placement.coords, type, 1);

        if (spawnable.affirmative()) {
            if (placement.type == UnitType.Upgrade) {
                double startHealth = there.unitInformation.startHealth.getAsDouble();
                if ((there.type == UnitType.Turret && there.health / startHealth > HEALTH_TO_REPLACE_TURRET_RATIO)
                        || (there.type == UnitType.Wall && there.health / startHealth > HEALTH_TO_REPLACE_WALL_RATIO)) {
                    move.attemptUpgrade(placement.coords);
                } else {
                    move.attemptRemoveStructure(placement.coords);
                }
            } else {
                move.attemptSpawn(placement.coords, placement.type);
            }
        } else if (spawnable == CanSpawn.NotEnoughResources) {
            return false;
        } else if (spawnable == CanSpawn.UnitAlreadyPresent) {
            double startHealth = there.unitInformation.startHealth.getAsDouble();
            if ((there.type == UnitType.Wall && placement.type == UnitType.Turret)
                    || (there.type == UnitType.Turret && there.health / startHealth <= HEALTH_TO_REPLACE_TURRET_RATIO)
                    || (there.type == UnitType.Wall && there.health / startHealth <= HEALTH_TO_REPLACE_WALL_RATIO)) {
                move.attemptRemoveStructure(placement.coords);
            }
        }
        return true;
    }

}