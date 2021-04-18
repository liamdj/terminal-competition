package com.c1games.terminal.strategy;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;
import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.GameIO;
import com.c1games.terminal.algo.FrameData;
import com.c1games.terminal.algo.io.GameLoop;
import com.c1games.terminal.algo.io.GameLoopDriver;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.units.UnitType;

import com.c1games.terminal.algo.action.ActionSimulator;
import com.c1games.terminal.algo.action.GameUnit;
import com.c1games.terminal.algo.action.MobileUnits;
import com.c1games.terminal.algo.action.Structure;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * 
 */
public class StrategyMain implements GameLoop {
    public static void main(String[] args) {
        new GameLoopDriver(new StrategyMain()).run();
    }

    // fraction of cores used for offensive when attacking this turn
    private static final double OFFENSIVE_CORES_RATIO = 0.3;

    private static final List<UnitPlacement> basicStructures = new ArrayList<>();
    private static final List<Coords> pathingCoords = new ArrayList<>();
    private static final Deque<Coords> supportCoords = new LinkedList<>();

    private List<GameUnit> p2PrevUnits = new ArrayList<>();
    private List<GameUnit> p2PrevPrevUnits = new ArrayList<>();

    private int turnLastAttacked = -1;

    @Override
    public void initialize(GameIO io, Config config) {
        // read structure locations from files
        GameIO.debug().println("Beginning Algo");

        basicStructures.add(new UnitPlacement(new Coords(2, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(25, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(6, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(21, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(11, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(16, 12), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(0, 13), UnitType.Wall));
        basicStructures.add(new UnitPlacement(new Coords(27, 13), UnitType.Turret));
        basicStructures.add(new UnitPlacement(new Coords(1, 12), UnitType.Wall));
        basicStructures.add(new UnitPlacement(new Coords(26, 12), UnitType.Turret));

        pathingCoords.add(new Coords(3, 12));
        pathingCoords.add(new Coords(10, 12));
        pathingCoords.add(new Coords(17, 12));
        pathingCoords.add(new Coords(24, 12));

        supportCoords.add(new Coords(13, 11));
        supportCoords.add(new Coords(14, 11));
        supportCoords.add(new Coords(12, 11));
        supportCoords.add(new Coords(15, 11));
        supportCoords.add(new Coords(13, 12));
        supportCoords.add(new Coords(14, 12));
        supportCoords.add(new Coords(12, 12));
        supportCoords.add(new Coords(15, 12));
        supportCoords.add(new Coords(13, 13));
        supportCoords.add(new Coords(14, 13));
        supportCoords.add(new Coords(12, 13));
        supportCoords.add(new Coords(15, 13));
        supportCoords.add(new Coords(13, 14));
        supportCoords.add(new Coords(14, 14));
        supportCoords.add(new Coords(12, 14));
        supportCoords.add(new Coords(15, 14));
    }

    /**
     * Make a move in the game.
     */
    @Override
    public void onTurn(GameIO io, GameState move) {
        GameIO.debug().println("Performing turn " + move.data.turnInfo.turnNumber);

        // ensures basic defenses are completed first
        buildBasicStructures(move);

        updateDefenses(move, move.data.p1Stats.cores * OFFENSIVE_CORES_RATIO);

        Attack attack = null;
        if (move.data.turnInfo.turnNumber != 0)
            attack = testAttacks(move, attacksToConsider(move, 6));
        if (attack != null && attack.turnsToWait == 0) {
            sendAttack(move, attack);
        }
    }

    /**
     * Save process action frames. Careful there are many action frames per turn!
     */
    @Override
    public void onActionFrame(GameIO io, GameState move) {

        // Save enemy base set up to test attacks
        if (move.data.turnInfo.actionPhaseFrameNumber == 0) {
            p2PrevPrevUnits = p2PrevUnits;
            p2PrevUnits = unitsList(move, PlayerId.Player2, false);
        }

        // Add locations where a support was destroyed
        for (FrameData.Events.DeathEvent death : move.data.events.death) {
            if (death.unitOwner == PlayerId.Player1 && death.destroyedUnitType == UnitType.Support) {
                supportCoords.addFirst(death.coords);
            }
        }
    }

    private List<GameUnit> unitsList(GameState move, PlayerId player, boolean immediate) {
        ActionSimulator sim = new ActionSimulator(move, immediate);
        List<GameUnit> list = new LinkedList<>();
        for (Iterator<Structure> itr = sim.structures.iterator(); itr.hasNext();) {
            GameUnit unit = itr.next();
            if (player == PlayerId.Error || unit.getPlayer() == player)
                list.add(unit);
        }
        for (Iterator<MobileUnits> itr = sim.mobileUnits.iterator(); itr.hasNext();) {
            GameUnit unit = itr.next();
            if (player == PlayerId.Error || unit.getPlayer() == player)
                list.add(unit);
        }
        return list;
    }

    private void buildBasicStructures(GameState move) {
        for (UnitPlacement placement : basicStructures) {
            move.attemptSpawn(placement.coords, placement.type);
        }
    }

    private void updateDefenses(GameState move, double coreBudget) {
        Defense defense = new Defense(move);
        for (Attack attack : opponentAttacks(move)) {
            defense.checkAttack(attack);
        }
        defense.spendCores(coreBudget);
    }

    // from each edge location using all bits on one unit type
    private List<Attack> opponentAttacks(GameState move) {
        double nextBits = nextBits((double) move.data.p2Stats.bits, move.data.turnInfo.turnNumber);
        double scoutCost = move.config.unitInformation.get(UnitType.Scout.ordinal()).cost2.getAsDouble();
        double demolisherCost = move.config.unitInformation.get(UnitType.Demolisher.ordinal()).cost2.getAsDouble();

        List<Attack> ret = new ArrayList<>();
        List<Coords> edgeCoords = new ArrayList<>();
        edgeCoords.addAll(Arrays.asList(MapBounds.EDGE_LISTS[MapBounds.EDGE_TOP_LEFT]));
        edgeCoords.addAll(Arrays.asList(MapBounds.EDGE_LISTS[MapBounds.EDGE_TOP_RIGHT]));
        for (Coords coords : edgeCoords) {
            Attack scouts = new Attack(1);
            scouts.add(new UnitPlacement(coords, UnitType.Scout, (int) (nextBits / scoutCost)));
            ret.add(scouts);
            Attack demo = new Attack(1);
            demo.add(new UnitPlacement(coords, UnitType.Demolisher, (int) (nextBits / demolisherCost)));
            ret.add(demo);
        }
        return ret;
    }

    private double nextBits(double currentBits, int turnNumber) {
        return 0.75 * currentBits + 1 + (int) ((turnNumber + 1) / 5);
    }

    private void sendAttack(GameState move, Attack attack) {

        for (UnitPlacement placement : attack.spawns) {
            for (int n = 0; n < placement.quantity; n++) {
                boolean success = move.attemptSpawn(placement.coords, placement.type);
                Unit struct = move.getWallAt(placement.coords);
                if (success && struct != null) {
                    if (struct.type == UnitType.Wall)
                        move.attemptRemoveStructure(placement.coords);
                    else if (placement.type == UnitType.Upgrade)
                        supportCoords.removeFirst();
                }
            }
        }

        turnLastAttacked = move.data.turnInfo.turnNumber;
    }

    private List<Attack> attacksToConsider(GameState move, int lookAhead) {
        // Find all scout and all demolisher attacks with current setup for this turn
        // and next lookAhead
        int turnNumber = move.data.turnInfo.turnNumber;

        Map<Integer, Integer> scoutsToTurns = new TreeMap<>();
        double scoutCost = move.config.unitInformation.get(UnitType.Scout.ordinal()).cost2.getAsDouble();
        Map<Integer, Integer> demolishersToTurns = new TreeMap<>();
        double demolisherCost = move.config.unitInformation.get(UnitType.Demolisher.ordinal()).cost2.getAsDouble();

        double bits = (double) move.data.p1Stats.bits;
        for (int t = 0; t < lookAhead; t++) {
            scoutsToTurns.putIfAbsent((int) (bits / scoutCost), t);
            demolishersToTurns.putIfAbsent((int) (bits / demolisherCost), t);
            bits = nextBits(bits, turnNumber + t);
        }

        double wallCost = move.config.unitInformation.get(UnitType.Wall.ordinal()).cost1.getAsDouble();
        double supportCost = 2 * move.config.unitInformation.get(UnitType.Support.ordinal()).cost1.getAsDouble();

        List<Attack> attacks = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : demolishersToTurns.entrySet()) {

            double cores = (double) move.data.p1Stats.cores + 4 * OFFENSIVE_CORES_RATIO * entry.getValue();

            int leftPathings = 0;
            while (leftPathings < pathingCoords.size()) {
                Unit unit = move.getWallAt(pathingCoords.get(leftPathings));
                if (!(unit == null || (unit.removing && entry.getValue() == 1) || entry.getValue() >= 2)) {
                    break;
                }
                leftPathings++;
            }

            // try each attack with or without additional pathings
            for (Coords pos : List.of(new Coords(3, 10), new Coords(9, 4), new Coords(13, 0))) {
                double coresLeft = cores;
                for (int pathings = 0; pathings <= leftPathings; pathings++) {
                    Attack attack = new Attack(entry.getValue());
                    attack.add(new UnitPlacement(pos, UnitType.Demolisher, entry.getKey()));
                    for (int i = 0; i < pathings && coresLeft >= wallCost; i++) {
                        attack.add(new UnitPlacement(pathingCoords.get(i), UnitType.Wall));
                        coresLeft -= wallCost;
                    }
                    for (Iterator<Coords> itr = supportCoords.iterator(); coresLeft >= supportCost && itr.hasNext();) {
                        Coords coords = itr.next();
                        attack.add(new UnitPlacement(coords, UnitType.Support));
                        attack.add(new UnitPlacement(coords, UnitType.Upgrade));
                        coresLeft -= supportCost;
                    }
                    attacks.add(attack);
                }
            }

            int rightPathings = 0;
            while (rightPathings < pathingCoords.size()) {
                Unit unit = move.getWallAt(pathingCoords.get(pathingCoords.size() - 1 - rightPathings));
                if (!(unit == null || (unit.removing && entry.getValue() == 1) || entry.getValue() >= 2)) {
                    break;
                }
                rightPathings++;
            }

            for (Coords pos : List.of(new Coords(24, 10), new Coords(18, 4), new Coords(14, 0))) {
                double coresLeft = cores;
                for (int pathings = 0; pathings <= rightPathings; pathings++) {
                    Attack attack = new Attack(entry.getValue());
                    attack.add(new UnitPlacement(pos, UnitType.Demolisher, entry.getKey()));
                    for (int i = 0; i < pathings && coresLeft >= wallCost; i++) {
                        attack.add(new UnitPlacement(pathingCoords.get(pathingCoords.size() - 1 - i), UnitType.Wall));
                        coresLeft -= wallCost;
                    }
                    for (Iterator<Coords> itr = supportCoords.iterator(); coresLeft >= supportCost && itr.hasNext();) {
                        Coords coords = itr.next();
                        attack.add(new UnitPlacement(coords, UnitType.Support));
                        attack.add(new UnitPlacement(coords, UnitType.Upgrade));
                        coresLeft -= supportCost;
                    }
                    attacks.add(attack);
                }
            }

        }

        for (Map.Entry<Integer, Integer> entry : scoutsToTurns.entrySet()) {

            double cores = (double) move.data.p1Stats.cores + 4 * OFFENSIVE_CORES_RATIO * entry.getValue();

            int leftPathings = 0;
            while (leftPathings < pathingCoords.size()) {
                Unit unit = move.getWallAt(pathingCoords.get(leftPathings));
                if (!(unit == null || (unit.removing && entry.getValue() == 1) || entry.getValue() >= 2)) {
                    break;
                }
                leftPathings++;
            }

            // try each attack with or without additional pathings
            for (Coords pos : List.of(new Coords(3, 10), new Coords(9, 4), new Coords(13, 0))) {
                double coresLeft = cores;
                for (int pathings = 0; pathings <= leftPathings; pathings++) {
                    Attack attack = new Attack(entry.getValue());
                    attack.add(new UnitPlacement(pos, UnitType.Scout, entry.getKey()));
                    for (int i = 0; i < pathings && coresLeft >= wallCost; i++) {
                        attack.add(new UnitPlacement(pathingCoords.get(i), UnitType.Wall));
                        coresLeft -= wallCost;
                    }
                    for (Iterator<Coords> itr = supportCoords.iterator(); coresLeft >= supportCost && itr.hasNext();) {
                        Coords coords = itr.next();
                        attack.add(new UnitPlacement(coords, UnitType.Support));
                        attack.add(new UnitPlacement(coords, UnitType.Upgrade));
                        coresLeft -= supportCost;
                    }
                    attacks.add(attack);
                }
            }

            int rightPathings = 0;
            while (rightPathings < pathingCoords.size()) {
                Unit unit = move.getWallAt(pathingCoords.get(pathingCoords.size() - 1 - rightPathings));
                if (!(unit == null || (unit.removing && entry.getValue() == 1) || entry.getValue() >= 2)) {
                    break;
                }
                rightPathings++;
            }

            for (Coords pos : List.of(new Coords(24, 10), new Coords(18, 4), new Coords(14, 0))) {
                double coresLeft = cores;
                for (int pathings = 0; pathings <= rightPathings; pathings++) {
                    Attack attack = new Attack(entry.getValue());
                    attack.add(new UnitPlacement(pos, UnitType.Scout, entry.getKey()));
                    for (int i = 0; i < pathings && coresLeft >= wallCost; i++) {
                        attack.add(new UnitPlacement(pathingCoords.get(pathingCoords.size() - 1 - i), UnitType.Wall));
                        coresLeft -= wallCost;
                    }
                    for (Iterator<Coords> itr = supportCoords.iterator(); coresLeft >= supportCost && itr.hasNext();) {
                        Coords coords = itr.next();
                        attack.add(new UnitPlacement(coords, UnitType.Support));
                        attack.add(new UnitPlacement(coords, UnitType.Upgrade));
                        coresLeft -= supportCost;
                    }
                    attacks.add(attack);
                }
            }

        }

        return attacks;
    }

    private Attack testAttacks(GameState move, List<Attack> attacksToTry) {

        // create base set ups to test against from opponent's previous turns
        List<GameUnit> currentUnits = unitsList(move, PlayerId.Error, true);
        List<GameUnit> mergedUnits1 = new ArrayList<>();
        mergedUnits1.addAll(currentUnits);
        mergedUnits1.addAll(p2PrevPrevUnits);
        List<GameUnit> mergedUnits2 = new ArrayList<>();
        mergedUnits2.addAll(currentUnits);
        mergedUnits2.addAll(p2PrevUnits);

        int sinceLastAttack = move.data.turnInfo.turnNumber - turnLastAttacked;
        Attack bestAttack = null;
        double bestValue = 0;
        for (Attack attack : attacksToTry) {

            double minValue = Double.MAX_VALUE;
            for (List<GameUnit> units : List.of(currentUnits, mergedUnits1, mergedUnits2)) {
                // keep track of value versus worst set up
                minValue = Math.min(minValue,
                        attack.test(move.config, units, sinceLastAttack, (int) move.data.p2Stats.integrity));
            }
            if (minValue > bestValue) {
                bestAttack = attack;
                bestValue = minValue;
                if (attack.turnsToWait > 1)
                    break;
            }
        }

        return bestAttack;
    }
}
