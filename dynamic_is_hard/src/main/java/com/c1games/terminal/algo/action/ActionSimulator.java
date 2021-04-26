package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.PlayerId;
import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.units.UnitType;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.SortedMap;
import java.util.Map;
import java.util.AbstractMap;
import java.util.TreeMap;
import java.util.Iterator;

public class ActionSimulator {

    private final StructBoard board;
    private final List<Coords> turretCoords;
    private final List<Coords> supportCoords;
    private final List<MobileUnits> mobileUnits;

    private final UnitInformationContainer unitInfos;

    public int p1LivesLost = 0;
    public int p2LivesLost = 0;
    public double p1CoresLost = 0;
    public double p2CoresLost = 0;
    public Deque<Coords> unitPath = new LinkedList<>();
    public List<Coords> p1LostStructCoords = new ArrayList<>();

    // // copies structures from another simulator instance
    // // for given coords, keeps first in list and ignores others
    // public ActionSimulator(Config config, List<GameUnit> unitsToAdd) {
    // this.config = config;
    // upgradedUnitInfo = new ArrayList<>();
    // for (Config.UnitInformation unitInfo : config.unitInformation) {
    // Config.UnitInformation copy = new Config.UnitInformation(unitInfo);
    // if (copy.upgrade.isPresent())
    // copy.upgrade();
    // upgradedUnitInfo.add(copy);
    // }
    // board = new StructBoard();

    // supportCoords = new LinkedList<>();
    // turretCoords = new LinkedList<>();
    // mobileUnits = new LinkedList<>();
    // for (GameUnit unit : unitsToAdd) {
    // if (unit.isStructure()) {
    // Structure oldStruct = (Structure) unit;
    // Structure struct = new Structure(oldStruct.type, oldStruct.health,
    // oldStruct.coords);
    // struct.upgraded = oldStruct.upgraded;
    // map.setLocation(oldStruct.coords, struct);
    // structures.add(struct);
    // } else if (!((MobileUnits) unit).healths.isEmpty()) {
    // Locationable loc = map.getLocation(unit.getCoords());
    // MobileUnitsList list;
    // if (loc == null)
    // list = new MobileUnitsList();
    // else if (loc.hasStructure())
    // continue;
    // else
    // list = (MobileUnitsList) loc;
    // if (loc == null)
    // map.setLocation(unit.getCoords(), list);
    // list.addUnits((MobileUnits) unit);
    // mobileUnits.add((MobileUnits) unit);
    // }
    // }

    // for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
    // for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
    // Coords coords = new Coords(x, y);
    // if (MapBounds.inArena(coords) && map.getLocation(coords) == null)
    // map.setLocation(coords, new MobileUnitsList());
    // }
    // }

    // }

    // GameState used to reference id of turrets for attack order
    public ActionSimulator(UnitInformationContainer unitInfos, StructBoard startBoard, GameState move) {
        this.unitInfos = unitInfos;
        board = new StructBoard(startBoard);

        SortedMap<String, Coords> turretById = new TreeMap<>(Collections.reverseOrder());
        turretCoords = new LinkedList<>();
        supportCoords = new LinkedList<>();
        mobileUnits = new LinkedList<>();
        // Find existing turrets and supports
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Coords coords = new Coords(x, y);
                Structure struct = board.getLocation(coords);
                if (struct != null) {
                    Unit unit = move.getWallAt(coords);
                    if (struct.type == UnitType.Turret) {
                        if (unit != null)
                            turretById.put(unit.id, coords);
                        else
                            turretCoords.add(coords);
                    } else if (struct.type == UnitType.Support)
                        supportCoords.add(coords);
                }
            }
        }

        for (Coords coords : turretById.values()) {
            turretCoords.add(0, coords);
        }
        // // Include newly spawned structures
        // for (SpawnCommand command : move.buildStack) {
        // spawnUnit(new Coords(command.x, command.y), command.type);
        // }
    }

    public boolean spawnUnit(Coords coords, UnitType type) {
        return spawnUnits(coords, type, 1);
    }

    // Immediately place or remove units ignoring resource cost
    public boolean spawnUnits(Coords coords, UnitType type, int quantity) {
        if (quantity <= 0)
            return false;

        Structure struct = board.getLocation(coords);
        Config.UnitInformation unitInfo = unitInfos.getInfo(type, false);

        if (struct == null && unitInfo.unitCategory.orElse(-1) == GameState.WalkerUnitCategory) {
            int targetEdge;
            if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_BOTTOM_LEFT][coords.x][coords.y])
                targetEdge = MapBounds.EDGE_TOP_RIGHT;
            else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_BOTTOM_RIGHT][coords.x][coords.y])
                targetEdge = MapBounds.EDGE_TOP_LEFT;
            else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_TOP_LEFT][coords.x][coords.y])
                targetEdge = MapBounds.EDGE_BOTTOM_RIGHT;
            else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_TOP_RIGHT][coords.x][coords.y])
                targetEdge = MapBounds.EDGE_BOTTOM_LEFT;
            else
                return false;

            MobileUnits units = new MobileUnits(type, quantity, unitInfo.startHealth.getAsDouble(), coords, targetEdge);
            mobileUnits.add(units);
            return true;
        } else if (type == UnitType.Remove) {
            board.setLocation(coords, null);
            return true;
        } else if (board.build(unitInfos, type, coords)) {
            if (type == UnitType.Turret)
                turretCoords.add(coords);
            else if (type == UnitType.Support)
                supportCoords.add(coords);
            return true;
        } else {
            return false;
        }
    }

    // Simulates action phase until completion
    public void run() {

        pathfind();
        // if (!mobileUnits.isEmpty() && mobileUnits.get(0).type == UnitType.Demolisher
        // && mobileUnits.get(0).getPlayer() == PlayerId.Player2)
        // System.err.println("init path: " + mobileUnits.get(0).path);
        for (int frame = 1; !mobileUnits.isEmpty(); frame++) {
            List<Coords> toRemoveCoords = new ArrayList<>();

            // Move mobile units
            unitPath.add(mobileUnits.get(0).coords);
            for (Iterator<MobileUnits> itr = mobileUnits.iterator(); itr.hasNext();) {
                MobileUnits units = itr.next();
                double speed = unitInfos.getInfo(units.type, false).speed.getAsDouble();
                if ((int) (frame * speed) - (int) ((frame - 1) * speed) > 0) {
                    if (units.path.isEmpty()) {
                        if (MapBounds.IS_ON_EDGE[units.targetEdge][units.coords.x][units.coords.y]) {
                            if (units.getPlayer() == PlayerId.Player1)
                                p2LivesLost += units.healths.size();
                            else if (units.getPlayer() == PlayerId.Player2)
                                p1LivesLost += units.healths.size();
                        } else {
                            toRemoveCoords.addAll(selfDestruct(units));
                        }
                        itr.remove();
                    } else {
                        Coords next = units.path.removeFirst();
                        units.lastDirection = (units.coords.x == next.x) ? Pathfinder.Direction.VERTICAL
                                : Pathfinder.Direction.HORIZONTAL;
                        units.coords = next;
                    }
                }
            }

            // Add shielding
            for (Iterator<Coords> itr = supportCoords.iterator(); itr.hasNext();) {
                Coords coords = itr.next();
                Structure support = board.getLocation(coords);
                if (support == null || support.type != UnitType.Support) {
                    itr.remove();
                } else {
                    Config.UnitInformation unitInfo = unitInfos.getInfo(UnitType.Support, support.upgraded);
                    int yValue = Structure.getPlayer(coords.y) == PlayerId.Player1 ? coords.y
                            : MapBounds.BOARD_SIZE - 1 - coords.y;
                    double shieldAmount = unitInfo.shieldPerUnit.orElse(0)
                            + unitInfo.shieldBonusPerY.orElse(0) * yValue;
                    for (MobileUnits units : mobileUnits) {
                        if (coords.distance(units.coords) <= unitInfo.shieldRange.orElse(0)
                                && units.shieldsFrom.add(coords)) {
                            units.takeSplashDamage(-shieldAmount);
                        }
                    }
                }
            }

            // Perform attacks
            for (Iterator<Coords> itr = turretCoords.iterator(); itr.hasNext();) {
                Coords coords = itr.next();
                Structure turret = board.getLocation(coords);
                if (turret == null || turret.type != UnitType.Turret) {
                    itr.remove();
                } else {
                    Config.UnitInformation unitInfo = unitInfos.getInfo(UnitType.Turret, turret.upgraded);
                    MobileUnits target = getMobileTarget(coords, unitInfo.attackRange.getAsDouble(),
                            Structure.getPlayer(coords.y));
                    if (target != null) {
                        target.takeDamage(unitInfo.attackDamageWalker.orElse(0));
                    }
                }
            }
            for (MobileUnits units : mobileUnits) {
                Config.UnitInformation unitInfo = unitInfos.getInfo(units.type);
                // major issue: interceptor has attackDamageTower (bug?)
                // boolean canAttackStructures = false;
                // if (unitInfo.attackDamageTower.isPresent())
                // canAttackStructures = true;
                // else if (!unitInfo.attackDamageWalker.isPresent())
                // continue;
                boolean canAttackStructures = units.type != UnitType.Interceptor;
                for (int i = 0; i < units.healths.size(); i++) {
                    double damage = unitInfo.attackDamageWalker.getAsDouble();
                    GameUnit target;
                    if (canAttackStructures) {
                        Map.Entry<GameUnit, Coords> pair = getAnyTarget(units.coords,
                                unitInfo.attackRange.getAsDouble(), units.getPlayer());
                        target = pair.getKey();
                        Coords coords = pair.getValue();

                        if (target != null && target.isStructure()) {
                            Structure struct = (Structure) target;

                            Config.UnitInformation targetInfo = unitInfos.getInfo(struct.type, struct.upgraded);
                            double coreValue = Math.min(damage, target.getTargetHealth())
                                    / targetInfo.startHealth.getAsDouble() * targetInfo.cost1.getAsDouble();
                            if (units.getPlayer() == PlayerId.Player1)
                                p2CoresLost += coreValue;
                            else if (units.getPlayer() == PlayerId.Player2)
                                p1CoresLost += coreValue;

                            if (struct.getTargetHealth() <= damage)
                                toRemoveCoords.add(coords);
                        }
                    } else {
                        target = getMobileTarget(units.coords, unitInfo.attackRange.getAsDouble(), units.getPlayer());
                    }
                    if (target != null) {
                        target.takeDamage(damage);
                    } else {
                        // if no target, other units on same tile also have no target
                        break;
                    }
                }
            }

            // Check death
            for (Coords coords : toRemoveCoords) {
                board.setLocation(coords, null);
                if (Structure.getPlayer(coords.y) == PlayerId.Player1)
                    p1LostStructCoords.add(coords);
            }
            for (Iterator<MobileUnits> itr = mobileUnits.iterator(); itr.hasNext();) {
                MobileUnits units = itr.next();
                units.removeDead();
                if (units.healths.isEmpty()) {
                    itr.remove();
                }
            }

            // If a structure was destroyed, paths may change
            if (!toRemoveCoords.isEmpty())
                pathfind();
        }
    }

    // Recompute and set pathing for mobile units
    private void pathfind() {
        Pathfinder pather = new Pathfinder(board);
        for (MobileUnits units : mobileUnits) {
            units.path = pather.getPath(units.coords, units.targetEdge, units.lastDirection);
        }
    }

    private List<Coords> selfDestruct(MobileUnits units) {
        // config.mechanics not initializated ?
        // float range = config.mechanics.selfDestructRadius;
        double radius = 1.5;
        double damage = unitInfos.getInfo(units.type, false).startHealth.getAsDouble() * units.healths.size();

        List<Coords> deadStructCoords = new ArrayList<>();
        // check board area for enemy structures
        int window = (int) radius;
        for (int x = units.coords.x - window; x <= units.coords.x + window; x++) {
            for (int y = units.coords.y - window; y <= units.coords.y + window; y++) {
                Coords coords = new Coords(x, y);
                if (units.coords.distance(coords) <= radius && units.getPlayer() != Structure.getPlayer(y)) {
                    Structure struct = board.getLocation(coords);
                    if (struct != null) {
                        struct.takeDamage(damage);
                        if (struct.getTargetHealth() <= 0)
                            deadStructCoords.add(coords);
                    }
                }
            }
        }
        // check for enemy mobile units in range
        for (MobileUnits others : mobileUnits) {
            if (units.coords.distance(others.coords) <= radius && units.getPlayer() != others.getPlayer()) {
                others.takeSplashDamage(damage);
            }
        }
        // kill self-destructing units
        for (int i = 0; i < units.healths.size(); i++)
            units.healths.set(i, 0.0);

        return deadStructCoords;
    }

    // Chooses target for a unit based on following priorities:
    // (1) Mobile units over Structures
    // (2) nearest
    // (3) lowest remaining health
    // (4) furthest into/towards your side of the arena
    // Nullable for no valid valid targets
    public Map.Entry<GameUnit, Coords> getAnyTarget(Coords coords, double range, PlayerId player) {

        // first check mobile targets
        MobileUnits targetUnit = getMobileTarget(coords, range, player);
        if (targetUnit != null)
            return new AbstractMap.SimpleImmutableEntry<>(targetUnit, targetUnit.coords);

        // if none, then check structure targets
        Structure targetStruct = null;
        double targetDistTo = 2 * range;
        double targetHealth = 1000;
        Coords targetCoords = null;

        int window = (int) range;
        // Searchs bottom up for player 1 and top down for player 2
        int[] yValues = new int[window * 2 + 1];
        for (int i = 0; i < yValues.length; i++)
            yValues[i] = (player == PlayerId.Player1) ? (coords.y - window + i) : (coords.y + window - i);
        for (int y : yValues) {
            for (int x = coords.x - window; x <= coords.x + window; x++) {
                Coords here = new Coords(x, y);
                Structure struct = board.getLocation(here);

                if (struct != null && Structure.getPlayer(y) != player) {
                    double dist = coords.distance(here);
                    double health = struct.getTargetHealth();
                    if (dist <= range && health > 0
                            && (dist < targetDistTo || (targetDistTo == dist && health < targetHealth))) {
                        targetStruct = struct;
                        targetDistTo = dist;
                        targetHealth = health;
                        targetCoords = here;
                    }
                }

            }
        }
        return new AbstractMap.SimpleImmutableEntry<>(targetStruct, targetCoords);
    }

    public MobileUnits getMobileTarget(Coords coords, double range, PlayerId player) {
        MobileUnits targetUnit = null;
        double targetDistTo = 2 * range;
        double targetHealth = 1000;

        for (MobileUnits units : mobileUnits) {
            double health = units.getTargetHealth();
            if (units.getPlayer() != player && health > 0) {
                double dist = coords.distance(units.coords);
                if (dist <= range && (targetUnit == null || targetDistTo > dist
                        || (targetDistTo == dist && targetHealth > health))) {
                    targetUnit = units;
                    targetDistTo = dist;
                    targetHealth = health;
                }
            }
        }
        return targetUnit;
    }

    // private boolean fartherFromBottom(Coords coords1, Coords coords2, PlayerId
    // player) {
    // return (player == PlayerId.Player1) ? coords1.y > coords2.y : coords1.y <
    // coords2.y;
    // }
}