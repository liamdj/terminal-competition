package com.c1games.terminal.algo.action;

import java.util.List;

// Able to fill a space on the game map
public interface Locationable {

    boolean hasStructure();

    boolean hasMobileUnit();

    List<GameUnit> getUnits();

}
