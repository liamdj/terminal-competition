package com.c1games.terminal.strategy;

import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.action.ActionSimulator;
import com.c1games.terminal.algo.action.GameUnit;
import com.c1games.terminal.algo.action.Structure;
import com.c1games.terminal.algo.action.UnitInformationContainer;
import com.c1games.terminal.algo.action.StructBoard;
import com.c1games.terminal.algo.units.UnitType;
import com.c1games.terminal.algo.map.Unit;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Deque;

public class Attack {

    private static double CORES_PER_LIFE = 3;
    private static double TURNS_PER_EXCESS_LIFE = 0.4;
    private static int MAX_EXCESS_SCORES = 5;

    private final UnitInformationContainer unitInfos;

    public List<UnitPlacement> spawns;
    public int turnsToWait;
    public double coresRequired;

    public Set<Coords> usedCoords;
    public double damagePerTurn;

    public Attack(UnitInformationContainer unitInfos, int turnsToWait) {
        this.unitInfos = unitInfos;
        spawns = new ArrayList<>();
        this.turnsToWait = turnsToWait;
        coresRequired = 0;
        usedCoords = new TreeSet<>();
        damagePerTurn = 0;
    }

    public void add(UnitPlacement placement) {
        double coreCost = placement.type == UnitType.Upgrade ? unitInfos.getCores(UnitType.Support, true)
                : unitInfos.getCores(placement.type, false);
        coresRequired += coreCost;
        spawns.add(placement);
    }

    private double damageValue(double coresDestroyed, double scores, float p2Health, int turnsWaited) {
        if (scores >= p2Health)
            return 20 - turnsWaited + TURNS_PER_EXCESS_LIFE * Math.min(MAX_EXCESS_SCORES, scores - p2Health);
        else
            return (coresDestroyed + CORES_PER_LIFE * scores) / turnsWaited;
    }

    public void test(GameState move, List<StructBoard> setups, List<Coords> interceptorCoords, int sinceLastAttack) {
        double totalValue = 0;
        double weightSum = 0;
        for (StructBoard board : setups) {
            ActionSimulator sim = new ActionSimulator(unitInfos, board, move);
            for (UnitPlacement placement : spawns)
                sim.spawnUnits(placement.coords, placement.type, placement.quantity);
            for (Coords coords : interceptorCoords)
                sim.spawnUnits(coords, UnitType.Interceptor, 1);
            sim.run();

            double damage = damageValue(sim.p2CoresLost, sim.p2LivesLost, move.data.p2Stats.integrity,
                    turnsToWait + sinceLastAttack);
            totalValue += 0.1 * damage + 1;
            weightSum += 0.1 + 1 / damage;
            for (Coords coords : sim.unitPath)
                if (coords.y < MapBounds.BOARD_SIZE / 2)
                    usedCoords.add(coords);
        }
        damagePerTurn = totalValue / weightSum;
    }

    public void send(GameState move) {
        System.err.println("Attack: " + spawns);
        // System.err.println("Coords: " + usedCoords);
        // System.err.println("damage: " + damagePerTurn);
        // ActionSimulator sim = new ActionSimulator(unitInfos, new StructBoard(move,
        // unitInfos, false), move);
        // for (UnitPlacement placement : spawns)
        // sim.spawnUnits(placement.coords, placement.type, placement.quantity);
        // sim.run();
        // System.err.println("cores: " + sim.p2CoresLost);
        // System.err.println("lives: " + sim.p2LivesLost);
        for (UnitPlacement placement : spawns) {
            for (int n = 0; n < placement.quantity; n++) {
                if (placement.type == UnitType.Upgrade) {
                    move.attemptUpgrade(placement.coords);
                } else {
                    boolean success = move.attemptSpawn(placement.coords, placement.type);
                    Unit struct = move.getWallAt(placement.coords);
                    if (success && struct != null && struct.type == UnitType.Wall)
                        move.attemptRemoveStructure(placement.coords);
                }
            }
        }
    }
}