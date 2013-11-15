/**
 *
 * Brcm Dual Sim Utils
 *
 */

package com.android.mms.util;

import android.os.SystemProperties;
import android.util.Log;


public class BrcmDualSimUtils {

    /**
     * Name of dual sim property, query from System Property
     * 1: Dual SIM phone
     * 0: Single SIM phone
     */
    private static final String PROPERTY_DUAL_MODE_PHONE = "ro.dual.sim.phone";
    //private static final String PROPERTY_DUAL_MODE_PHONE = "rw.dual.sim.phone";
    //For easy testing
    private static int m_DualSimProp = -1;

    /**
     * Return boolean value that is current system support Dual Sim or not.
     */
    public static boolean isSupportDualSim()
    {
        if (m_DualSimProp == -1) {
            m_DualSimProp = SystemProperties.getInt(PROPERTY_DUAL_MODE_PHONE, 0);
            Log.d("SettingsUtils", "Dual Sim Property = " + m_DualSimProp);
        }
        return m_DualSimProp == 1 ? true : false;
        /*
        int SimProp = SystemProperties.getInt(PROPERTY_DUAL_MODE_PHONE, 0);
        Log.d("SettingsUtils", "Dual Sim Property = " + SimProp);
        return SimProp == 1 ? true : false;
        */
    }
}
