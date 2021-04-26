package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.units.UnitType;
import com.c1games.terminal.algo.map.SpawnCommand;

import java.util.List;

public class StructBoard {

    private Structure[][] board;

    public StructBoard() {
        board = new Structure[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
    }

    public StructBoard(GameState move, UnitInformationContainer unitInfos, boolean removeRemoving) {
        board = new Structure[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Coords coords = new Coords(x, y);
                if (MapBounds.inArena(coords)) {
                    List<Unit> list = move.allUnits[x][y];
                    if (!list.isEmpty()) {
                        Unit unit = list.get(0);
                        if (move.isStructure(list.get(0).type) && !(removeRemoving && unit.removing)) {
                            Structure struct = new Structure(unit.type, unit.health);
                            struct.upgraded = unit.upgraded;
                            setLocation(coords, struct);
                        }
                    }
                }
            }
        }
        // Include newly spawned structures
        for (SpawnCommand command : move.buildStack) {
            if (removeRemoving && command.type == UnitType.Remove) {
                setLocation(new Coords(command.x, command.y), null);
            } else {
                build(unitInfos, command.type, new Coords(command.x, command.y));
            }
        }
    }

    public StructBoard(StructBoard toCopy) {
        board = new Structure[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Structure struct = toCopy.getLocation(x, y);
                if (struct != null) {
                    board[x][y] = new Structure(struct.type, struct.getTargetHealth());
                    board[x][y].upgraded = struct.upgraded;
                }
            }
        }
    }
    // else {
    // MobileUnitsList location = new MobileUnitsList();
    // int targetEdge = getTargetEdge(coords);
    // int[] numbers = new int[3];
    // for (Unit unit : list)
    // numbers[unit.type.ordinal() - UnitType.Scout.ordinal()]++;
    // UnitType[] unitTypes = new UnitType[] { UnitType.Scout, UnitType.Demolisher,
    // UnitType.Interceptor };
    // for (int i = 0; i < 3; i++)
    // if (numbers[i] > 0) {
    // double health =
    // move.config.unitInformation.get(unitTypes[i].ordinal()).startHealth
    // .getAsDouble();
    // MobileUnits units = new MobileUnits(unitTypes[i], numbers[i], health, coords,
    // targetEdge);
    // location.addUnits(units);
    // }
    // setLocation(coords, location);
    // }

    public boolean removeDead() {
        boolean removedSomething = false;
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Structure struct = board[x][y];
                if (struct != null && struct.getTargetHealth() <= 0) {
                    board[x][y] = null;
                    removedSomething = true;
                }
            }
        }
        return removedSomething;
    }

    public boolean build(UnitInformationContainer unitInfos, UnitType type, Coords coords) {
        Structure struct = getLocation(coords);
        if (type == UnitType.Upgrade && struct != null && !struct.upgraded) {
            struct.upgraded = true;
            double initialHealth = unitInfos.getInfo(struct.type, false).startHealth.getAsDouble();
            double upgradedHealth = unitInfos.getInfo(struct.type, true).startHealth.getAsDouble();
            struct.takeDamage(initialHealth - upgradedHealth);
            return true;
        } else if (unitInfos.getInfo(type).unitCategory.orElse(-1) == GameState.TowerUnitCategory && struct == null) {
            Structure newStruct = new Structure(type, unitInfos.getInfo(type).startHealth.getAsDouble());
            setLocation(coords, newStruct);
            return true;
        } else {
            return false;
        }
    }

    public Structure getLocation(int x, int y) {
        return getLocation(new Coords(x, y));
    }

    public Structure getLocation(Coords coords) {
        if (!MapBounds.inArena(coords))
            return null;
        else
            return board[coords.x][coords.y];
    }

    public void setLocation(Coords coords, Structure put) {
        if (MapBounds.inArena(coords))
            board[coords.x][coords.y] = put;
    }

    private int getTargetEdge(Coords coords) {
        if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_BOTTOM_LEFT][coords.x][coords.y])
            return MapBounds.EDGE_TOP_RIGHT;
        else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_BOTTOM_RIGHT][coords.x][coords.y])
            return MapBounds.EDGE_TOP_LEFT;
        else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_TOP_LEFT][coords.x][coords.y])
            return MapBounds.EDGE_BOTTOM_RIGHT;
        else if (MapBounds.IS_ON_EDGE[MapBounds.EDGE_TOP_RIGHT][coords.x][coords.y])
            return MapBounds.EDGE_BOTTOM_LEFT;
        else
            return -1;
    }

}