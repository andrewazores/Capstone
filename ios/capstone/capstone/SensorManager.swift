//
//  SensorManager.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-28.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import Foundation
import CoreMotion

class SensorManager {
    
    let mySensorManager: CMMotionManager
    var accelX:Double = 0.0
    var accelY:Double = 0.0
    var accelZ:Double = 0.0
    var rotX:Double = 0.0
    var rotY:Double = 0.0
    var rotZ:Double = 0.0
    
    
    init () {
        mySensorManager = CMMotionManager()
        
        mySensorManager.deviceMotionUpdateInterval = 0.01
        mySensorManager.accelerometerUpdateInterval = 0.01
        mySensorManager.gyroUpdateInterval = 0.01
        
        mySensorManager.startAccelerometerUpdatesToQueue(NSOperationQueue.currentQueue(), withHandler: {(accelerometerData: CMAccelerometerData!, error:NSError!)in
            self.outputAccelerationData(accelerometerData.acceleration)
            if (error != nil)
            {
                println("\(error)")
            }
        })
        
        mySensorManager.startGyroUpdatesToQueue(NSOperationQueue.currentQueue(), withHandler: {(gyroData: CMGyroData!, error: NSError!)in
            self.outputRotationData(gyroData.rotationRate)
            if (error != nil)
            {
                println("\(error)")
            }
        })
        
    }
    
    func outputAccelerationData(acceleration:CMAcceleration) {
        accelX = acceleration.x
        accelY = acceleration.y
        accelZ = acceleration.z
        
    }
    
    func outputRotationData(rotation: CMRotationRate) {
        rotX = rotation.x
        rotY = rotation.y
        rotZ = rotation.z
    }
    
    
    func getAccelerationData() -> (Double, Double, Double) {
        println("X: \(accelX) Y: \(accelY) Z: \(accelZ)")
        return(accelX, accelY, accelZ)
    }
    
    func getRotationData() -> (Double, Double, Double) {
        return(rotX, rotY, rotZ)
    }
    
}
