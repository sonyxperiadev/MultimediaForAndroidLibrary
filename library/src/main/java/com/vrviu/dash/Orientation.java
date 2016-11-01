/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vrviu.dash;

import java.lang.Math;

/**
 *
 * @author biscuit
 */
public class Orientation {
    double m_Yaw;
    double m_Pitch;
    double m_Roll;

    public void setYaw( double yaw )       {   m_Yaw = yaw;        }
    public void setPitch( double pitch )   {   m_Pitch = pitch;    }
    public void setRoll( double roll )     {   m_Roll = roll;      }
    public void setYPR( double yaw, double pitch, double roll )
    {
        setYaw(yaw);
        setPitch(pitch);
        setRoll(roll);
    }
    public double getYaw()                 { return m_Yaw;         }
    public double getPitch()               { return m_Pitch;       }
    public double getRoll()                { return m_Roll;        }


    public String toString()
    {
        String result = "(y:"+m_Yaw+",p:"+m_Pitch+",r:"+m_Roll+")";
        return result;
    }

    public double[] rotateVector(double v[]) {
        double r = -m_Roll*Math.PI/180.0;
        double p = -m_Pitch*Math.PI/180.0;
        double y = m_Yaw*Math.PI/180.0;

        // Compute roll
        double vR[] = {
            Math.cos(r) * v[0] + Math.sin(r) * v[1],
            -Math.sin(r) * v[0] + Math.cos(r) * v[1],
            v[2]
        };
        // Compute pitch
        double vP[] = {vR[0],
            Math.cos(p) * vR[1] + Math.sin(p) * vR[2],
            -Math.sin(p) * vR[1] + Math.cos(p) * vR[2]
        };
        // Compute yaw
        double vY[] = {
            Math.cos(y) * vP[0] + Math.sin(y) * vP[2],
            vP[1],
            -Math.sin(y) * vP[0] + Math.cos(y) * vP[2]
        };
        return vY;
    }
    public double[] getViewVector() {
        double v[] = { 0, 0, -1};
        return rotateVector(v);
    }
    public double[] getInterpupillaryVector() {
        double v[] = { 1, 0, 0};
        return rotateVector(v);
    }
}
