package com.c1games.terminal.algo.action;

import java.util.List;
import java.util.ArrayList;

public class MobileUnitsList implements Locationable {

    public final List<MobileUnits> unitsList;

    public MobileUnitsList() {
        unitsList = new ArrayList<>();
    }

    public boolean hasStructure() {
        return false;
    }

    public boolean hasMobileUnit() {
        return !unitsList.isEmpty();
    }

    public List<GameUnit> getUnits() {
        return (List<GameUnit>) (List<? extends GameUnit>) unitsList;
    }

    public void addUnits(MobileUnits units) {
        unitsList.add(units);
    }

    public void removeUnits(MobileUnits units) {
        unitsList.remove(units);
    }
}
