package com.c1games.terminal.algo.action;

import java.util.List;
import java.util.ArrayList;

public class MobileUnitsList implements Locationable {

    final List<MobileUnits> unitsList;

    public MobileUnitsList() {
        unitsList = new ArrayList<MobileUnits>();
    }
    
    public boolean hasStructure() {
        return false;
    }

    public boolean hasMobileUnit() {
        return !unitsList.isEmpty();
    }

    public List<? extends GameUnit> getUnits() {
        return unitsList;
    }

    public void add(MobileUnits units) {
        unitsList.add(units);
    }

    public void remove(MobileUnits units) {
        unitsList.remove(units);
    }
}
