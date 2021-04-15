package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Coords;
import com.c1games.terminal.algo.map.MapBounds;

public class GameMap {

    private Locationable[][] map;

    public GameMap() {
        map = new Locationable[MapBounds.BOARD_SIZE][MapBounds.BOARD_SIZE];
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

}