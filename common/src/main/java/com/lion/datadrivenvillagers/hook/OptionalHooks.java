package com.lion.datadrivenvillagers.hook;

/// Confirms an optional mixin fired. `require = 0` keeps a lost injection point silent otherwise.
/// A confirm method only runs if Mixin found the injection point and spliced the call in.
public final class OptionalHooks {

    private static volatile boolean farmerWorkBehaviour;
    private static volatile boolean gift;

    private OptionalHooks() {
    }

    public static void confirmFarmerWorkBehaviour() {
        farmerWorkBehaviour = true;
    }

    public static void confirmGift() {
        gift = true;
    }

    public static boolean isFarmerWorkBehaviourConfirmed() {
        return farmerWorkBehaviour;
    }

    public static boolean isGiftConfirmed() {
        return gift;
    }
}
