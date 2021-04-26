package com.c1games.terminal.algo.action;

public interface GameUnit {

    double getTargetHealth();

    void takeDamage(double damage);

    void takeSplashDamage(double damage);

    boolean isStructure();
}
