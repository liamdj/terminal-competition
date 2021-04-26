package com.c1games.terminal.algo.action;

import com.c1games.terminal.algo.Config;
import com.c1games.terminal.algo.units.UnitType;

import java.util.List;
import java.util.ArrayList;

// this class should be unnecessary, but I don't understand GSON for Config.java
public class UnitInformationContainer {

    private final List<Config.UnitInformation> unitInfos;
    private final List<Config.UnitInformation> upgradedUnitInfos;

    public UnitInformationContainer(Config config) {
        unitInfos = config.unitInformation;
        upgradedUnitInfos = new ArrayList<>();
        for (Config.UnitInformation info : unitInfos) {
            Config.UnitInformation copy = new Config.UnitInformation(info);
            if (copy.upgrade.isPresent())
                copy.upgrade();
            upgradedUnitInfos.add(copy);
        }
    }

    public Config.UnitInformation getInfo(UnitType type) {
        return unitInfos.get(type.ordinal());
    }

    public Config.UnitInformation getInfo(UnitType type, boolean upgraded) {
        if (upgraded) {
            return upgradedUnitInfos.get(type.ordinal());
        } else {
            return unitInfos.get(type.ordinal());
        }
    }

    public double getCores(UnitType type, boolean upgrade) {
        if (upgrade)
            return getInfo(type, true).cost1.orElse(0) - getInfo(type, false).cost1.orElse(0);
        else
            return getInfo(type, false).cost1.orElse(0);
    }

}