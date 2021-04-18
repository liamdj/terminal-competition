package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.map.GameState;
import com.c1games.terminal.algo.map.MapBounds;
import com.c1games.terminal.algo.map.Unit;
import com.c1games.terminal.algo.units.UnitType;

import java.util.List;

public class GameMap {

    private Locationable[][] map;

    public GameMap() {
        map = new Locationable[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
    }

    public GameMap(GameState move, boolean removeRemoving) {
        map = new Locationable[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
        for (int x = 0; x < MapBounds.BOARD_SIZE; x++) {
            for (int y = 0; y < MapBounds.BOARD_SIZE; y++) {
                Coords coords = new Coords(x, y);
                if (!MapBounds.inArena(coords))
                    continue;
                List<Unit> list = move.allUnits[x][y];
                if (list.isEmpty()) {
                    setLocation(coords, new MobileUnitsList());
                } else if (move.isStructure(list.get(0).type)) {
                    Unit unit = list.get(0);
                    if (removeRemoving && unit.removing)
                        continue;
                    Structure struct = new Structure(unit.type, unit.health, coords);
                    struct.upgraded = unit.upgraded;
                    setLocation(coords, struct);
                } else {
                    MobileUnitsList location = new MobileUnitsList();
                    int targetEdge = getTargetEdge(coords);
                    int[] numbers = new int[3];
                    for (Unit unit : list)
                        numbers[unit.type.ordinal() - UnitType.Scout.ordinal()]++;
                    UnitType[] unitTypes = new UnitType[] { UnitType.Scout, UnitType.Demolisher, UnitType.Interceptor };
                    for (int i = 0; i < 3; i++)
                        if (numbers[i] > 0) {
                            double health = move.config.unitInformation.get(unitTypes[i].ordinal()).startHealth
                                    .getAsDouble();
                            MobileUnits scouts = new MobileUnits(unitTypes[i], numbers[i], health, coords, targetEdge);
                            location.addUnits(scouts);
                        }
                    setLocation(coords, location);
                }
            }
        }

    }

    public Locationable getLocation(int x, int y) {
        return getLocation(new Coords(x, y));
    }

    public Locationable getLocation(Coords coords) {
        if (!MapBounds.inArena(coords))
            return null;
        else
            return map[coords.x][coords.y];
    }

    public void setLocation(Coords coords, Locationable put) {
        if (MapBounds.inArena(coords))
            map[coords.x][coords.y] = put;
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