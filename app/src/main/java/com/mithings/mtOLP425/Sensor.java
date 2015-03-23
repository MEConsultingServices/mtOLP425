package com.mithings.mtOLP425;

public class Sensor 
{
    private static float acceValues[] = new float[3];
    private static final float ALPHA = 0.8f;
    private float[] gravity = new float[3];

	public Sensor() {}
	
	public int handleLinearAcceSensor(int input, int sensorNo) 
	{
    	acceValues[sensorNo] = (float) input;	
		acceValues = highPass(acceValues[0],acceValues[1], acceValues[2]);   
        return (int) acceValues[sensorNo];
	}

	private float[] highPass(float x, float y, float z) 
	{
        float[] filteredValues = new float[3];
        
        gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x;
        gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y;
        gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z;

        filteredValues[0] = x - gravity[0];
        filteredValues[1] = y - gravity[1];
        filteredValues[2] = z - gravity[2];
        
        return filteredValues;
    }
}
